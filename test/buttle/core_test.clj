(ns buttle.core-test
  (:require [clojure.test :refer :all]
            [buttle.core :refer :all]
            [buttle.driver-manager :as drv]))

(drv/register-driver (buttle.jdbc.Driver.))

(def postgres-url "jdbc:postgresql://127.0.0.1:6632/postgres?")
(def buttle-url (format "jdbc:buttle:{:user %s :password %s :target-url %s}"
                        (pr-str (System/getenv "buttle_user"))
                        (pr-str (System/getenv "buttle_password"))
                        (pr-str postgres-url)))

(defn get-postgres-connection []
  (drv/get-connection postgres-url
                      (System/getenv "buttle_user")
                      (System/getenv "buttle_password")))

(defn get-buttle-connection []
  (drv/get-connection buttle-url
                      (System/getenv "buttle_user")
                      (System/getenv "buttle_password")))

(deftest postgres-tests
  (testing "Just connecting to Postgres"
    (is (with-open [conn (get-postgres-connection)]
          "ok")))
  (testing "Access pg_catalog.pg_tables"
    (is (with-open [conn (get-postgres-connection)]
          (-> conn
              .createStatement
              (.executeQuery "select * from pg_catalog.pg_tables where schemaname = 'pg_catalog'")
              (resultset-seq))))))

(deftest buttle-tests
  (testing "Just connecting to Postgres through buttle"
    (is (with-open [conn (get-buttle-connection)]
          "ok"))))

