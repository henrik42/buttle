(ns buttle.proxy-test
  (:require [clojure.test :refer :all]
            [buttle.util :as util]
            [buttle.proxy :as proxy]))

;; This `java.sql.Connection` proxy stands for the _real_ object that
;; we want to proxy. Clojure proxys do not incorporate a handler
;; function but uses method-body-forms. So here we define some of the
;; method-bodies that we'll use in the tests below.
(def a-connection
  (proxy [java.sql.Connection] []
    (hashCode []
      42)
    (rollback []
      (throw (RuntimeException. "connection:ROLLBACK!")))
    (close []
      (throw (RuntimeException. "connection:CLOSE!")))
    (nativeSQL [sql]
      (str "connection:" sql))))

;; This handler-function for the Buttle proxy.
(defn connection-handler [the-method target-obj [a b c :as the-args]]
  (let [meth (.getName the-method)]
    (condp = meth
      "rollback" (.invoke the-method target-obj the-args)
      "close" (throw (RuntimeException. "handler:CLOSE!"))
      "nativeSQL" (if (= \: (first a))
                    (.invoke the-method target-obj the-args)
                    (str "handler:" a)))))

;; The Buttle proxy "around" the real object. We use the var instead
;; of connection-handler just for development of the test code so that
;; we can re-define the handler without having to re-create the Buttle
;; proxy.
(def connection-proxy
  (proxy/make-proxy java.sql.Connection a-connection #'connection-handler))

(deftest connection-tests
  (is (= 42
         (.hashCode a-connection))
      ".hashCode on a-connection")
  (is (= 42
         (.hashCode connection-proxy))
      ".hashCode on connection proxy")
  (is (= "connection:foo"
         (.nativeSQL a-connection "foo"))
      ".nativeSQL on a-connection")
  (is (= "handler:bar"
         (.nativeSQL connection-proxy "bar"))
      ".nativeSQL on a-connection proxy")
  (is (= "connection::bar"
         (.nativeSQL connection-proxy ":bar"))
      ".nativeSQL with super call on a-connection proxy")
  (is (thrown-with-msg? RuntimeException #"connection:CLOSE!"
        (.close a-connection))
      "throwing from a-connection")
  (is (thrown-with-msg? RuntimeException #"handler:CLOSE!"
        (.close connection-proxy))
      "throwing from connection proxy")
  (is (thrown-with-msg? RuntimeException #"connection:ROLLBACK!"
        (.rollback connection-proxy))
      "throwing from a-connection"))

(deftest invocation-key-test
  (is (= [java.sql.Connection :buttle/close]
         (proxy/invocation-key 
          (.getMethod java.sql.Connection "close" nil)))
      "check java.sql.Connection/close invocation key"))

(deftest handle-test
  (testing "Handing Object.toString()"
    (is (= "foo"
           (proxy/handle
            (.getMethod Object "toString" nil)
            (proxy [java.sql.Connection] []
              (toString [] "foo"))
            nil))
        "check non-proxied toString()")
    (is (thrown? java.lang.reflect.InvocationTargetException
                 (proxy/handle
                  (.getMethod Object "toString" nil)
                  (proxy [java.sql.Connection] []
                    (toString []
                      (throw (RuntimeException. "oops foo"))))
                  nil))
        "`handle` does not unroll InvocationTargetException! only `make-proxy` does that!"))
  (testing "Handling Connection/getCatalog"
    (is (= "bar"
           (proxy/handle
            (.getMethod java.sql.Connection "getCatalog" nil)
            (proxy [java.sql.Connection] []
              (getCatalog [] "bar"))
            nil))
        "check proxied method getCatalog"))
  (testing "Handling createStatement"
    (is (= {:a-stmt-is-jdk-proxy false
            :stmt-is-jdk-proxy true}
           (let [a-stmt (proxy [java.sql.Statement] [])
                 stmt (proxy/handle
                       (.getMethod java.sql.Connection "createStatement" nil)
                       (proxy [java.sql.Connection] []
                         (createStatement []
                           a-stmt))
                       nil)]
             {:a-stmt-is-jdk-proxy
              (java.lang.reflect.Proxy/isProxyClass (.getClass a-stmt))
              :stmt-is-jdk-proxy
              (java.lang.reflect.Proxy/isProxyClass (.getClass stmt))}))
        "See that we get a JDK proxy stmt (around a-stmt) through handle")))





(defn remove-handle [[clss mthd]]
  (remove-method proxy/handle [clss mthd]))
  
(defmacro def-handle [[clss mthd] [the-method target-obj the-args] body]
  (list 'do
        (list 'fix-prefers! [clss mthd])
        (list 'defmethod 'buttle.proxy/handle [clss mthd] '[the-method target-obj the-args]
              body)))

(defn methods-of [clss]
  (->> clss
       .getMethods
       (map #(keyword "buttle" (.getName %)))))

(defn fix-prefers! [[clss mthd]]
  (util/log (format "(fix-prefers! %s)" [clss mthd]))
  (when (= mthd :buttle/default)
    (when (= Object clss)
      (throw (RuntimeException. "You cannot use def-handle with Object/:buttle/default")))
    (doseq [m (methods-of clss)]
      (util/log (format "(fix-prefers! %s) : derive %s :buttle/default" [clss mthd] m))
      ;; MUTATION/SIDEEFFECT!!!!
      (derive m :buttle/default))
    (doseq [m (descendants :buttle/default)]
      (util/log (format "(fix-prefers! %s) : prefer-method %s %s" [clss mthd]
                        [clss :buttle/default] [java.lang.Object m]))
      ;; MUTATION/SIDEEFFECT!!!!
      (prefer-method proxy/handle
                     [clss :buttle/default]
                     [java.lang.Object m]))))




(defn handle-connection [f]
  (proxy/handle
   (condp = f
     :getCatalog (.getMethod java.sql.Connection "getCatalog" nil)
     :getSchema (.getMethod java.sql.Connection "getSchema" nil)
     (throw (RuntimeException. (str "oops " f))))
   (proxy [java.sql.Connection] []
     (getCatalog [] "proxy getCatalog")
     (getSchema [] "proxy getSchema"))
   nil))

(defn handle-resultset [f]
  (proxy/handle
   (condp = f
     :getString (.getMethod java.sql.ResultSet "getString" (into-array [String]))
     (throw (RuntimeException. (str "oops " f))))
   (condp get f
     #{:getString} (proxy [java.sql.ResultSet] []
                     (getString [_] "proxy getString")))
   (into-array ["bar"])))

;; lein test :only buttle.proxy-test/handle-tests
(deftest handle-tests
  
  (testing "proxys"
    (is (= "proxy getSchema"
           (handle-connection :getSchema)))
    (is (= "proxy getCatalog"
           (handle-connection :getCatalog)))
    (is (= "proxy getString"
           (handle-resultset :getString))))
  
  (testing "calling function"
    
    (def-handle [Object :buttle/getCatalog] [the-method target-obj the-args]
      (str "Object/getCatalog: intercepted " (.getName the-method)))
    (def-handle [java.sql.ResultSet :buttle/getString] [the-method target-obj the-args]
      (str "ResultSet/getString: intercepted " (.getName the-method)))
    
    (is (= "Object/getCatalog: intercepted getCatalog"
           (handle-connection :getCatalog)))
    (is (= "proxy getSchema"
           (handle-connection :getSchema)))
    (is (= "ResultSet/getString: intercepted getString"
           (handle-resultset :getString)))
    
    (def-handle [java.sql.Connection :buttle/default] [the-method target-obj the-args]
      (str "Connection/default: intercepted " (.getName the-method)))
    
    (is (= "Connection/default: intercepted getCatalog"
           (handle-connection :getCatalog)))
    (is (= "Connection/default: intercepted getSchema"
           (handle-connection :getSchema)))

    (def-handle [java.sql.Connection :buttle/getCatalog] [the-method target-obj the-args]
      (str "Connection/getCatalog: intercepted " (.getName the-method)))
    
    (is (= "Connection/getCatalog: intercepted getCatalog"
           (handle-connection :getCatalog)))
    (is (= "Connection/default: intercepted getSchema"
           (handle-connection :getSchema)))
    
    (remove-handle [java.sql.Connection :buttle/default])
    
    (is (= "Connection/getCatalog: intercepted getCatalog"
           (handle-connection :getCatalog)))
    (is (= "proxy getSchema"
           (handle-connection :getSchema)))

    (remove-handle [java.sql.Connection :buttle/getCatalog])

    (is (= "Object/getCatalog: intercepted getCatalog"
           (handle-connection :getCatalog)))
    (is (= "proxy getSchema"
           (handle-connection :getSchema)))

    (remove-handle [Object :buttle/getCatalog])
    (remove-handle [java.sql.ResultSet :buttle/getString])
    
    (is (= "proxy getCatalog"
           (handle-connection :getCatalog)))
    (is (= "proxy getString"
           (handle-resultset :getString)))))
    
