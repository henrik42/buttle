(ns buttle.driver-manager
  (:import [java.sql DriverManager]))

(defn register-driver [driver]
  (DriverManager/registerDriver driver))

(defn get-driver [url]
  (DriverManager/getDriver url))

(defn get-drivers []
  (enumeration-seq (DriverManager/getDrivers)))

(defn get-connection [url user password]
  (DriverManager/getConnection url user password))

(defn deregister-drivers []
  (seq 
   (doall 
    (for [d (get-drivers)]
      (do 
        (DriverManager/deregisterDriver d)
        d)))))

