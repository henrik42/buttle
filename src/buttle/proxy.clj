(ns buttle.proxy
  "Delivers a proxy factory."
  
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
   `make-proxy`."

  [proxy-type target-obj handler-fn the-proxy the-method the-args]
  (try
    (if (= proxy-type (.getDeclaringClass the-method))
      (handler-fn the-method target-obj the-args)
      (.invoke the-method target-obj the-args))
    (catch java.lang.reflect.InvocationTargetException t
      (throw (.getCause t)))))

(defn make-proxy
  "A proxy factory.

   Creates and returns a Java dynamic proxy with `proxy-type`. The
   classloader for this proxy is taken from `target-obj`. The proxy
   delegates any method invocation to `invoke-fn` which in turn
   delegates to `handler-fn`."

  [proxy-type target-obj handler-fn]
  (java.lang.reflect.Proxy/newProxyInstance
   (.. target-obj getClass getClassLoader)
   (into-array [proxy-type])
   (proxy [java.lang.reflect.InvocationHandler] []
     (invoke [the-proxy the-method the-args]
       (invoke-fn proxy-type target-obj handler-fn the-proxy the-method the-args)))))

