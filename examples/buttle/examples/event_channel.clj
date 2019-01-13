(ns buttle.examples.event-channel
  (:require [buttle.proxy :as proxy]
            [clojure.core.async :as a]
            [buttle.event :as event]))

;; When this file/namespace is loaded it will start a go-loop which
;; consumes events from `buttle.event/event-mult` and prints them to
;; stdout.
;;
;; You can use this in cases when you're using the _Buttle_ JDBC
;; driver but you don't have control over the main program flow. You
;; just need to define the system property `buttle.user-form` (see
;; `buttle.driver/eval-buttle-user-form`) like so: (you can also use
;; `require` if you have `examples` in your classpath).
;;
;; set BUTTLE_USER_FORM="-Dbuttle.user-form=(load-file ""C:/buttle-workspace/examples/buttle/examples/event_channel.clj"")"
;; java %BUTTLE_USER_FORM% [...]

(defn log [e]
  (.println System/out (str "buttle.examples.event-channel : " e)))

(let [ch (a/chan)]
  (a/tap event/event-mult ch)
  (a/go
   (loop []
     (when-let [e (a/<! ch)]
       (log e)
       (recur)))))

