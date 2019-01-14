(ns buttle.driver
  "The _Buttle_ `java.sql.Driver`.

  This namespace delivers `buttle.jdbc.Driver` via `:gen-class`. This
  named class can be used by tools (like SQuirreL) and the SPI
  `services/java.sql.Driver`. Note that this class has implementations
  for `acceptsURL` and `connect` only.

  The `-init` constructor function will register a _Buttle_ `Driver`
  proxy with the `java.sql.DriverManager`. So whenever an instance of
  `buttle.jdbc.Driver` is created, a proxy ( __not__ the
  `buttle.jdbc.Driver`!) is registered.

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
   :name buttle.jdbc.Driver
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
  function throws. Otherwise a _Buttle_ `Connection` proxy for the
  opened `Connection` with `buttle.proxy/handle` is returned."

  [url]
  (when-let [{:keys [target-url user password] :as args} (accepts-url-fn url)]
    (proxy/make-proxy
     java.sql.Connection
     (mgr/get-connection target-url user password)
     #'proxy/handle)))

(defn make-driver
  "Creates and returns a _Buttle_ `java.sql.Driver`.

   This driver can be registered with the
   `java.sql.DriverManager`. There are two important methods that this
   driver (proxy) implements: `connect` and `acceptsURL`. These are
   needed for interaction with the `DriverManager` so that the
   _Buttle_ driver can be _picked up_ for _Buttle_ urls."

  []
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
      (java.util.logging.Logger/getLogger "buttle"))))

(defn -init
  "Registers a _Buttle_ `Driver` proxy with the
  `java.sql.DriverManager`. This function is the _constructor_ of
  `buttle.jdbc.Driver`. So whenever an instance of
  `buttle.jdbc.Driver` is created, a proxy ( __not__ the
  `buttle.jdbc.Driver`!) is registered."
  
  []
  (mgr/register-driver (make-driver))
  [[] nil])

(defn -connect
  "Implements `java.sql.Driver.connect(String, Properties)`. Just
  call `(connect-fn url)`."

  [this url info]
  (connect-fn url))

(defn -acceptsURL
  "Implements `java.sql.Driver.acceptsURL(String)`. Just calls
  to `(boolean (accepts-url-fn url))`."

  [this url]
  (boolean (accepts-url-fn url)))

(defn eval-buttle-user-form
  "If system property `buttle.user-form` is set, uses `(-> read-string
  eval)` to evaluate that string. This function is called when
  namespace `buttle.driver` is loaded. This happens for example when
  die _Buttle_ JDBC driver is loaded.

  Use this function to load your own code when you do not control the
  main program flow (like when using _Buttle_ in tools like
  SQuirreL)."

  []
  (when-let [user-form (System/getProperty "buttle.user-form")]
    (try 
      (eval (read-string user-form))
      (catch Throwable t
        (.println System/err (format "(eval-buttle-user-form %s) failed: %s" (pr-str user-form) t))))))

;; invoke `eval-buttle-user-form` when namespace is loaded. See
;; example in `examples/buttle/examples/event_channel.clj`
(eval-buttle-user-form)
