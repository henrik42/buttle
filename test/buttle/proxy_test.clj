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





;; **************** isa Hierarchie herstellen 
#_
(isa? :buttle/getCatalog :buttle/default)

#_
(isa? java.sql.Connection Object)

#_ ;; Beziehung zwischen Methode und default herstellen
(do 
  (derive :buttle/getCatalog :buttle/default)
  (derive :buttle/getSchema :buttle/default))

;; **************** handle Implementationen installieren

#_ ;; konkret Connection/getCatalog
(defmethod proxy/handle [java.sql.Connection :buttle/getCatalog] [the-method target-obj the-args]
  (str "Connection/getCatalog: intercepted " (.getName the-method)))

#_ ;; konkret Connection/getSchema
(defmethod proxy/handle [java.sql.Connection :buttle/getSchema] [the-method target-obj the-args]
  (str "Connection/getSchema: intercepted " (.getName the-method)))

#_ ;; generisch alle Methoden der Klasse Connection/*
(defmethod proxy/handle [java.sql.Connection :buttle/default] [the-method target-obj the-args]
  (str "Connection/default: intercepted " (.getName the-method)))

#_ ;; generisch alle Klassen für eine Method */getCatalog
(defmethod proxy/handle [Object :buttle/getCatalog] [the-method target-obj the-args]
  (str "Object/getCatalog: intercepted " (.getName the-method)))

#_ ;; generisch */* überschreibt :default handle
(defmethod proxy/handle [Object :buttle/default] [the-method target-obj the-args]
  (str "Object/default: intercepted " (.getName the-method)))

#_
(remove-method proxy/handle [java.sql.Connection :buttle/getCatalog])



#_
(defmacro defhandle [[clss mthd] [the-method target-obj the-args] body]
  (list 'do
        (list 'fix-hierarchy [clss mthd])
        (list 'defmethod 'buttle.proxy/handle [clss mthd] '[the-method target-obj the-args]
              body)))

#_
(defn fix-hierarchy [[clss mthd]]
  (when-not (= mthd :buttle/default)
    (util/log "-->" (list 'derive mthd :buttle/default))
    (derive mthd :buttle/default))
  (prefer-method buttle.proxy/handle
                 [class :buttle/default]
                 [java.lang.Object mthd]))
  
#_
(defhandle [Object :buttle/default] [the-method target-obj the-args]
  (str the-args))



;; Für konkrete Methoden derive
;; (derive :buttle/getCatalog :buttle/default)
;; (prefer-method proxy/handle
;;                [java.sql.Connection :buttle/default]
;;                [java.lang.Object :buttle/getCatalog])
;; 
#_
(defmacro defhandle [[clss mthd] [the-method target-obj the-args] body]
  body)

#_
(macroexpand-1
 '(defhandle [Object :buttle/default] [the-method target-obj the-args]
    (str the-args)))

;; Multiple methods in multimethod 'handle' match dispatch value:
;; [java.sql.Connection :buttle/getCatalog] ->
;; [java.lang.Object :buttle/getCatalog] and
;; [java.sql.Connection :buttle/default], and neither is preferred

;; Sobald man beide Dispatch Values hat, muss man einen der beiden
;; bevorzugen.
;;
;; Ansatz
;; ------
;;
;; Immer, wenn :buttle/default verwendet wird, wird das prefer
;; ausgeführt, und immer wenn java.lang.Object verwendet wird, wird
;; auch das prefer ausgeführt. Man hat hier ein Reihenfolgeproblem!
;; Deswegen muss man beide Fälle berücksichtigen.
;;
;; Man braucht also sowas wie
;; (defhandle [Object :buttle/default] [the-method target-obj the-args]
;;  (str "Object/default: intercepted " (.getName the-method)))
;;
;; Das Macro richtet dann die prefer Beziehung ein.
;; (defhandle Object [the-method target-obj the-args]
;;  (str "Object/default: intercepted " (.getName the-method)))
;; (defhandle java.sql.Connection [the-method target-obj the-args]
;;  (str "Object/default: intercepted " (.getName the-method)))

#_ 
(prefer-method proxy/handle
               [java.sql.Connection :buttle/default]
               [java.lang.Object :buttle/getCatalog])






;; ---------------------------------------------------------

(defn remove-handle [[clss mthd]]
  (remove-method proxy/handle [clss mthd]))
  
(defmacro def-handle [[clss mthd] [the-method target-obj the-args] body]
  (list 'do
        (list 'fix-prefers! [clss mthd])
        (list 'defmethod 'buttle.proxy/handle [clss mthd] '[the-method target-obj the-args]
              body)))

#_
(some-> (prefers proxy/handle)
        (get [Object :buttle/default])
        (get [java.lang.Object :buttle/default]))


;; Aber es muss doch genau umgekehrt sein!!
#_
(derive :buttle/default :buttle/getSchema)

#_ ;; -> exception
(derive :buttle/default :buttle/default)

#_ ;; so ist es richtig
(derive :buttle/getSchema :buttle/default)

#_ ;; --> true
(isa? :buttle/default :buttle/foo)

#_
(isa? :buttle/getSchema :buttle/default)







;; Fall 1: defhandle [java.sql.Connection :buttle/default]
;;
;; Achtung: man kennt aber die descendants!!! Und über die kann man
;; schleifen, um die prefers zu erstellen!!!!
;; (prefer [java.sql.Connection :buttle/default] [Object desc])
;; Damit bevorzugen wir [java.sql.Connection :buttle/default]
;;
;; Es erfolgt der Call mit [java.sql.Connection :buttle/getSchema].
;;
;; Fall 2: defhandle [java.lang.Object :buttle/getCatalog] erst
;; derive! und dann Schleife über alle prefer-keys und
;; schleife/rekursiv über die clss :default

#_
(prefer-method proxy/handle
               [java.sql.Connection :buttle/default]
               [java.lang.Object :buttle/getCatalog])

#_
(prefer-method proxy/handle
               [java.sql.Connection :buttle/default]
               [java.lang.Object :buttle/getSchema])

#_ ;; {[java.sql.Connection :buttle/default]
   ;;  #{[java.lang.Object :buttle/getSchema] [java.lang.Object :buttle/getCatalog]}}
(prefers proxy/handle)

#_
(prefers-for [java.sql.Connection :buttle/default] )

#_
(defn prefers-for [])






;; Ist nicht threadsafe
#_
(defn fix-hierarchy! [[clss mthd]]
  (util/log "fix-hierarchy! -->" [clss mthd])
  (when-not (= mthd :buttle/default)
    ;; make mthd a :buttle/default, so that :buttle/default methods
    ;; will match calls to mthd
    (derive mthd :buttle/default))
  (when-not
      (some-> (prefers proxy/handle)
              (get [Object :buttle/default])
              (get [java.lang.Object :buttle/default]))
    (prefer-method proxy/handle
                   [clss :buttle/default]
                   [java.lang.Object mthd])))




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
      (derive m :buttle/default))
    (doseq [m (descendants :buttle/default)]
      (util/log (format "(fix-prefers! %s) : prefer-method %s %s" [clss mthd]
                        [clss :buttle/default] [java.lang.Object m]))
      ;; MUTATION/SIDEEFFECT!!!!
      (prefer-method proxy/handle
                     [clss :buttle/default]
                     [java.lang.Object m]))))

#_ ;; konkret Connection/getCatalog
(def-handle [java.sql.Connection :buttle/getCatalog] [the-method target-obj the-args]
  (str "Connection/getCatalog: intercepted " (.getName the-method)))

#_ ;; konkret Connection/getSchema
(def-handle [java.sql.Connection :buttle/getSchema] [the-method target-obj the-args]
  (str "Connection/getSchema: intercepted " (.getName the-method)))

#_ ;; generisch alle Methoden der Klasse Connection/*
(def-handle [java.sql.Connection :buttle/default] [the-method target-obj the-args]
  (str "Connection/default: intercepted " (.getName the-method)))

#_ 
(def-handle [java.sql.ResultSet :buttle/default] [the-method target-obj the-args]
  (str "ResultSet/default: intercepted " (.getName the-method)))

#_ ;; generisch alle Klassen für eine Method */getCatalog
(def-handle [Object :buttle/getCatalog] [the-method target-obj the-args]
  (str "Object/getCatalog: intercepted " (.getName the-method)))

#_ ;; generisch alle Klassen für eine Method */getCatalog
(def-handle [Object :buttle/getString] [the-method target-obj the-args]
  (str "Object/getString: intercepted " (.getName the-method)))

;; Preference conflict in multimethod 'handle':
;; [java.lang.Object :buttle/default] is already preferred to [java.lang.Object :buttle/default]

;; KAPUTT: geht nicht, weil wir nicht ermitteln können, dass ein
;; getSchema auch ein :buttle/default ist.
#_ ;; generisch */* überschreibt :default handle
(def-handle [Object :buttle/default] [the-method target-obj the-args]
  (str "Object/default: intercepted " (.getName the-method)))

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
    
    (is (= "proxy getCatalog"
           (handle-connection :getCatalog)))

    ))
    
