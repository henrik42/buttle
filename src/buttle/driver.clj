(ns buttle.driver
  (:require [buttle.driver-manager :as mgr]
            [buttle.connection :as conn]
            [buttle.util :as util]
            [buttle.proxy :as proxy]))

(defn parse-jdbc-url [url]
  (try 
    (some-> (re-matches #"jdbc:buttle:(.+)" url)
            second
            read-string
            eval)
    (catch Throwable t
      (throw (ex-info "Could not parse url" {:url url} t)))))

(defn accepts-url-fn [url]
  (when-let [{:keys [target-url user password]} (parse-jdbc-url url)]
    {:target-url target-url
     :user user
     :password password}))

(defn connect-fn [url]
  (when-let [{:keys [target-url user password]} (accepts-url-fn url)]
    (proxy/make-proxy
     java.sql.Connection
     (mgr/get-connection target-url user password)
     conn/handle)))

(defn make-driver []
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

