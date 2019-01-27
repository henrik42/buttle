(ns buttle.data-source
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

(defn lookup-data-source [jndi]
  (when-not jndi
    (throw (RuntimeException. "No `jndi` property set.")))
  (with-open [ctx (InitialContext.)]
    (.lookup ctx jndi)))

(defn make-data-source []
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

(defn -init []
  [[] (make-data-source)])

(defn -setUrl [this url]
  (.setUrl (.state this) url))

(defn -setJndi [this jndi]
  (.setJndi (.state this) jndi))

(defn -getConnection
  ([this]
     (.getConnection (.state this)))
  ([this username password]
     (.getConnection (.state this) username password)))

(defn -getLogWriter [this]
  (.getLogWriter (.state this)))

(defn -setLogWriter [this pr-wrt]
  (.setLogWriter (.state this) pr-wrt))

(defn -setLoginTimeout [this sec]
  (.setLoginTimeout (.state this) sec))

(defn -getLoginTimeout [this]
  (.getLoginTimeout (.state this)))

(defn -getParentLogger [this]
  (.getParentLogger (.state this)))
  
(defn -unwrap [this ifc]
  (.unwrap (.state this) ifc))

(defn -isWrapperFor [this ifc]
  (.isWrapperFor (.state this) ifc))
