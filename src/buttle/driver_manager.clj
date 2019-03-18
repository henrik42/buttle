(ns buttle.driver-manager
  "A thin/simple Clojure API around `java.sql.DriverManager`."
  
  (:import [java.sql DriverManager]))

(defn register-driver
  "Registers the `driver` (see
  `java.sql.DriverManager.registerDriver(Driver)`)."

  [driver]
  (DriverManager/registerDriver driver))

(defn get-driver
  "Returns the registered `java.sql.Driver` which accepts the
  `url`. Throws if a driver cannot be found (see
  `java.sql.DriverManager.getDriver(String)`)."

  [url]
  (DriverManager/getDriver url))

(defn get-drivers
  "Returns a seq of all registered drivers (see
  `java.sql.DriverManager.getDrivers()`)"
  
  []
  (enumeration-seq (DriverManager/getDrivers)))

(defn get-connection 
  "Finds the driver for `url` and uses it to open a
  `java.sql.Connection`. Returns the connection or throws if anything
  goes wrong (see `java.sql.DriverManager.getConnection(String,
  String, String)`)."
  
  ([url user password]
     (DriverManager/getConnection url user password))
  ([url info]
     (DriverManager/getConnection url info)))

(defn deregister-drivers
  "Iterates over all registered drivers (as of `(get-drivers)`) and
  deregisters each. Returns seq of drivers."

  []
  (seq 
   (doall 
    (for [d (get-drivers)]
      (do 
        (DriverManager/deregisterDriver d)
        d)))))

