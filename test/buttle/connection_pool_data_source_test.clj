(ns buttle.connection-pool-data-source-test
  (:require [clojure.test :refer :all]
            [buttle.driver-test :as driver-test]
            [buttle.util :as util]
            [buttle.data-source-test] ;; see buttle.data-source-test/def-once-set-initial-context-factory-builder
            [buttle.connection-pool-data-source :as cp]))

(deftest create-instance
  (let [buttle-cp-ds
        (doto (buttle.jdbc.ConnectionPoolDataSource.)
          (.setDelegateSpec
           (format "{:delegate-class org.postgresql.ds.PGConnectionPoolDataSource
                     :url %s}"
                   (pr-str driver-test/postgres-url))))]
    (with-open [conn (.getPooledConnection
                      buttle-cp-ds
                      driver-test/buttle-user
                      driver-test/buttle-password)])))
    
(deftest lookup-jndi
  (with-open [ctx (javax.naming.InitialContext.)]
    (.rebind ctx "foo-ds"
             (util/->java-bean org.postgresql.ds.PGConnectionPoolDataSource
                               {:url driver-test/postgres-url}))
    (let [buttle-cp-ds
          (doto (buttle.jdbc.ConnectionPoolDataSource.)
            (.setDelegateSpec "\"foo-ds\""))]
      (with-open [conn (.getPooledConnection
                        buttle-cp-ds
                        driver-test/buttle-user
                        driver-test/buttle-password)]))))
  