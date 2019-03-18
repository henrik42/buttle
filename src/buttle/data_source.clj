(ns buttle.data-source
  "The _Buttle_ `javax.sql.DataSource`.

  This namespace delivers `buttle.jdbc.DataSource` via
  `:gen-class`. This named class can be used as a datasource class for
  application servers."
  
  (:import [javax.sql DataSource]
           [java.sql SQLException]
           [java.sql SQLFeatureNotSupportedException])
  (:require [buttle.proxy :as proxy]
            [buttle.util :as util]))
  
(definterface ButtleDataSource
  (^void setDelegateSpec [^String spec]))

(gen-class
 :init init
 :state state
 :name buttle.jdbc.DataSource
 :extends buttle.SetContextClassLoaderInStaticInitializer
 :implements [javax.sql.DataSource
              buttle.data_source.ButtleDataSource])

(defn spec->type
  "Dispatch for `retrieve-data-soure`. Returns type of `spec` (`:jndi`
  for `String`, `:ds-class` for maps)."

  [spec]
  (cond
   (string? spec) :jndi
   (map? spec) :ds-class
   :else (format "Unknown spec '%s'" (pr-str spec))))

(defmulti retrieve-data-soure 
  "Factory/lookup for _real_ datasource. `String` arg will be expected
  to be JNDI name of a `javax.sql.DataSource`. In this case the
  datasource will be looked up in JNDI. If the arg is a map the
  class-typed `:delegate-class` will be used to create an instance and
  then all remaining keys/values will be used to set the instance's
  Java-Bean properties."

  #'spec->type)

;; Create instance and invoke setters. Setter-names are derived from
;; map keys by camel-casing `:foo-bar` to `setFooBar`.
(defmethod retrieve-data-soure :ds-class [ds-class-spec]
  (util/->java-bean (:delegate-class ds-class-spec)
                    (dissoc ds-class-spec :delegate-class)))

;; Retrieve datasource and check that it *really IS* a
;; datasource. Else we won't be able to delegate to it.
(defmethod retrieve-data-soure :jndi [jndi-spec]
  (let [ds (util/jndi-lookup jndi-spec)]
    (when-not (isa? (.getClass ds) javax.sql.DataSource)
      (throw
       (RuntimeException.
        (format "This is not a javax.sql.DataSource: '%s'  It implements these interfaces: '%s'"
                ds (->> ds .getClass .getInterfaces (into []))))))
    ds))

;; ----------------------------------------------------------------------------------------------------------------------
;; A note on classloading
;;
;; See src/buttle/connection_pool_data_source.clj for details.
;; ----------------------------------------------------------------------------------------------------------------------

(defn make-data-source
  "Creates the _Buttle_ datasource."

  []
  (let [ds-spec (atom nil)
        ds (atom nil)
        ;; classloader of cached proxy class definition
        cl (-> (proxy [DataSource ButtleDataSource] [])
               .getClass
               .getClassLoader)
        ;; lazy creation and cache
        ds! (fn [] (or @ds
                       (reset! ds
                               (retrieve-data-soure @ds-spec))))]
    
    ;; make Java dyn. proxy D which uses cl as its definiting classloader
    (proxy/make-proxy
     [(Class/forName "buttle.data_source.ButtleDataSource" true cl) DataSource]

     ;; proxy should have classloader cl
     (proxy [DataSource ButtleDataSource] []
       (setDelegateSpec [spec]
         (try
           (util/with-tccl (.getClassLoader (Class/forName "buttle.jdbc.DataSource"))
             (reset! ds-spec
                     (-> spec
                         (.replaceAll "[\\n\\t]+" " ")
                         read-string
                         eval)))
           (catch Throwable t
             (throw (ex-info "Could not parse spec" {:spec spec} t)))))
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

(defn -setDelegateSpec
  "Sets the `delegateSpec` of the _Buttle_ datasource."
  
  [this spec]
  (.setDelegateSpec (.state this) spec))

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
