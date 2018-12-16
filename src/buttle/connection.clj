(ns buttle.connection
  (:require [buttle.proxy :as proxy]
            [buttle.util :as util]))

(defmulti handle #'util/method->keyword)

(defmethod handle :default [the-method target-obj the-args]
  (throw (RuntimeException. (str "No handle defined for "
                                 {:the-method the-method
                                  :target-obj target-obj
                                  :the-args (into [] the-args)}))))

(defmethod handle :createStatement [the-method target-obj the-args]
  "foo")

(defn make-proxy [conn]
  (proxy/make-proxy java.sql.Connection conn handle))
  