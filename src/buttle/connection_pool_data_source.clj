(ns buttle.connection-pool-data-source
  (:import [javax.sql ConnectionPoolDataSource])
  (:require [buttle.proxy :as proxy]
            [buttle.util :as util]))

(definterface ButtleCpDataSource 
  (^void setDelegateSpec [^String spec]))

(gen-class
 :init init
 :state state
 :name buttle.jdbc.ConnectionPoolDataSource
 :extends buttle.SetContextClassLoaderInStaticInitializer
 :implements [javax.sql.ConnectionPoolDataSource
              buttle.connection_pool_data_source.ButtleCpDataSource])

(defn spec->type
  [spec]
  (cond
   (string? spec) :jndi
   (map? spec) :cp-class
   :else (format "Unknown spec '%s'" (pr-str spec))))

(defmulti retrieve-cp-data-soure 
  #'spec->type)

(defmethod retrieve-cp-data-soure :jndi [jndi-spec]
  (let [cp-ds (util/jndi-lookup jndi-spec)]
    (when-not (isa? (.getClass cp-ds) ConnectionPoolDataSource)
      (throw
       (RuntimeException.
        (format "This is not a javax.sql.ConnectionPoolDataSource: '%s'  It implements these interfaces: '%s'"
                cp-ds (->> cp-ds .getClass .getInterfaces (into []))))))
    cp-ds))

(defmethod retrieve-cp-data-soure :cp-class [cp-class-spec]
  (let [clazz (:delegate-class cp-class-spec)]
    (when-not (isa? clazz ConnectionPoolDataSource)
      (throw
       (RuntimeException.
        (format "This is not a javax.sql.ConnectionPoolDataSource: '%s'  It implements these interfaces: '%s'"
                clazz (->> clazz .getInterfaces (into []))))))
    (util/->java-bean clazz (dissoc cp-class-spec :delegate-class))))

(defn make-cp-data-source
  []
  (let [cp-ds-spec (atom nil)
        cp-ds (atom nil)
        ;; lazy creation and cache
        cp-ds! (fn [] (or @cp-ds
                          (reset! cp-ds
                                  (retrieve-cp-data-soure @cp-ds-spec))))]
    (proxy/make-proxy
     [ConnectionPoolDataSource ButtleCpDataSource]
     (proxy [ConnectionPoolDataSource ButtleCpDataSource] []
       (setDelegateSpec [spec]
         (try
           (util/with-tccl (.getClassLoader (Class/forName "buttle.jdbc.ConnectionPoolDataSource"))
             (reset! cp-ds-spec
                     (-> spec
                         (.replaceAll "[\\n\\t]+" " ")
                         read-string
                         eval)))
           (catch Throwable t
             (throw (ex-info "Could not parse spec" {:spec spec} t)))))
       (getPooledConnection [& [user password :as xs]]
         (if-not xs (-> (cp-ds!) .getPooledConnection)
                 (-> (cp-ds!) (.getPooledConnection user password))))
       (getLogWriter []
         (-> (cp-ds!) .getLogWriter))
       (setLogWriter [pr-wrt]
         (-> (cp-ds!) (.setLogWriter pr-wrt)))
       (setLoginTimeout [sec]
         (-> (cp-ds!) (.setLoginTimeout sec)))
       (getLoginTimeout []
         (-> (cp-ds!) .getLoginTimeout))
       (getParentLogger []
         (-> (cp-ds!) .getParentLogger)))
     (fn [the-method target-obj the-args]
       ;; set TCCL so that CLojure RT/Compiler uses correct
       ;; classloader.
       (util/with-tccl (.getClassLoader (Class/forName "buttle.jdbc.ConnectionPoolDataSource"))
         (proxy/handle the-method target-obj the-args))))))

(defn -init
  []
  [[] (make-cp-data-source)])

(defn -setDelegateSpec
  [this spec]
  (.setDelegateSpec (.state this) spec))

(defn -getPooledConnection
  ([this]
     (.getPooledConnection (.state this)))
  ([this username password]
     (.getPooledConnection (.state this) username password)))

(defn -getLogWriter
  "Implements `javax.sql.CommonDataSource/getLogWriter`. Just
  delegates to the referenced/internal cp-datasource (see `-init`)."

  [this]
  (.getLogWriter (.state this)))

(defn -setLogWriter
  "Implements `javax.sql.CommonDataSource/setLogWriter`. Just
  delegates to the referenced/internal cp-datasource (see `-init`)."

  [this pr-wrt]
  (.setLogWriter (.state this) pr-wrt))

(defn -setLoginTimeout
  "Implements `javax.sql.CommonDataSource/setLoginTimeout`. Just
  delegates to the referenced/internal cp-datasource (see `-init`)."

  [this sec]
  (.setLoginTimeout (.state this) sec))

(defn -getLoginTimeout
  "Implements `javax.sql.CommonDataSource/getLoginTimeout`. Just
  delegates to the referenced/internal cp-datasource (see `-init`)."
  
  [this]
  (.getLoginTimeout (.state this)))

(defn -getParentLogger
  "Implements `javax.sql.CommonDataSource/getParentLogger`. Just
  delegates to the referenced/internal cp-datasource (see `-init`)."

  [this]
  (.getParentLogger (.state this)))
  
