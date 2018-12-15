(ns buttle.proxy
  (:require [buttle.util :as util]))

(def debug true #_ false)

(defn invoke-fn [proxy-type target-obj handler-fn the-proxy the-method the-args]
  (if (= proxy-type (.getDeclaringClass the-method))
    (do
      (when debug
        (util/log (format "invoke-fn: delegating to handler-fn=>>%s<< the-method=>>%s<< target-obj=>>%s<< the-args=>>%s<<"
                          handler-fn the-method target-obj (into [] the-args))))
      (handler-fn the-method target-obj the-args))
    (do
      (when debug
        (util/log (format "invoke-fn: invoking the-method=>>%s<< target-obj=>>%s<< the-args=>>%s<<"
                          the-method target-obj (into [] the-args))))
      (try 
        (.invoke the-method target-obj the-args)
        (catch java.lang.reflect.InvocationTargetException t
          (throw (.getCause t)))))))

(defn make-proxy [proxy-type target-obj handler-fn]
  (java.lang.reflect.Proxy/newProxyInstance
   (.. target-obj getClass getClassLoader)
   (into-array [proxy-type])
   (proxy [java.lang.reflect.InvocationHandler] []
     (invoke [the-proxy the-method the-args]
       (when debug
         (util/log (format "InvocationHandler.invoke: the-method=>>%s<< the-args=>>%s<<"
                           the-method (into [] the-args))))
       (invoke-fn proxy-type target-obj handler-fn the-proxy the-method the-args)))))






(def a
  (proxy [java.util.function.Function] []
    (apply [t]
      (util/log (format "*** Function: t=>>%s<<" (pr-str t)))
      (when (= t "3")
        (throw (NumberFormatException. "oops")))
      (str "->" t))))


(def p (make-proxy
        #_ java.util.function.Function
        clojure.lang.IFn
        #_ a
        (fn [t]
          (util/log (format "*** Function: t=>>%s<<" (pr-str t)))
          (when (= t "3")
            (throw (NumberFormatException. "oops")))
          (str "->" t))
        (fn [the-method target-obj the-args]
          (util/log
           (format "*** handler-fn: the-method=>>%s<< target-obj=>>%s<< the-args=>>%s<<"
                   the-method target-obj (into [] the-args)))
          (try 
            (.invoke the-method target-obj the-args)
            (catch java.lang.reflect.InvocationTargetException t
              (util/log (format "*** handler-fn: EX %s" t))
              (throw (.getCause t)))))))

#_
(try
  #_ (.apply p "3")
  (p "3")
  (catch  Throwable t
    (util/log (format "*** Exception= %s" t))))