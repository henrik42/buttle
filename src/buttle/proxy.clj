(ns buttle.proxy
  "A proxy factory.

   __Note:__ You cannot use Clojure `proxy` for creating/registering
   `java.sql.Driver` instances due to classloader/caller checks in
   `java.sql.DriverManger`. See `test/buttle/driver_test.clj` for more
   details.

   This namespace delivers all functions needed to create proxys for
   JDBC relevant interfaces and to implement the delegation logic for
   these proxys that is needed to route method calls through to the
   _real_ JDBC driver's instances."
  
  (:require [buttle.util :as util]))

(defn invoke-fn
  "Invocation handler for `make-proxy`. It delegates any method
   invocation of `proxy-type` to `handler-fn`. Any other method
   invocations (like `Object.toString()`) will be invoked on
   `target-object`.

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
    (if (= proxy-type (.getDeclaringClass the-method))
      (handler-fn the-method target-obj the-args)
      (.invoke the-method target-obj the-args))
    (catch java.lang.reflect.InvocationTargetException t
      (throw (.getCause t)))))

(defn make-proxy
  "A proxy factory.

   Creates and returns a __Java dynamic proxy__ with `proxy-type`. The
   classloader for this proxy is taken from `target-obj`. The proxy
   delegates any method invocation to `invoke-fn` which in turn
   delegates to `handler-fn`.

   Example usage:

        (make-proxy java.sql.Driver \"foo-driver\"
         (fn [the-method target-obj the-args]
           (condp = (.getName the-method)
             \"acceptsURL\" true
             \"connect\" (proxy [java.sql.Connection] []
                         (toString [] \"foo-connection\")))))
  "

  [proxy-type target-obj handler-fn]
  (java.lang.reflect.Proxy/newProxyInstance
   (.. target-obj getClass getClassLoader)
   (into-array [proxy-type])
   (proxy [java.lang.reflect.InvocationHandler] []
     (invoke [the-proxy the-method the-args]
       (invoke-fn proxy-type target-obj handler-fn the-proxy the-method the-args)))))

(defn invocation-key
  "Dispatch function for `handle`. Returns a vector with the method's
  declaring class and `buttle/` namespaced keyword for the method's
  name."

  [the-method & _]
  [(-> the-method .getDeclaringClass)
   (->> the-method .getName (keyword "buttle"))])

(defmulti handle
  "A generic delegation function (arity `[the-method target-obj
  the-args]`) which delivers proxy'ed return values.

  The `:default` implementation calls `the-method` on `target-obj`
  with `the-args`, creates a proxy via `make-proxy` for non-nil
  interface-typed return values and returns the (possibly proxy'ed)
  result. Throws if the invoked method throws.

  The multi-method dispatch is done on `invocation-key`.

  This is a multi-method so that you have a means to hook into the
  execution/delegation/proxying logic for some/any of the proxy'ed
  interface types i.e. `invocation-key` dispatch values."

  #'invocation-key)

(defmethod handle :default [the-method target-obj the-args]
  (let [r (.invoke the-method target-obj the-args)
        rt (and r (#{java.sql.Statement
                     java.sql.PreparedStatement
                     java.sql.CallableStatement
                     java.sql.Savepoint
                     java.sql.Clob
                     java.sql.Blob
                     java.sql.NClob
                     java.sql.SQLXML}
                   (.getReturnType the-method)))]
    (if rt
      (make-proxy rt r handle)
      r)))
