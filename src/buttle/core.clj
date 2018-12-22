(ns buttle.core
  (:require [buttle.driver :as drv])
  (:gen-class
   :name buttle.jdbc.Driver
   :implements [java.sql.Driver]))

(defn -connect [this url info]
  (drv/connect-fn url))

