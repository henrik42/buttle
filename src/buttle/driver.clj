(ns buttle.driver
  "The _Buttle_ `java.sql.Driver`.

  This namespace delivers `buttle.jdbc.Driver` via `:gen-class`. This
  named class can be used by tools (like SQuirreL) and the SPI
  `services/java.sql.Driver`.

  The `-init` constructor function will register a _Buttle_ `Driver`
  proxy (see `make-driver`) with the `java.sql.DriverManager`. So
  whenever an instance of `buttle.jdbc.Driver` is created, a new
  proxy ( __not__ the `buttle.jdbc.Driver`!) is registered. This will
  probably change to just ever registering one proxy.

  When this namespace is loaded `eval-buttle-user-form` will be
  executed.

  __WARNING:__ this means that anyone who controls the system
  properties of the hosting JVM can run any Clojure code (but then
  again --- if someone controls the system properties he/she is
  probably able to run any command anyway).

  Functions in this namespace deliver all the functionality needed for
  the `java.sql.Driver` interface/contract. Things for connections,
  statements etc. are all delivered through `buttle.proxy`."
  
  (:gen-class
   :init init
   :state state
   :name buttle.jdbc.Driver
   :extends buttle.SetContextClassLoaderInStaticInitializer
   :implements [java.sql.Driver])
  (:require [buttle.driver-manager :as mgr]
            [buttle.util :as util]
            [buttle.proxy :as proxy]))

(defn parse-jdbc-url
  "Parses a _Buttle_ JDBC url.

   A _Buttle_ JDBC url has the format `#\"jdbc:buttle:(.+)\"`. Any
   text after `jdbc:buttle:` will be `read-string-eval'ed` and should
   yield a map with keys `:target-url`, `:user` and `:password`. The
   `eval`'ed value is returned (even if not a map). If `url` does not
   match the pattern `nil` is returned. If `read-string-eval` throws
   an `Exception` then an `Exception` will be thrown."

  [url]
  (try 
    (some-> (re-matches #"jdbc:buttle:(.+)" url)
            second
            read-string
            eval)
    (catch Throwable t
      (throw (ex-info "Could not parse url" {:url url} t)))))

(defn accepts-url-fn
  "Parses `url` via `parse-jdbc-url` and retrieves the keys
  `:target-url`, `:user` and `:password` from the returned
  value (assuming that it is a map). Returns a map with `:target-url`,
  `:user` and `:password`."

  [url]
  (when-let [{:keys [target-url user password]} (parse-jdbc-url url)]
    {:target-url target-url
     :user user
     :password password}))

(defn connect-fn
  "Returns `nil` if `url` is not a _Buttle_ url (as of
  `accepts-url-fn`). Else opens a JDBC `Connection` to `:target-url`
  with `:user` and `:password` via
  `buttle.driver-manager/get-connection`. If that throws then this
  function throws. Otherwise the connection is returned."

  [url]
  (when-let [{:keys [target-url user password] :as args} (accepts-url-fn url)]
    (mgr/get-connection target-url user password)))

(defn make-driver
  "Creates and returns a _Buttle_ `java.sql.Driver`.

   Note that the underlying driver (which delegates to the real
   driver) is a Clojure `proxy` (not an instance of
   `buttle.jdbc.Driver`) which is wrapped by a
   `buttle.proxy/make-proxy`. So calls to retured driver can be
   intercepted by `buttle.proxy/def-handle`.

   This driver can be registered with the
   `java.sql.DriverManager`. There are two important methods that this
   driver (proxy) implements: `connect` and `acceptsURL`. These are
   needed for interaction with the `DriverManager` so that the
   _Buttle_ driver can be _picked up_ for _Buttle_ urls.

   Note: the _Buttle_ proxy will set the current thread's context
   classloader to _Buttle_'s classloader when delegating to
   `buttle.proxy/handle`. This is needed for cases when _Buttle_ is
   used as a data-source and deployed as a _module_ in Wildfly/JBoss
   application server. In this case `clojure.lang.RT` tries to load
   `clojure/core.clj` which it can't because it uses the current
   thread's context classloader and for Wildfly that will not be the
   _Buttle_ module's classloader. So we explicitly set the tccl and
   things work. In non-application-server cases this usually does not
   hurt (but it may some day..)

   At the moment you cannot configure this feature."

  []
  (proxy/make-proxy
   java.sql.Driver
   (proxy [java.sql.Driver] []
     ;; java.sql.Driver.connect(String url, Properties info)
     (connect [url info]
       (connect-fn url))
     ;; boolean acceptsURL(String url)
     (acceptsURL [url]
       (boolean (accepts-url-fn url)))
     ;; DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info)
     (getPropertyInfo [url info]
       (make-array java.sql.DriverPropertyInfo 0))
     ;; int getMajorVersion();
     (getMajorVersion []
       47)
     ;; int getMinorVersion();
     (getMinorVersion []
       11)
     ;; boolean jdbcCompliant();
     (jdbcCompliant []
       true)
     ;; Logger getParentLogger()
     (getParentLogger []
       (java.util.logging.Logger/getLogger "buttle")))
   (fn [the-method target-obj the-args]
     (util/with-tccl (.getClassLoader buttle.jdbc.Driver)
       (proxy/handle the-method target-obj the-args)))))

(def -init
  "Constructor function of `buttle.jdbc.Driver`.

   On first invokation creates a _Buttle_ `Driver` (see
   `make-driver`), keeps a reference to it and registers it with the
   `java.sql.DriverManager`.

   This _Buttle_ driver becomes the internal `state` of the
   `buttle.jdbc.Driver`. All `Driver` method implemenations of this
   class delegate to this internal driver."
  
  (let [driver (atom nil)]
    (fn []
      (if-let [r @driver]
        [[] r]
        (let [r (make-driver)]
          (mgr/register-driver r)
          (reset! driver r)
          [[] r])))))

(defn -connect
  "Implements `java.sql.Driver.connect(String, Properties)`. Just
  delegates to the referenced/internal driver (see `-init`)."

  [this url info]
  (.connect (.state this) url info))

(defn -acceptsURL
  "Implements `java.sql.Driver.acceptsURL(String)`. Just delegates to
  the referenced/internal driver (see `-init`)."

  [this url]
  (.acceptsURL (.state this) url))

(defn -getPropertyInfo
  "Just delegates to the referenced/internal driver (see `-init`)."
  
  [this url info]
  (.getPropertyInfo (.state this) url info))
  
(defn -getMajorVersion
  "Just delegates to the referenced/internal driver (see `-init`)."

  [this]
  (.getMajorVersion (.state this)))

(defn -getMinorVersion
  "Just delegates to the referenced/internal driver (see `-init`)."
  
  [this]
  (.getMinorVersion (.state this)))

(defn -jdbcCompliant
  "Just delegates to the referenced/internal driver (see `-init`)."

  [this]
  (.jdbcCompliant (.state this)))

(defn -getParentLogger
  "Just delegates to the referenced/internal driver (see `-init`)."
  
  [this]
  (.getParentLogger (.state this)))
  
(defn eval-buttle-user-file!
  "If system property `buttle.user-file` is set, uses `load-file` to
  evaluate that file.

  This function is called when namespace `buttle.driver` is
  loaded. This happens for example when die _Buttle_ JDBC driver is
  loaded.

  Use this function to load your own code when you do not control the
  main program flow (like when using _Buttle_ in tools like SQuirreL
  or in a Java application server when you do not control/own the main
  application)."
  
  []
  (when-let [user-file (System/getProperty "buttle.user-file")]
    (try 
      (load-file user-file)
      (catch Throwable t
        (.println System/err (format "(eval-buttle-user-file! %s) failed: %s" (pr-str user-file) t))))))

;; Note - this will execute when lein compiling, but should do no harm
(eval-buttle-user-file!)
