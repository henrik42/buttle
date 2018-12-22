(ns buttle.proxy
  (:require [buttle.util :as util]))

(defn invoke-fn [proxy-type target-obj handler-fn the-proxy the-method the-args]
  (try
    (if (= proxy-type (.getDeclaringClass the-method))
      (handler-fn the-method target-obj the-args)
      (.invoke the-method target-obj the-args))
    (catch java.lang.reflect.InvocationTargetException t
      (throw (.getCause t)))))

(defn make-proxy [proxy-type target-obj handler-fn]
  (java.lang.reflect.Proxy/newProxyInstance
   (.. target-obj getClass getClassLoader)
   (into-array [proxy-type])
   (proxy [java.lang.reflect.InvocationHandler] []
     (invoke [the-proxy the-method the-args]
       (invoke-fn proxy-type target-obj handler-fn the-proxy the-method the-args)))))

