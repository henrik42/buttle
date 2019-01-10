(ns buttle.event
  (:require [clojure.core.async :as a]))

(def event-ch (a/chan))

(defn put-event [e]
  (a/>!! event-ch e))

(defn send-event [e]
  (put-event e))

;; API/Hook for consumers
(def event-mult (a/mult event-ch))

