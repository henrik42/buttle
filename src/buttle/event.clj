(ns buttle.event
  "Send events to consumers via `clojure.core.async/chan`.

  Consumers can `tap` on `event-mult` to receive events that are
  produced through `send-event`. `buttle.proxy/handle-default` is such
  a producer.

  Note that `send-event` __synchronuosly__ puts events onto channel
  `event-ch` which is the input channel for `event-mult`. When there
  is __no__ __channel__ connected to `event-mult` (which is the case
  when this namespace is loaded the first time) calling `send-event`
  will __not__ __block__ (`event-mult` will just eat up those
  events). When there __is__ one or more channels connected to
  `event-mult` (by consumers having called `clojure.core.async/tap`)
  calling `send-event` __will__ __block__ until the event has been
  sent/consumed by each of the connected channels. So make sure you
  have a `go` block consuming any channel that you connect to
  `event-mult`."
  
  (:require [clojure.core.async :as a]))

(def event-ch
  "For internal use only. The channel through which events are
  sent. Is the input for `event-mult`."

  (a/chan))

(defn put-event
  "For internal use only. Sends event `e` to `event-ch`."

  [e]
  (a/>!! event-ch e))

(defn send-event 
  "__Synchronuosly__ (__blocking__) sends event `e` to `event-ch` (via `put-event`).

  API for producing/sending events, which are then consumed through
  `event-mult` and conncted consumer channels (if present)."

  [e]
  (put-event e))

(def event-mult
  "API for consumers which want to receive events. Use
  `clojure.core.async/tap` to register your consumer
  `clojure.core.async/chan`"

  (a/mult event-ch))

