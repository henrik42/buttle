(ns buttle.driver-test
  (:require [clojure.test :refer :all]
            [buttle.driver-manager :as mgr]
            [buttle.util :as util]
            [buttle.proxy :as proxy]
            [buttle.driver :as drv]))

(java.sql.DriverManager/setLogWriter
 (proxy [java.io.PrintWriter] [*out*]
   (println [s]
     (proxy-super println (str "buttle.driver-test -- DriverManager LOG: " s)))))

#_ ;; Not working! The registered driver is a proxy now!
(deftest auto-load-buttle-driver
  ;; buttle.jdbc.Driver is auto registered via SPI mechanism
  (is (= buttle.jdbc.Driver (.getClass (mgr/get-driver "jdbc:buttle:42")))))

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
  (is (thrown-with-msg? Throwable #"The url cannot be null" (drv/connect-fn "jdbc:buttle:42"))))

(deftest test-make-driver
  (let [buttle-driver (drv/make-driver)
        ;; Note: you CANNOT use a Clojure proxy here! The
        ;; DriverManager will not give back the registered driver
        ;; (bar-driver below) to you when calling getDriver - due to
        ;; the caller/classloader check in DriverManager. So we use
        ;; proxy/make-proxy instead (foo-driver below) which uses the
        ;; Java reflection API for creating the proxy. In this case
        ;; the interaction with DriverManager works as aspected. All
        ;; this is verified by the test cases here.
        bar-driver (proxy [java.sql.Driver] []
                     (toString []
                       "bar-driver"))
        foo-driver (proxy/make-proxy
                    java.sql.Driver
                    "foo-driver"
                    (fn [the-method target-obj the-args]
                      (condp = (.getName the-method)
                        "acceptsURL" (boolean (re-matches #"foo:bar:.*" (first the-args)))
                        "connect" (proxy [java.sql.Connection] []
                                    (toString [] "foo-connection")))))]

    ;; In java.sql.DriverManager.getDriver(String) there is
    ;;
    ;;     println("    skipping: " + aDriver.driver.getClass().getName());
    ;;
    ;; which will print something like
    ;;
    ;;  DriverManager LOG: DriverManager.getDriver("foobar")
    ;;  DriverManager LOG:     skipping: buttle.driver_test.proxy$java.lang.Object$Driver$1a6dd307
    ;;
    ;; In java.sql.DriverManager.getDrivers() and java.sql.DriverManager.getConnection(String, Properties, Class<?>)
    ;; there is a bug:
    ;;
    ;;     println("    skipping: " + aDriver.getClass().getName());
    ;;
    ;; will print just "skipping: java.sql.DriverInfo" which is
    ;; probably not intended.

    (mgr/register-driver bar-driver)
    (is (thrown-with-msg? java.sql.SQLException #"No suitable driver" (mgr/get-driver "foobar")))
    (is (= nil ((into #{} (mgr/get-drivers)) bar-driver)))
    
    (mgr/register-driver foo-driver)
    (try
      (is (= foo-driver ((into #{} (mgr/get-drivers)) foo-driver)))
      ;; go through local driver proxy
      (is (= "foo-connection"
             (str (.connect buttle-driver "jdbc:buttle:{:target-url \"foo:bar:fred\"}" nil))))
      ;; go through registered Driver instance
      (is (= "foo-connection"
             (str (mgr/get-connection "jdbc:buttle:{:target-url \"foo:bar:fred\"}" "no-user" "no-password"))))
      (finally 
        (java.sql.DriverManager/deregisterDriver foo-driver)))))

#_ ;; this will be removed.
(let [driver (buttle.jdbc.Driver.)]
  (proxy/def-handle [java.sql.Driver :buttle/acceptsURL] [the-method target-obj the-args]
    (do 
      (.println System/out "bar")
      (proxy/handle-default the-method target-obj the-args)))
  (.acceptsURL driver "jdbc:buttle:42"))