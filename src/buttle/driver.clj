(ns buttle.driver
  (:gen-class
   :name buttle.jdbc.Driver
   :implements [java.sql.Driver])
  (:require [buttle.driver-manager :as mgr]
            [buttle.connection :as conn]
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
  opened `Connection` with `buttle.connection/handle` is returned."

  [url]
  (when-let [{:keys [target-url user password]} (accepts-url-fn url)]
    (proxy/make-proxy
     java.sql.Connection
     (mgr/get-connection target-url user password)
     #'conn/handle)))

(defn make-driver
  "Creates and returns a _Buttle_ `java.sql.Driver`.

   This driver can be registered with the `java.sql.DriverManager` via
   `register-driver`. There are two important methods that this
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

(defn register-driver []
  (mgr/register-driver (make-driver)))

(defn -connect [this url info]
  (connect-fn url))
