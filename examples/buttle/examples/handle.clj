(ns buttle.examples.handle
  (:require [buttle.proxy :as proxy]))

;; When this file/namespace is loaded it will (re-) register the
;; `:default` method implementation for `buttle.proxy/handle`. It just
;; prints log messages to stdout when entering and leaving methods
;; (and when the method call throws). This acts like an around advice
;; in AOP.
;;
;; You can use this in cases when you're using the _Buttle_ JDBC
;; driver but you don't have control over the main program flow. You
;; just need to define the system property `buttle.user-file` (see
;; `buttle.driver/eval-buttle-user-file!`) like so:
;;
;; set BUTTLE="-Dbuttle.user-file=C:/buttle-workspace/examples/buttle/examples/handle.clj"
;; java %BUTTLE% [...]

(defn invoke-with-logging [the-method target-obj the-args]
  (println (format "buttle.examples.handle: INVOKE %s"
                   (pr-str [the-method target-obj (into [] the-args)])))
  (let [r (try
            (proxy/handle-default the-method target-obj the-args)
            (catch Throwable t
              (do
                (println (format "buttle.examples.handle: THROW %s : %s"
                                 (pr-str [the-method target-obj (into [] the-args)]) (pr-str t)))
                (throw t))))]
    (println (format "buttle.examples.handle: RETURN %s --> %s"
                     (pr-str [the-method target-obj (into [] the-args)]) (pr-str r)))
    r))

;; #_ ;; handle :default will print lots(!) of messages. 
(defmethod proxy/handle :default [the-method target-obj the-args]
  (invoke-with-logging the-method target-obj the-args))

#_ ;; just using [java.sql.Driver :buttle/default] prints only a few lines
(proxy/def-handle [java.sql.Driver :buttle/default] [the-method target-obj the-args]
  (invoke-with-logging the-method target-obj the-args))

#_ ;; using [java.sql.Connection :buttle/default] prints a few more lines
(proxy/def-handle [java.sql.Connection :buttle/default] [the-method target-obj the-args]
  (invoke-with-logging the-method target-obj the-args))

