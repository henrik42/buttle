(ns buttle.xa-data-source
  ""
  
  (:import [javax.naming InitialContext]
           [javax.sql XADataSource])
  (:require [buttle.proxy :as proxy]
            [buttle.util :as util]))

(definterface ButtleDataSource 
  (setXaDatasourceSpec [^String spec]))

(gen-class
 :init init
 :state state
 :name buttle.jdbc.XADataSource
 :implements [javax.sql.XADataSource
              buttle.xa_data_source.ButtleDataSource])

(defn spec->type
  "Dispatch for `retrieve-xa-data-soure`. Returns type of
  `spec` (`:jndi` for `String`, `:xa-class` for maps)."

  [spec]
  (cond
   (isa? spec String) :jndi
   (map? spec) :xa-class
   :else (format "Unknown spec '%s'" (pr-str spec))))

(defmulti retrieve-xa-data-soure 
  "Factory/lookup for _real_ xa-datasource. `String` arg will be
  expected to be JNDI name of a `javax.sql.XADataSource`. In this case
  the xa-datasource will be looked up in JNDI. If the arg is a map the
  `:xa-datasource-class` will be used to create an instance and then
  all remining keys/values will be used to set the instance's
  Java-Bean properties."

  #'spec->type)

(defmethod retrieve-xa-data-soure :jndi [jndi-spec]
  (throw
   (RuntimeException. "not implemented yet: retrieve-xa-data-soure :jndi ")))

(defmethod retrieve-xa-data-soure :xa-class [xa-class-spec]
  (let [xa-datasource-class (:xa-datasource-class xa-class-spec)
        xa-ds (.newInstance xa-datasource-class)
        meths (->> xa-datasource-class
                   .getMethods
                   (map #(-> [(.getName %1) %1]))
                   (into {}))]
    (doseq [[k v] (dissoc xa-class-spec :xa-datasource-class)
            :let [mn (clojure.string/replace
                            (str "set-" (name k))
                            #"-(.)" #(-> % second .toUpperCase))
                  m (meths mn)]]
      (when-not m
        (throw (RuntimeException. (format "Setter not found for %s. Known methods are %s"
                                          [k v mn] (keys meths)))))
      (.invoke m xa-ds (into-array [v])))
    xa-ds))

(defn make-xa-data-source
  "Creates and returns a _Buttle_ `javax.sql.XADataSource`.

  Use `setXaDatasourceSpec` to control what the _real_ (or _backing_)
  `javax.sql.XADataSource` is. You can use `String` to use an
  xa-datasource from JNDI. Use a map to create an instance and set
  properties (see `retrieve-xa-data-soure` for details)."

  []
  (let [xa-ds-spec (atom nil)
        xa-ds (atom nil)
        xa-ds! (fn [] (or @xa-ds
                          (reset! xa-ds
                                  (retrieve-xa-data-soure @xa-ds-spec))))]
    (proxy/make-proxy
     [XADataSource ButtleDataSource]
     (proxy [XADataSource ButtleDataSource] []
       (setXaDatasourceSpec [spec]
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
       (util/with-tccl (.getClassLoader (Class/forName "buttle.jdbc.XADataSource"))
         (proxy/handle the-method target-obj the-args))))))

(defn -init
  "Constructor function of `buttle.jdbc.XADataSource`."

  []
  [[] (make-xa-data-source)])

(defn -setXaDatasourceSpec
  "Implements
  `buttle.xa_data_source.ButtleDataSource/setXaDatasourceSpec`. Just
  delegates to the referenced/internal _Buttle_ datasource (see
  `-init`)."

  [this spec]
  (.setXaDatasourceSpec (.state this) spec))

(defn -getXAConnection
  "Implements `javax.sql.XADataSource/getXAConnection`. Just delegates
  to the referenced/internal datasource (see `-init`)."

  ([this]
     (.getXAConnection (.state this)))
  ([this username password]
     (.getXAConnection (.state this) username password)))

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
  
