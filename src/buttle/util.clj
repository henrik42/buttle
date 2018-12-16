(ns buttle.util)

(defn log [& xs]
  (let [s (apply pr-str xs)]
    (.println System/out s)
    s))

(defn method->keyword [method & _]
  ;; {:post [(log "method->keyword --> " %)]}
  (->> method .getName keyword))

