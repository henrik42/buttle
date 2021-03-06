(ns buttle.proxy
  "A proxy factory.

   This namespace delivers all functions needed to (generically)
   create proxies for JDBC related interfaces and to implement the
   delegation logic for these proxies that is needed to route method
   calls through to the _real_ JDBC driver's instances.

   In order to hook your own code into the delegation use `def-handle`
   to register your functions for certain method calls."
  
  (:require [buttle.event :as event]
            [buttle.util :as util]))

(defn ->invoke-event
  "Returns an _invoke-event-map_."
  
  [the-method target-obj the-args]
  {:type :invoke
   :invoke (keyword
            (-> the-method .getDeclaringClass .getName str)
            (-> the-method .getName))
   :args (into [] the-args)
   :thread (let [t (Thread/currentThread)]
             {:name (.getName t)
              :id (.getId t)})
   :ts (System/currentTimeMillis)})

(defn ->throw-event
  "Returns a _throw-event-map_."

  [invoke-evt t]
  (let [now (System/currentTimeMillis)]
    {:type :throw
     :invoke invoke-evt
     :throw t
     :ts now
     :dur-msec (- now (:ts invoke-evt))}))

(defn ->return-event
  "Returns a _return-event-map_."

  [invoke-evt r]
  (let [now (System/currentTimeMillis)]
    {:type :return
     :invoke invoke-evt
     :return r
     :ts (System/currentTimeMillis)
     :dur-msec (- now (:ts invoke-evt))}))

(def function-default-hierarchy
  "Atom carrying a simple/shallow hierarchy of `:buttle/<method-name>`
  to `:buttle/default` entries. This hierarchy is used for `handle`
  multi-method and `fix-prefers!`."
  
  (atom (make-hierarchy)))

(defn invoke-fn
  "Invocation handler for `make-proxy`. It delegates any method
   invocation of `proxy-type` (which may be an interface or a vector
   of interfaces) to `handler-fn`. Any other method invocations (like
   `Object.toString()`) will be invoked on `target-object`.

   Note that any
   `java.lang.reflect.InvocationTargetException` (incl. those coming
   from `handler-fn`) will be un-rolled so that the cause `Exception`
   will come out of `invoke-fn` and thus the proxy made by
   `make-proxy`.

   This function is not meant for public usage. It functions as a
   hookable delegation-point for `make-proxy` so that you may
   re-bind/re-def the var when debugging and hacking."

  [proxy-type target-obj handler-fn the-proxy the-method the-args]
  (try
    (if ((if (vector? proxy-type) (into #{} proxy-type) #{proxy-type})
         (.getDeclaringClass the-method))
      (handler-fn the-method target-obj the-args)
      (.invoke the-method target-obj the-args))
    (catch java.lang.reflect.InvocationTargetException t
      (throw (.getCause t)))))

(defn make-proxy
  "A proxy factory.

   Creates and returns a __Java dynamic proxy__ with
   `proxy-type` (which may be an interface or a vector of
   interfaces).

   The proxy delegates any method invocation to `invoke-fn` which in
   turn delegates to `handler-fn`.

   The classloader for this proxy is taken from the first
   interface. If you want to delegate invocations on this proxy D
   through handler-fn and onto a Clojure proxy P you have to make
   sure, that D's and P's method/class declarations are
   compatible. See notes on classloading in
   src/buttle/connection_pool_data_source.clj for more details.

   Example usage:

        (make-proxy java.sql.Driver \"foo-driver\"
         (fn [the-method target-obj the-args]
           (condp = (.getName the-method)
             \"acceptsURL\" true
             \"connect\" (proxy [java.sql.Connection] []
                         (toString [] \"foo-connection\")))))"

  [proxy-type target-obj handler-fn]
  (let [pt (if (vector? proxy-type)
             (into-array proxy-type)
             (into-array [proxy-type]))]
    (java.lang.reflect.Proxy/newProxyInstance
     (.getClassLoader (first pt)) 
     pt
     (proxy [java.lang.reflect.InvocationHandler] []
       (invoke [the-proxy the-method the-args]
         (invoke-fn proxy-type target-obj handler-fn the-proxy the-method the-args))))))

(defn invocation-key
  "Dispatch function for `handle`. Returns a vector with the method's
  declaring class and `buttle/` namespaced keyword for the method's
  name.

  Example:

      (-> java.sql.Connection
          (.getMethod \"close\" nil)
          invocation-key)
      ;; --> [java.sql.Connection :buttle/close]"

  [the-method & _]
  [(-> the-method .getDeclaringClass)
   (->> the-method .getName (keyword "buttle"))])

(defmulti handle
  "A generic delegation function (arity `[the-method target-obj
  the-args]`) which delivers proxy'ed return values.

  The `:default` implementation just delegates to `handle-default`.

  The multi-method dispatch is done on `invocation-key`.

  This is a multi-method so that you have a means (see `def-handle`)
  to hook into the execution/delegation/proxying logic for some/any of
  the proxy'ed interface types i.e. `invocation-key` dispatch values."

  #'invocation-key :hierarchy function-default-hierarchy)

(defn handle-default
  "Calls `the-method` on `target-obj` with `the-args`, creates a proxy
  via `make-proxy` (which uses `handle` as its `handler-fn`) for
  non-`nil` interface-typed return values and returns the (possibly
  proxy'ed) result. Throws if the invoked method throws.

  Sends (`buttle.event/send-event`) an `->invoke-event` before calling
  `the-method`. Sends a `->throw-event` if `the-method` call
  throws. Else sends a `->return-event` before result proxy is
  created."

  [the-method target-obj the-args]

  (let [invoke-evt (->invoke-event the-method target-obj the-args)
        _ (event/send-event invoke-evt)
        r (try
            (.invoke the-method target-obj the-args)
            (catch Throwable t
              (event/send-event (->throw-event invoke-evt t))
              (throw t)))
        _ (event/send-event (->return-event invoke-evt r))
        rt (and r (.getReturnType the-method))]
    (if (and rt (.isInterface rt))
      ;; see comment for buttle.driver/make-driver -- need
      ;; Class/forName because when using class literal
      ;; buttle.jdbc.Driver the compiler complains about a cyclic
      ;; dependency driver/proxy/driver.
      (make-proxy rt r
                  (fn [the-method target-obj the-args]
                    (util/with-tccl (.getClassLoader
                                     (Class/forName "buttle.jdbc.Driver"))
                      (handle the-method target-obj the-args))))
      r)))

(defmethod handle :default [the-method target-obj the-args]
  (handle-default the-method target-obj the-args))

(defmacro def-handle
  "Registers a `handle` method implementation for dispatch (as of
  `invocation-key`) value `[clss mthd]`. Can be undone via
  `remove-handle`. Re-registering just overwrites.

  Uses `fix-prefers!` on the given key. So you may use keys like
  __(a)__ `[Object :buttle/getCatalog]` and __(b)__
  `[java.sql.Connection :buttle/default]` to register an
  implementation for __(a)__ specific method __names__ (ignoring the
  defining class/interface) and __(b)__ specific interfaces ignoring
  the method name (with a __preference__ for __(b)__ in conflicting
  cases).

  The most specific registration would be `[java.sql.Connection
  :buttle/getCatalog]`. So this would be prefered over __(a)__ and
  __(b)__ when present.

  __Note:__ This macro (i.e. the resulting code) may not be not
  thread-safe because it uses `fix-prefers!` which may not be
  thread-safe. You should use `def-handle` only in top-level-forms for
  defining `handle` method-implemenations but not in functions you
  call as part of the program flow."

  [[clss mthd] [the-method target-obj the-args] body]
  (list 'do
        (list 'buttle.proxy/fix-prefers! [clss mthd])
        (list 'defmethod 'buttle.proxy/handle [clss mthd] '[the-method target-obj the-args]
              body)))

(defn remove-handle
  "Removes the `handle` for `[clss mthd]`. No-op if there is no
  registered `handle` for this key."

  [[clss mthd]]
  (remove-method handle [clss mthd]))
  
(defn methods-of
  "Returns seq of `:buttle/` namespaced method names of class `clss`."

  [clss]
  (->> clss
       .getMethods
       (map #(keyword "buttle" (.getName %)))))

(defn fix-prefers!
  "If `(= mthd :buttle/default)` makes all/any method `m` of class
  `clss` (via `derive`) a child of `:buttle/default` and _prefers_
  `[clss :buttle/default]` over `[java.lang.Object m]`. This lets you
  dispatch via `invocation-key` with an _inheritance_ mechanism which
  uses/combines `isa?` on types `(isa? Connection Object)` and on
  method keys `(isa? :buttle/getCatalog :buttle/default)`.

  Now you can __(a)__ `def-handle [Object :buttle/getCatalog]` and
  __(b)__ `def-handle [java.sql.Connection :buttle/default]` with a
  __preference__ for __(a)__ when calling `Connection/getCatalog`.

  This function is thread-safe only if `derive` and `prefer-method`
  are so. You will usually not use this function directly but only
  through `def-handle`."

  [[clss mthd]]
  (when (= mthd :buttle/default)
    (when (= Object clss)
      (throw (RuntimeException. "You cannot use def-handle with Object/:buttle/default")))
    (doseq [m (methods-of clss)]
      ;; MUTATION/SIDEEFFECT!!!!
      (swap! function-default-hierarchy derive m :buttle/default))
    (doseq [m (descendants @function-default-hierarchy :buttle/default)]
      ;; MUTATION/SIDEEFFECT!!!!
      (prefer-method handle
                     [clss :buttle/default]
                     [java.lang.Object m]))))

