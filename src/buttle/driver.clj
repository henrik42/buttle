(ns buttle.driver
  (:require [buttle.util :as util]
            [buttle.driver-manager :as mgr]
            [buttle.connection :as conn]
            [buttle.proxy :as proxy]))

#_
(parse-jdbc-url "jdbc:buttle:{:target-url \"jdbc:postgresql://psql-serv1.innovas.de:6532/psql_entw}\"}")

(defn parse-jdbc-url [url]
  (try 
    (some-> (re-matches #"jdbc:buttle:(.+)" url)
            second
            read-string
            eval)
    (catch Throwable t
      (throw (ex-info "Could not parse url" {:url url} t)))))

#_
(accepts-url-fn "jdbc:buttle:{:target-url \"jdbc:postgresql://psql-serv1.innovas.de:6532/psql_entw}\"}")

(defn accepts-url-fn [url]
  {:post [(util/log (format "(%s/accepts-url-fn %s) --> %s" *ns* (pr-str url) %))]}
  (util/log (format "(%s/accepts-url-fn %s)" *ns* (pr-str url)))
  (when-let [{:keys [target-url user password]} (parse-jdbc-url url)]
    {:target-url target-url
     :user user
     :password password}))

#_
(mgr/register-driver (make-driver))

#_
(mgr/get-driver "jdbc:buttle:{:target-url \"jdbc:postgresql://psql-serv1.innovas.de:6532/psql_entw}\"}")

#_
(.prepareStatement
 (connect-fn "jdbc:buttle:{:user \"xkv\" :password \"Waldfee\" :target-url \"jdbc:postgresql://psql-serv1.innovas.de:6532/psql_entw\"}")
 "foo")

(defn connect-fn [url]
  {:post [(util/log (format "(%s/connect-fn %s) --> %s" *ns* (pr-str url) %))]}
  (util/log (format "(%s/connect-fn %s)" *ns* (pr-str url)))
  (when-let [{:keys [target-url user password]} (accepts-url-fn url)]
    (conn/make-proxy (mgr/get-connection target-url user password))))

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
      42)
    ;; int getMinorVersion();
    (getMinorVersion []
      42)
    ;; boolean jdbcCompliant();
    (jdbcCompliant []
      true)
    ;; Logger getParentLogger()
    (getParentLogger []
      (java.util.logging.Logger/getLogger "buttle"))))

