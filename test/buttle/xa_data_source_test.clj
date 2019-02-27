(ns buttle.xa-data-source-test
  (:require [clojure.test :refer :all]
            [buttle.driver-test :as driver-test]
            [buttle.util :as util]
            [buttle.data-source-test] ;; see buttle.data-source-test/def-once-set-initial-context-factory-builder
            [buttle.connection-pool-data-source :as cp]))

(deftest create-instance
  (let [buttle-xa-ds
        (doto (buttle.jdbc.XADataSource.)
          (.setDelegateSpec
           (format "{:delegate-class org.postgresql.xa.PGXADataSource
                     :url %s}"
                   (pr-str driver-test/postgres-url))))]
    (with-open [conn (.getXAConnection
                      buttle-xa-ds
                      driver-test/buttle-user
                      driver-test/buttle-password)])))
    
(deftest lookup-jndi
  (with-open [ctx (javax.naming.InitialContext.)]
    (.rebind ctx "foo-ds"
             (util/->java-bean org.postgresql.xa.PGXADataSource
                               {:url driver-test/postgres-url}))
    (let [buttle-xa-ds
          (doto (buttle.jdbc.XADataSource.)
            (.setDelegateSpec "\"foo-ds\""))]
      (with-open [conn (.getXAConnection
                        buttle-xa-ds
                        driver-test/buttle-user
                        driver-test/buttle-password)]))))
  