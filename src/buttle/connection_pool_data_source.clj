(ns buttle.connection-pool-data-source
  "The _Buttle_ `javax.sql.ConnectionPoolDataSource` (CP-datasource).

  This namespace delivers `buttle.jdbc.ConnectionPoolDataSource` via
  `:gen-class`. This named class can be used as a CP-datasource class
  for application servers."

  (:import [javax.sql ConnectionPoolDataSource])
  (:require [buttle.proxy :as proxy]
            [buttle.driver]
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
  "Dispatch for `retrieve-cp-data-soure`. Returns type of
  `spec` (`:jndi` for `String`, `:cp-class` for maps)."
  
  [spec]
  (cond
   (string? spec) :jndi
   (map? spec) :cp-class
   :else (format "Unknown spec '%s'" (pr-str spec))))

(defmulti retrieve-cp-data-soure 
  "Factory/lookup for _real_ CP-datasource. `String` arg will be
  expected to be JNDI name of a
  `javax.sql.ConnectionPoolDataSource`. In this case the CP-datasource
  will be looked up in JNDI. If the arg is a map the class-typed
  `:delegate-class` will be used to create an instance and then all
  remaining keys/values will be used to set the instance's Java-Bean
  properties."
  
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

;; ----------------------------------------------------------------------------------------------------------------------
;; A note on classloading
;;
;; buttle.proxy/make-proxy creates a Java dyn. proxy D which
;; reflectivly calls methods M on the Clojure proxy P that's created
;; below.
;;
;; This only works if D's class definition matches P's class
;; definition. Otherwise M of D can not be called on P.
;;
;; P is defined by Clojure's dynamic claassloader. So we have to make
;; D be defined by them same classloader. Clojure's proxy classes are
;; cached by key (which is [ConnectionPoolDataSource
;; ButtleCpDataSource] below) so we calculate it's classloader cl and
;; then use it to load class/interface
;; buttle.connection_pool_data_source.ButtleCpDataSource.
;; buttle.proxy/make-proxy uses the first argument's classloader to
;; define D. This makes D and P being compatible and M can be called.
;;
;; Only for IBM WAS I had to do this. Wildfly's classloaders work
;; different somehow.
;; ----------------------------------------------------------------------------------------------------------------------

(defn make-cp-data-source
  "Creates and returns a _Buttle_ `javax.sql.ConnectionPoolDataSource`.

  Use `setDelegateSpec` to control what the _real_ (or _backing_)
  `javax.sql.ConnectionPoolDataSource` is. You can use `String` to use
  a CP-datasource from JNDI. Use a map to create an instance and set
  properties (see `retrieve-cp-data-soure` for details).

  Note: creation is done on-demand when the backing CP-datasource is
  actually needed/used."

  []
  (let [cp-ds-spec (atom nil)
        cp-ds (atom nil)
        ;; classloader of cached proxy class definition
        cl (-> (proxy [ConnectionPoolDataSource ButtleCpDataSource] [])
               .getClass
               .getClassLoader)
        ;; lazy creation and cache
        cp-ds! (fn [] (or @cp-ds
                          (reset! cp-ds
                                  (retrieve-cp-data-soure @cp-ds-spec))))]
    
    ;; make Java dyn. proxy D which uses cl as its definiting classloader
    (proxy/make-proxy
     [(Class/forName "buttle.connection_pool_data_source.ButtleCpDataSource" true cl) ConnectionPoolDataSource]
     
     ;; proxy should have classloader cl
     (proxy [ConnectionPoolDataSource ButtleCpDataSource] []
       (setDelegateSpec [spec]
         (try
           (util/with-tccl (.getClassLoader (Class/forName "buttle.jdbc.ConnectionPoolDataSource"))
             (reset! cp-ds-spec
                     (-> spec
                         (.replaceAll "[\\n\\t\\r]+" " ")
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
  "Constructor function of
  `buttle.jdbc.ConnectionPoolDataSource`. Calls `make-cp-data-source`
  and initialized `state` with that (i.e. the _Buttle_ CP-datasource
  is cached)."

  []
  [[] (make-cp-data-source)])

(defn -setDelegateSpec
  "Implements
  `buttle.connection_pool_data_source.ButtleCpDataSource/setDelegateSpec`. Just
  delegates to the referenced/internal _Buttle_ CP-datasource (see
  `-init`)."
  
  [this spec]
  (.setDelegateSpec (.state this) spec))

(defn -getPooledConnection
  "Implements
  `javax.sql.ConnectionPoolDataSource/getPooledConnection`. Just
  delegates to the referenced/internal CP-datasource (see `-init`)."
  
  ([this]
     (.getPooledConnection (.state this)))
  ([this username password]
     (.getPooledConnection (.state this) username password)))

(defn -getLogWriter
  "Implements `javax.sql.CommonDataSource/getLogWriter`. Just
  delegates to the referenced/internal CP-datasource (see `-init`)."

  [this]
  (.getLogWriter (.state this)))

(defn -setLogWriter
  "Implements `javax.sql.CommonDataSource/setLogWriter`. Just
  delegates to the referenced/internal CP-datasource (see `-init`)."

  [this pr-wrt]
  (.setLogWriter (.state this) pr-wrt))

(defn -setLoginTimeout
  "Implements `javax.sql.CommonDataSource/setLoginTimeout`. Just
  delegates to the referenced/internal CP-datasource (see `-init`)."

  [this sec]
  (.setLoginTimeout (.state this) sec))

(defn -getLoginTimeout
  "Implements `javax.sql.CommonDataSource/getLoginTimeout`. Just
  delegates to the referenced/internal CP-datasource (see `-init`)."
  
  [this]
  (.getLoginTimeout (.state this)))

(defn -getParentLogger
  "Implements `javax.sql.CommonDataSource/getParentLogger`. Just
  delegates to the referenced/internal CP-datasource (see `-init`)."

  [this]
  (.getParentLogger (.state this)))
  
