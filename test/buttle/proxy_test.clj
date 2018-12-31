(ns buttle.proxy-test
  (:require [clojure.test :refer :all]
            [buttle.proxy :as proxy]))

;; This `java.sql.Connection` proxy stands for the _real_ object that
;; we want to proxy. Clojure proxys do not incorporate a handler
;; function but uses method-body-forms. So here we define some of the
;; method-bodies that we'll use in the tests below.
(def a-connection
  (proxy [java.sql.Connection] []
    (hashCode []
      42)
    (rollback []
      (throw (RuntimeException. "connection:ROLLBACK!")))
    (close []
      (throw (RuntimeException. "connection:CLOSE!")))
    (nativeSQL [sql]
      (str "connection:" sql))))

;; This handler-function for the Buttle proxy.
(defn connection-handler [the-method target-obj [a b c :as the-args]]
  (let [meth (.getName the-method)]
    (condp = meth
      "rollback" (.invoke the-method target-obj the-args)
      "close" (throw (RuntimeException. "handler:CLOSE!"))
      "nativeSQL" (if (= \: (first a))
                    (.invoke the-method target-obj the-args)
                    (str "handler:" a)))))

;; The Buttle proxy "around" the real object. We use the var instead
;; of connection-handler just for development of the test code so that
;; we can re-define the handler without having to re-create the Buttle
;; proxy.
(def connection-proxy
  (proxy/make-proxy java.sql.Connection a-connection #'connection-handler))

(deftest connection-tests
  (is (= 42 (.hashCode a-connection)) ".hashCode on a-connection")
  (is (= 42 (.hashCode connection-proxy)) ".hashCode on connection proxy")
  (is (= "connection:foo" (.nativeSQL a-connection "foo")) ".nativeSQL on a-connection")
  (is (= "handler:bar" (.nativeSQL connection-proxy "bar")) ".nativeSQL on a-connection proxy")
  (is (= "connection::bar" (.nativeSQL connection-proxy ":bar")) ".nativeSQL with super call on a-connection proxy")
  (is (thrown-with-msg? RuntimeException #"connection:CLOSE!" (.close a-connection)) "throwing from a-connection")
  (is (thrown-with-msg? RuntimeException #"handler:CLOSE!" (.close connection-proxy)) "throwing from connection proxy")
  (is (thrown-with-msg? RuntimeException #"connection:ROLLBACK!" (.rollback connection-proxy)) "throwing from a-connection"))

(deftest invocation-key-test
  (is (= [java.sql.Connection :buttle/close]
         (proxy/invocation-key 
          (.getMethod java.sql.Connection "close" nil)))))

  