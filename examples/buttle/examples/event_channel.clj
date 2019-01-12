(ns buttle.examples.event-channel
  (:require [buttle.proxy :as proxy]
            [clojure.core.async :as a]
            [buttle.event :as event]))

;; set BUTTLE_USER_FORM="-Dbuttle.user-form=(do (load-file ""C:/buttle-workspace/examples/buttle/examples/event_channel.clj"") (buttle.examples.event-channel/consume-events) (.println System/out :loaded-buttle.examples.event-channel))"

(defn log [e]
  (.println System/out (str "buttle.examples.event-channel : " e)))

(defn consume-events []
  (let [ch (a/chan)]
    (a/tap event/event-mult ch)
    (a/go
     (loop []
       (when-let [e (a/<! ch)]
         (log e)
         (recur))))))

