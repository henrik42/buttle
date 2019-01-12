(ns buttle.examples.handle
  (:require [buttle.proxy :as proxy]))

(defn handle-with-log [the-method target-obj the-args]
  (println (format "ENTER %s" (pr-str [the-method target-obj (into [] the-args)])))
  (let [r (proxy/handle-default the-method target-obj the-args)]
    (println (format "EXIT %s --> %s" (pr-str [the-method target-obj (into [] the-args)]) (pr-str r)))
    r))

(defmethod proxy/handle :default [the-method target-obj the-args]
  (handle-with-log the-method target-obj the-args))

