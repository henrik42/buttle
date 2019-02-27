(ns buttle.data-source-test
  (:require [clojure.test :refer :all]
            [buttle.driver-test :as driver-test]
            [buttle.util :as util]
            [buttle.data-source :as ds]))

;; https://docs.oracle.com/javase/8/docs/api/javax/naming/InitialContext.html
;; https://www.javacodegeeks.com/2012/04/jndi-and-jpa-without-j2ee-container.html

(defn create-initial-context-factory []
  (proxy [org.osjava.sj.MemoryContextFactory] []
    (getInitialContext [env]
      (proxy-super
       getInitialContext
       (doto (.clone env)
         (.put "org.osjava.sj.jndi.ignoreClose" "true")
         (.put "org.osjava.sj.jndi.shared" "true"))))))

(defonce def-once-set-initial-context-factory-builder
  (javax.naming.spi.NamingManager/setInitialContextFactoryBuilder
   (proxy [javax.naming.spi.InitialContextFactoryBuilder] []
     (createInitialContextFactory [_]
       (create-initial-context-factory)))))

(deftest sane-check
  (with-open [ctx (javax.naming.InitialContext.)]
    (.rebind ctx "foo" "bar"))
  (with-open [ctx (javax.naming.InitialContext.)]
    (is (= "bar" (.lookup ctx "foo")))))

(deftest create-instance
  (let [buttle-ds
        (doto (buttle.jdbc.DataSource.)
          (.setDatasourceSpec
           (format "{:datasource-class org.postgresql.ds.PGSimpleDataSource
                     :url %s}"
                   (pr-str driver-test/postgres-url))))]
    (with-open [conn (.getConnection
                      buttle-ds
                      driver-test/buttle-user
                      driver-test/buttle-password)])))

(deftest lookup-jndi
  (with-open [ctx (javax.naming.InitialContext.)]
    (.rebind ctx "foo-ds"
             (util/->java-bean org.postgresql.ds.PGSimpleDataSource
                               {:url driver-test/postgres-url
                                :user driver-test/buttle-user
                                :password driver-test/buttle-password}))
    (let [buttle-ds
          (doto (buttle.jdbc.DataSource.)
            (.setDatasourceSpec "\"foo-ds\""))]
      (with-open [conn (.getConnection
                        buttle-ds
                        driver-test/buttle-user
                        driver-test/buttle-password)]))))

(deftest lookup-connection
  (is (= "foo-connection"
         (let [_ (with-open [ctx (javax.naming.InitialContext.)]
                   (.rebind ctx "foo-ds"
                            (proxy [javax.sql.DataSource] []
                              (getConnection [& xs]
                                (proxy [java.sql.Connection] []
                                  (toString [] "foo-connection"))))))
               buttle-ds (doto (buttle.jdbc.DataSource.)
                           (.setDatasourceSpec "\"foo-ds\""))
               conn (.getConnection buttle-ds)]
           (str conn)))))
