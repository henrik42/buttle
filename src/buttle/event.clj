(ns buttle.event
  "Send events to consumers via core.async/channel.

  Consumers can `tap` on `event-mult` to receive events that are
  produced through `send-event` (`buttle.proxy/handle-default` is such
  a producer)."
  
  (:require [clojure.core.async :as a]))

(def event-ch
  "The channel through which events are sent. Is the input for
  `event-mult`."

  (a/chan))

(defn put-event
  "For internal use only. Sends event `e` to `event-ch`."

  [e]
  (a/>!! event-ch e))

(defn send-event 
  "Sends event `e` to `event-ch`."

  [e]
  (put-event e))

(def event-mult
  "API/`clojure.core.async/mult` for consumers which want to receive
  events."

  (a/mult event-ch))

