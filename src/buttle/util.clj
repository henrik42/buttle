(ns buttle.util)

(defn log [& xs]
  (.println System/out (apply pr-str xs)))

