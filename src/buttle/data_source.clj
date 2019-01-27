(ns buttle.data-source
  (:gen-class
   :init init
   :state state
   :name buttle.jdbc.DataSource
   :implements [javax.sql.DataSource]
   :methods [[setUrl [String] void]
             [setJndi [String] void]])
  (:import [java.sql SQLException]
           [java.sql SQLFeatureNotSupportedException])
  (:require [buttle.proxy :as proxy]))

#_
(defn create-initial-context-factory []
  (proxy [org.osjava.sj.MemoryContextFactory] []
    (getInitialContext [env]
      (proxy-super
       getInitialContext
       (doto (.clone env)
         (.put "org.osjava.sj.jndi.ignoreClose" "true")
         (.put "org.osjava.sj.jndi.shared" "true"))))))

#_
(javax.naming.spi.NamingManager/setInitialContextFactoryBuilder
 (proxy [javax.naming.spi.InitialContextFactoryBuilder] []
   (createInitialContextFactory [_]
     (create-initial-context-factory))))

#_
(let [env (doto (java.util.Hashtable.)
            #_ (.put "org.osjava.sj.jndi.ignoreClose" "true")
            #_ (.put "org.osjava.sj.jndi.shared" "true"))]
  (with-open [ctx (javax.naming.InitialContext. env)]
    (.rebind ctx "foo" "bar"))
  (with-open [ctx (javax.naming.InitialContext. env)]
    (.lookup ctx "foo")))

#_ ;; (lookup-data-source "data-source")
(with-open [ctx (javax.naming.InitialContext.)]
  (.rebind ctx "data-source"
           (proxy [javax.sql.DataSource] [])))

;; https://docs.oracle.com/javase/8/docs/api/javax/naming/InitialContext.html
;; https://www.javacodegeeks.com/2012/04/jndi-and-jpa-without-j2ee-container.html
(defn lookup-data-source [jndi]
  (when-not jndi
    (throw (RuntimeException. "No `jndi` property set.")))
  (with-open [ctx (javax.naming.InitialContext.)]
    (or
     (-> ctx
         (.lookup jndi))
     (throw
      (RuntimeException. (format "Could not find JNDI '%s'." jndi))))))

(definterface ButtleDataSource
  (setUrl [^String url])
  (setJndi [^String jndi]))

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
     [javax.sql.DataSource ButtleDataSource]
     (proxy [javax.sql.DataSource ButtleDataSource] []
       (setUrl [url])
       (setJndi [jndi])
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
