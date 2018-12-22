(ns buttle.connection
  (:require [buttle.proxy :as proxy]))

(defn invocation-key [the-method _target-obj _the-args]
  [(-> the-method .getDeclaringClass)
   (->> the-method .getName (keyword "buttle"))])

(defmulti handle #'invocation-key)

(defmethod handle :default [the-method target-obj the-args]
  (let [r (.invoke the-method target-obj the-args)
        rt (and r (#{java.sql.Statement
                     java.sql.PreparedStatement
                     java.sql.CallableStatement
                     java.sql.Savepoint
                     java.sql.Clob
                     java.sql.Blob
                     java.sql.NClob
                     java.sql.SQLXML}
                   (.getReturnType the-method)))]
    (if rt
      (proxy/make-proxy rt r handle)
      r)))
