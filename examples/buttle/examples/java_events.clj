(ns buttle.examples.java-events
  (:require [clojure.core.async :as a]
            [buttle.event :as event]))

;; When this file/namespace is loaded it will start a go-loop which
;; consumes events from `buttle.event/event-mult` and calls
;; `ButtleTest/processEvent` (see `java/ButtleTest.java`).

(let [ch (a/chan)]
  (a/tap event/event-mult ch)
  (a/go
   (loop []
     (when-let [e (a/<! ch)]
       (ButtleTest/processEvent e)
       (recur)))))

