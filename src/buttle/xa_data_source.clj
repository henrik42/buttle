(ns buttle.xa-data-source
  "The _Buttle_ `javax.sql.XADataSource`.

  This namespace delivers `buttle.jdbc.XADataSource` via
  `:gen-class`. This named class can be used as an xa-datasource class
  for application servers.

  __Example (Wildfly)__:


        <xa-datasource jndi-name=\"java:/jdbc/buttle-xa\" pool-name=\"buttle-xa\">
          <xa-datasource-class>buttle.jdbc.XADataSource</xa-datasource-class>
          <driver>buttle-driver</driver>
          <security>
            <user-name>postgres-user</user-name>
            <password>postgres-password</password>
          </security>
          <xa-datasource-property name=\"XaDatasourceSpec\">
            {:xa-datasource-class org.postgresql.xa.PGXADataSource
             :url \"jdbc:postgresql://127.0.0.1:6632/postgres\"}
          </xa-datasource-property>
        </xa-datasource>"
  
  (:import [javax.sql XADataSource])
  (:require [buttle.proxy :as proxy]
            [buttle.util :as util]))

;; Needed for -setXaDatasourceSpec. Otherwise the compile won't
;; generate the method for buttle.jdbc.XADataSource
(definterface ButtleDataSource 
  (^void setDelegateSpec [^String spec]))

;; Cannot be part of `ns` form because references
;; `buttle.xa_data_source.ButtleDataSource` from above
(gen-class
 :init init
 :state state
 :name buttle.jdbc.XADataSource
 :extends buttle.SetContextClassLoaderInStaticInitializer
 :implements [javax.sql.XADataSource
              buttle.xa_data_source.ButtleDataSource])

(defn spec->type
  "Dispatch for `retrieve-xa-data-soure`. Returns type of
  `spec` (`:jndi` for `String`, `:xa-class` for maps)."

  [spec]
  (cond
   (string? spec) :jndi
   (map? spec) :xa-class
   :else (format "Unknown spec '%s'" (pr-str spec))))

(defmulti retrieve-xa-data-soure 
  "Factory/lookup for _real_ xa-datasource. `String` arg will be
  expected to be JNDI name of a `javax.sql.XADataSource`. In this case
  the xa-datasource will be looked up in JNDI. If the arg is a map the
  `:xa-datasource-class` will be used to create an instance and then
  all remaining keys/values will be used to set the instance's
  Java-Bean properties."

  #'spec->type)

;; Retrieve xa-datasource and check that it *really IS* an
;; xa-datasource. Else we won't be able to delegate to it.
(defmethod retrieve-xa-data-soure :jndi [jndi-spec]
  (let [xa-ds (util/jndi-lookup jndi-spec)]
    (when-not (isa? (.getClass xa-ds) javax.sql.XADataSource)
      (throw
       (RuntimeException.
        (format "This is not a javax.sql.XADataSource: '%s'  It implements these interfaces: '%s'"
                xa-ds (->> xa-ds .getClass .getInterfaces (into []))))))
    xa-ds))

;; Create instance and invoke setters. Setter-names are derived from
;; map keys by camel-casing `:foo-bar` to `setFooBar`.
(defmethod retrieve-xa-data-soure :xa-class [xa-class-spec]
  (util/->java-bean (:delegate-class xa-class-spec)
                    (dissoc xa-class-spec :delegate-class)))

(defn make-xa-data-source
  "Creates and returns a _Buttle_ `javax.sql.XADataSource`.

  Use `setXaDatasourceSpec` to control what the _real_ (or _backing_)
  `javax.sql.XADataSource` is. You can use `String` to use an
  xa-datasource from JNDI. Use a map to create an instance and set
  properties (see `retrieve-xa-data-soure` for details).

  Note: creation is done on-demand when the backing xa-datasource is
  actually needed."

  []
  (let [xa-ds-spec (atom nil)
        xa-ds (atom nil)
        ;; lazy creation and cache
        xa-ds! (fn [] (or @xa-ds
                          (reset! xa-ds
                                  (retrieve-xa-data-soure @xa-ds-spec))))]
    (proxy/make-proxy
     [XADataSource ButtleDataSource]
     (proxy [XADataSource ButtleDataSource] []
       (setDelegateSpec [spec]
         (try
           (util/with-tccl (.getClassLoader (Class/forName "buttle.jdbc.XADataSource"))
             (reset! xa-ds-spec
                     (-> spec
                         (.replaceAll "[\\n\\t]+" " ")
                         read-string
                         eval)))
           (catch Throwable t
             (throw (ex-info "Could not parse spec" {:spec spec} t)))))
       (getXAConnection [& [user password :as xs]]
         (if-not xs (-> (xa-ds!) .getXAConnection)
                 (-> (xa-ds!) (.getXAConnection user password))))
       (getLogWriter []
         (-> (xa-ds!) .getLogWriter))
       (setLogWriter [pr-wrt]
         (-> (xa-ds!) (.setLogWriter pr-wrt)))
       (setLoginTimeout [sec]
         (-> (xa-ds!) (.setLoginTimeout sec)))
       (getLoginTimeout []
         (-> (xa-ds!) .getLoginTimeout))
       (getParentLogger []
         (-> (xa-ds!) .getParentLogger)))
     (fn [the-method target-obj the-args]
       ;; set TCCL so that CLojure RT/Compiler uses correct
       ;; classloader.
       (util/with-tccl (.getClassLoader (Class/forName "buttle.jdbc.XADataSource"))
         (proxy/handle the-method target-obj the-args))))))

(defn -init
  "Constructor function of `buttle.jdbc.XADataSource`. Calls
  `make-xa-data-source` and initialized `state` with that (i.e. the
  _Buttle_ xa-datasource is cached)."

  []
  [[] (make-xa-data-source)])

(defn -setDelegateSpec
  "Implements
  `buttle.xa_data_source.ButtleDataSource/setDelegateSpec`. Just
  delegates to the referenced/internal _Buttle_ xa-datasource (see
  `-init`)."

  [this spec]
  (.setDelegateSpec (.state this) spec))

(defn -getXAConnection
  "Implements `javax.sql.XADataSource/getXAConnection`. Just delegates
  to the referenced/internal xa-datasource (see `-init`)."

  ([this]
     (.getXAConnection (.state this)))
  ([this username password]
     (.getXAConnection (.state this) username password)))

(defn -getLogWriter
  "Implements `javax.sql.CommonDataSource/getLogWriter`. Just
  delegates to the referenced/internal xa-datasource (see `-init`)."

  [this]
  (.getLogWriter (.state this)))

(defn -setLogWriter
  "Implements `javax.sql.CommonDataSource/setLogWriter`. Just
  delegates to the referenced/internal xa-datasource (see `-init`)."

  [this pr-wrt]
  (.setLogWriter (.state this) pr-wrt))

(defn -setLoginTimeout
  "Implements `javax.sql.CommonDataSource/setLoginTimeout`. Just
  delegates to the referenced/internal xa-datasource (see `-init`)."

  [this sec]
  (.setLoginTimeout (.state this) sec))

(defn -getLoginTimeout
  "Implements `javax.sql.CommonDataSource/getLoginTimeout`. Just
  delegates to the referenced/internal xa-datasource (see `-init`)."
  
  [this]
  (.getLoginTimeout (.state this)))

(defn -getParentLogger
  "Implements `javax.sql.CommonDataSource/getParentLogger`. Just
  delegates to the referenced/internal xa-datasource (see `-init`)."

  [this]
  (.getParentLogger (.state this)))
  
