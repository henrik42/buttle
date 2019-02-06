(ns buttle.data-source
  "The _Buttle_ `javax.sql.DataSource`.

  This namespace delivers `buttle.jdbc.DataSource` via
  `:gen-class`. This named class can be used as a datasource class for
  application servers.

  __Note:__ This namespace has not yet been tested in an application
  server. There are application servers (like Wildfly) which use their
  own `javax.sql.DataSource` implementation which delegates to the
  JDBC driver's `java.sql.Driver` implementation. So in these cases
  the JDBC driver does not even need to deliver an implementation for
  `javax.sql.DataSource`."
  
  (:gen-class
   :init init
   :state state
   :name buttle.jdbc.DataSource
   :implements [javax.sql.DataSource]
   :methods [[setUrl [String] void]
             [setJndi [String] void]])
  (:import [javax.naming InitialContext]
           [javax.sql DataSource]
           [java.sql SQLException]
           [java.sql SQLFeatureNotSupportedException])
  (:require [buttle.proxy :as proxy]))

(definterface ButtleDataSource
  (setUrl [^String url])
  (setJndi [^String jndi]))

(defn lookup-data-source
  "Looks up JNDI entry `jndi` and returns it."

  [jndi]
  (when-not jndi
    (throw (RuntimeException. "No `jndi` property set.")))
  (with-open [ctx (InitialContext.)]
    (.lookup ctx jndi)))

(defn make-data-source
  "Creates the _Buttle_ datasource."

  []
  (let [ds (atom nil)
        url (atom nil) 
        jndi (atom nil)
        ds! (fn []
              (or @ds
                  (reset!
                   ds
                   (lookup-data-source @jndi))))]
    (proxy/make-proxy
     [DataSource ButtleDataSource]
     (proxy [DataSource ButtleDataSource] []
       ;; TODO (setUrl [url])
       (setJndi [x]
         (reset! jndi x))
       (getConnection [& [user password :as xs]]
         (if-not xs (-> (ds!) .getConnection)
                 (-> (ds!) (.getConnection user password))))
       (getLogWriter []
         (-> (ds!) .getLogWriter))
       (setLogWriter [pr-wrt]
         (-> (ds!) (.setLogWriter pr-wrt)))
       (setLoginTimeout [sec]
         (-> (ds!) (.setLoginTimeout sec)))
       (getLoginTimeout []
         (-> (ds!) .getLoginTimeout))
       (getParentLogger []
         (-> (ds!) .getParentLogger))
       (unwrap [ifc]
         (-> (ds!) (.unwrap ifc)))
       (isWrapperFor [ifc]
         (-> (ds!) (.isWrapperFor ifc))))
     proxy/handle)))

(defn -init
  "Constructor function of `buttle.jdbc.DataSource`."

    []
    [[] (make-data-source)])

(defn -setUrl
  [this url]
  (.setUrl (.state this) url))

(defn -setJndi [this jndi]
  (.setJndi (.state this) jndi))

(defn -getConnection
  "Implements `javax.sql.DataSource/getConnection`. Just delegates to
  the referenced/internal datasource (see `-init`)."

  ([this]
     (.getConnection (.state this)))
  ([this username password]
     (.getConnection (.state this) username password)))

(defn -getLogWriter
  "Implements `javax.sql.CommonDataSource/getLogWriter`. Just
  delegates to the referenced/internal datasource (see `-init`)."

  [this]
  (.getLogWriter (.state this)))

(defn -setLogWriter
  "Implements `javax.sql.CommonDataSource/setLogWriter`. Just
  delegates to the referenced/internal datasource (see `-init`)."

  [this pr-wrt]
  (.setLogWriter (.state this) pr-wrt))

(defn -setLoginTimeout
  "Implements `javax.sql.CommonDataSource/setLoginTimeout`. Just
  delegates to the referenced/internal datasource (see `-init`)."

  [this sec]
  (.setLoginTimeout (.state this) sec))

(defn -getLoginTimeout
  "Implements `javax.sql.CommonDataSource/getLoginTimeout`. Just
  delegates to the referenced/internal datasource (see `-init`)."
  
  [this]
  (.getLoginTimeout (.state this)))

(defn -getParentLogger
  "Implements `javax.sql.CommonDataSource/getParentLogger`. Just
  delegates to the referenced/internal datasource (see `-init`)."

  [this]
  (.getParentLogger (.state this)))
  
(defn -unwrap
  "Implements `java.sql.Wrapper/unwrap`. Just delegates to the
  referenced/internal datasource (see `-init`)."

  [this ifc]
  (.unwrap (.state this) ifc))

(defn -isWrapperFor
  "Implements `java.sql.Wrapper/isWrapperFor`. Just delegates to the
  referenced/internal datasource (see `-init`)."

  [this ifc]
  (.isWrapperFor (.state this) ifc))
