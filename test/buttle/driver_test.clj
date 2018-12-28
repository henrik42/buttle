(ns buttle.driver-test
  (:require [clojure.test :refer :all]
            [buttle.driver-manager :as mgr]
            [buttle.util :as util]
            [buttle.proxy :as proxy]
            [buttle.driver :as drv]))

(deftest test-parse-jdbc-url
  (is (= nil (drv/parse-jdbc-url "foobar")))
  (is (= nil (drv/parse-jdbc-url "jdbc:buttle:")))
  (is (= 42 (drv/parse-jdbc-url "jdbc:buttle:42")))
  (is (thrown-with-msg? Throwable #"Could not parse url" (drv/parse-jdbc-url "jdbc:buttle:("))))
    
(deftest test-accepts-url-fn
  (is (= nil (drv/accepts-url-fn "foobar")))
  (is (= nil (drv/accepts-url-fn "jdbc:buttle:")))
  (is (= {:target-url nil, :user nil, :password nil}
         (drv/accepts-url-fn "jdbc:buttle:42")))
  (is (thrown-with-msg? Throwable #"Could not parse url" (drv/accepts-url-fn "jdbc:buttle:("))))

(def postgres-url "jdbc:postgresql://127.0.0.1:6632/postgres")
(def buttle-url (format "jdbc:buttle:{:user %s :password %s :target-url %s}"
                        (pr-str (System/getenv "buttle_user"))
                        (pr-str (System/getenv "buttle_password"))
                        (pr-str postgres-url)))

(deftest test-connect-fn
  (is (= nil (drv/connect-fn "foobar")))
  (is (= nil (drv/connect-fn "jdbc:buttle:")))
  (is (= true (-> (drv/connect-fn buttle-url) .getClass java.lang.reflect.Proxy/isProxyClass)))
  (is (thrown-with-msg? Throwable #"The url cannot be null" (drv/connect-fn "jdbc:buttle:42"))))

#_
(mgr/register-driver
 (proxy [java.sql.Driver] []
   (acceptsURL [url]
     (boolean (re-matches #"foo:bar:.*" url)))))

(deftest test-make-driver
  (let [buttle-driver (drv/make-driver)
        ;; Note: you CANNOT use a Clojure's proxy here! The
        ;; DriverManager will not give back the registered driver to
        ;; you when calling getDriver - probably due to the
        ;; classloader check in DriverManager. So we use
        ;; proxy/make-proxy instead which uses the Java reflection API
        ;; for creating the proxy. In this case the interaction with
        ;; DriverManager works as aspected.
        foo-driver (proxy/make-proxy
                    java.sql.Driver
                    "foo-driver"
                    (fn [the-method target-obj the-args]
                      (condp = (.getName the-method)
                        "acceptsURL" (boolean (re-matches #"foo:bar:.*" (first the-args)))
                        "connect" (proxy [java.sql.Connection] []
                                    (toString [] "foo-connection")))))]
    (mgr/register-driver foo-driver)
    (try
      (is (= "foo-connection"
             (str (.connect buttle-driver "jdbc:buttle:{:target-url \"foo:bar:fred\"}" nil))))
      (finally 
        (java.sql.DriverManager/deregisterDriver foo-driver)))))

