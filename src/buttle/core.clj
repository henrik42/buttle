(ns buttle.core
  (:require [buttle.util :as util])
  (:gen-class
   :name buttle.jdbc.Driver
   :implements [java.sql.Driver]))

;; Register driver when loading namespace as part of the class loading
;; of buttle.jdbc.Driver. When compile/dev loads namespace do not
;; register.
#_
(if-not *compile-files*
  (register-driver))

(defn target-type [o]
  (util/log "dispatch" o)
  (cond
    (instance? java.sql.Connection o) :connection
    (instance? java.sql.Statement o) :statement
    (instance? java.sql.CallableStatement o) :callable-statement
    (instance? java.sql.PreparedStatement o) :prepared-statement
    (instance? java.sql.ResultSet o) :resultset
    :else :default))

(defn method-name [the-method conn the-args]
  (.getName the-method))

;; proxy factories per target-type
(defmulti make-proxy target-type)

(defmethod make-proxy :default [o]
  (util/log "UNKNOWN " o)
  o)

;; -----------------------------------------------------------------------------------------
;; Connection
;; -----------------------------------------------------------------------------------------

(defmulti connection-fn method-name)

(defmethod connection-fn "createStatement" [the-method conn the-args]
  (util/log "conn create-stmt " conn " meth=" the-method)
  (make-proxy (.invoke the-method conn the-args)))

(defmethod connection-fn :default [the-method conn the-args]
  #_ (util/log "conn " conn " meth=" the-method)
  #_ (make-proxy (.invoke the-method conn the-args))
  (.invoke the-method conn the-args))

(defn connection-handler [conn]
  (proxy [java.lang.reflect.InvocationHandler] []
    (invoke [the-proxy the-method the-args]
      (if (= java.sql.Connection (.getDeclaringClass the-method))
        (connection-fn the-method conn the-args)
        (.invoke the-method conn the-args)))))

(defmethod make-proxy :connection [conn]
  (java.lang.reflect.Proxy/newProxyInstance
   (.. conn getClass getClassLoader)
   (into-array [java.sql.Connection])
   (connection-handler conn)))

;; -----------------------------------------------------------------------------------------
;; Statement
;; -----------------------------------------------------------------------------------------

(defn statement-handler [stmt]
  (proxy [java.lang.reflect.InvocationHandler] []
    (invoke [the-proxy the-method the-args]
      (if (= java.sql.Statement (.getDeclaringClass the-method))
        (do 
          (util/log "statement " stmt " meth=" the-method)
          (make-proxy (.invoke the-method stmt the-args)))
        (do 
          (util/log "non-statement " stmt " meth=" the-method)
          (.invoke the-method stmt the-args))))))

(defmethod make-proxy :statement [stmt]
  (java.lang.reflect.Proxy/newProxyInstance
   (.. stmt getClass getClassLoader)
   (into-array [java.sql.Statement])
   (statement-handler stmt)))

;; -----------------------------------------------------------------------------------------
;; ResultSet
;; -----------------------------------------------------------------------------------------

(defn resultset-handler [rs]
  (proxy [java.lang.reflect.InvocationHandler] []
    (invoke [the-proxy the-method the-args]
      (if (= java.sql.ResultSet (.getDeclaringClass the-method))
        (do 
          (util/log "rs " rs " meth=" the-method)
          (.invoke the-method rs the-args)
          #_ (make-proxy (.invoke the-method rs the-args)))
        (do 
          (util/log "non-rs " rs " meth=" the-method)
          (.invoke the-method rs the-args))))))

(defmethod make-proxy :resultset [rs]
  (java.lang.reflect.Proxy/newProxyInstance
   (.. rs getClass getClassLoader)
   (into-array [java.sql.ResultSet])
   (resultset-handler rs)))

;; -----------------------------------------------------------------------------------------
;; Driver
;; -----------------------------------------------------------------------------------------

#_
(def c (java.sql.DriverManager/getConnection
        "jdbc:postgresql://127.0.0.1:5432/hhe"
        "inno" "inno"))

#_
(defn -connect [this url info]
  {:post [(do (util/log "EXIT (-connect " url " " info ") --> " %) true)]}
  (util/log "ENTER (-connect " url " " info ")")
  (if-let [{:keys [delegate-url user password]} (parse-jdbc-url url)]
    (if-let [conn (java.sql.DriverManager/getConnection delegate-url user password)]
      (make-proxy conn))))

#_
(defn -acceptsURL [this url]
  (util/log "-acceptsURL " url)
  (boolean (parse-jdbc-url url)))

(defn -getMajorVersion [this]
  (util/log "-getMajorVersion")
  42)

(defn -getMinorVersion [this]
  (util/log "-getMinorVersion")
  42)

(defn -getParentLogger
  "Return the parent Logger of all the Loggers used by this driver."

  [this]

  (util/log "(getParentLogger")
  nil)

(defn #_ DriverPropertyInfo_arr -getPropertyInfo
  "Gets information about the possible properties for this driver."
  
  [this
   #_ String url
   #_ Properties info]

  (util/log "-getPropertyInfo")
  nil)


(defn -jdbcCompliant [this]
  true)

