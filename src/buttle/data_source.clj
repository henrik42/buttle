(ns buttle.data-source
  (:gen-class
   :init init
   :state state
   :name buttle.jdbc.DataSource
   :implements [javax.sql.DataSource])
  (:import [java.sql SQLException]
           [java.sql SQLFeatureNotSupportedException])
  (:require [buttle.proxy :as proxy]))

(defn make-data-source []
  (proxy/make-proxy
   javax.sql.DataSource
   (proxy [javax.sql.DataSource] [])
   proxy/handle))

(defn -init []
  (let [ds (make-data-source)]
    [[] ds]))

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

(defn -getLoginTimeout [this ]
  (.getLoginTimeout (.state this)))

(defn -getParentLogger [this ]
  (.getParentLogger (.state this)))
  
(defn -unwrap [this ifc]
  (.unwrap (.state this) ifc))

(defn -isWrapperFor [this ifc]
  (.isWrapperFor (.state this) ifc))
