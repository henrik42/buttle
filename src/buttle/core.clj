(ns buttle.core
  (:gen-class
   :name buttle.jdbc.Driver
   :implements [java.sql.Driver]))

;; -----------------------------------------------------------------------------------------
;;
;; -----------------------------------------------------------------------------------------
(defn log [& xs]
  (.println System/out (apply pr-str xs)))

(defn register-driver []
  (let [dn "buttle.jdbc.Driver"]
    (try 
      (doto (.. (Class/forName dn) newInstance)
        (java.sql.DriverManager/registerDriver))
      (catch Throwable t
        (log "buttle.core: DriverManager/registerDriver '" dn "' failed: " t)
        (throw (RuntimeException. (str "DriverManager/registerDriver '" dn "' failed: " t)
                                  t))))))

;; Register driver when loading namespace as part of the class loading
;; of buttle.jdbc.Driver. When compile/dev loads namespace do not
;; register.
(if-not *compile-files*
  (register-driver))

;; url = jdbc:subprotocol:subname
;; jdbc:buttle:{:delegate-url "foo:bar"}
;; org/postgresql/Driver
;; "jdbc:postgresql://127.0.0.1:5432/hhe" "inno" "inno"
#_
(parse-jdbc-url "jdbc:buttle:{:delegate-url \"foo:bar\"}")

#_
(parse-jdbc-url "jdbc:buttle:egate-url \"foo:bar\"}")

(defn parse-jdbc-url [url]
  (try 
    (some-> (re-matches #"jdbc:buttle:(.+)" url)
            second
            read-string
            eval)
    (catch Throwable t
      (throw (ex-info "Could not parse url" {:url url} t)))))

(defn target-type [o]
  (log "dispatch" o)
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
  (log "UNKNOWN " o)
  o)

;; -----------------------------------------------------------------------------------------
;; Connection
;; -----------------------------------------------------------------------------------------

(defmulti connection-fn method-name)

(defmethod connection-fn "createStatement" [the-method conn the-args]
  (log "conn create-stmt " conn " meth=" the-method)
  (make-proxy (.invoke the-method conn the-args)))

(defmethod connection-fn :default [the-method conn the-args]
  #_ (log "conn " conn " meth=" the-method)
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
          (log "statement " stmt " meth=" the-method)
          (make-proxy (.invoke the-method stmt the-args)))
        (do 
          (log "non-statement " stmt " meth=" the-method)
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
          (log "rs " rs " meth=" the-method)
          (.invoke the-method rs the-args)
          #_ (make-proxy (.invoke the-method rs the-args)))
        (do 
          (log "non-rs " rs " meth=" the-method)
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

(defn -connect [this url info]
  {:post [(do (log "EXIT (-connect " url " " info ") --> " %) true)]}
  (log "ENTER (-connect " url " " info ")")
  (if-let [{:keys [delegate-url user password]} (parse-jdbc-url url)]
    (if-let [conn (java.sql.DriverManager/getConnection delegate-url user password)]
      (make-proxy conn))))

(defn -acceptsURL [this url]
  (log "-acceptsURL " url)
  (boolean (parse-jdbc-url url)))

(defn -getMajorVersion [this]
  (log "-getMajorVersion")
  42)

(defn -getMinorVersion [this]
  (log "-getMinorVersion")
  42)

(defn -getParentLogger
  "Return the parent Logger of all the Loggers used by this driver."

  [this]

  (log "(getParentLogger")
  nil)

(defn #_ DriverPropertyInfo_arr -getPropertyInfo
  "Gets information about the possible properties for this driver."
  
  [this
   #_ String url
   #_ Properties info]

  (log "-getPropertyInfo")
  nil)


(defn -jdbcCompliant [this]
  true)

