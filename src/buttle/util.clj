(ns buttle.util)

(defn log [& xs]
  (let [s (apply pr-str xs)]
    (.println System/out (str "buttle: " s))
    s))

(defmacro with-tccl [cl & body]
  `(binding [*use-context-classloader* true]
     (let [tccl# (.getContextClassLoader (Thread/currentThread))]
       (try (.setContextClassLoader (Thread/currentThread) ~cl)
            ~@body
            (finally
              (.setContextClassLoader (Thread/currentThread) tccl#))))))

