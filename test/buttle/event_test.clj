(ns buttle.event-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a]
            [buttle.event :as event]))

(deftest produce-and-consume-event-test
  (let [p (promise)
        c (let [ch (a/chan)]
            (a/tap event/event-mult ch)
            (a/go
             (when-let [e (a/<! ch)]
               (deliver p e))
             (a/close! ch)
             (a/untap event/event-mult ch))
            ch)]
    (event/send-event {:foo :bar})
    (is (= {:foo :bar} @p))))


  