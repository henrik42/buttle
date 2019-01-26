(ns buttle.data-source
  (:gen-class
   :init init
   :state state
   :name buttle.jdbc.DataSource
   :implements [javax.sql.DataSource])
  (:import [java.sql SQLException]
           [java.sql SQLFeatureNotSupportedException])
  (:require [buttle.proxy :as proxy]))

#_
(def ds (make-data-source))

#_
(def dsx (buttle.jdbc.DataSource.))

;; Problem: unsere Datasource ist nur ein Proxy um eine "echte"
;; DataSource, an die wir alle Methodenaufrufe deligieren
;; (Alternative: wir bieten eine DataSource, die auf den DriverManager
;; zurückgreift). Diese echte DataSource müssen wir uns aus dem JNDI
;; holen.
;;
;; Es ist aber gar nicht klar, in welcher Reihenfolge dies
;; geschieht. D.h. wir müssen davon ausgehen, dass unsere DataSource
;; erzeugt und ggf. sogar *verwendet* wird, bevor die "echte"
;; DataSource im JNDI registriert wurde. Daher müssen wir den
;; Zeitpunkt, an dem wir uns die "echte" DataSource aus dem JNDI
;; holen, so weit wie möglich nach hinten verschieben.
;;
;; Sollten wir die "echte" DataSource brauchen und finden sie aber
;; nicht im JNDI, bleibt uns nichts anderes übrig, als eine Exception
;; zu werfen.

(defn lookup-data-source []
  (proxy [javax.sql.DataSource] []
    (getConnection [& xs]
      (.println System/out
                (format "lookup-data-source : %s" xs)))))

(defn make-data-source []
  (let [ds (atom nil)
        ds! (fn []
              (or @ds
                  (reset!
                   ds
                   (lookup-data-source))))]
    (proxy/make-proxy
     javax.sql.DataSource
     (proxy [javax.sql.DataSource] []
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
