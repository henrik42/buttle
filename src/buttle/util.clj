(ns buttle.util
  "Just some helpers."
  (:import [javax.naming InitialContext]))

(defn log [& xs]
  (let [s (apply pr-str xs)]
    (.println System/out (str "buttle: " s))
    s))

(defmacro with-tccl
  "Thread-locally binds `clojure.core/*use-context-classloader*` to
  `true`, sets the current thread's context classloader to `cl` and
  executes `body` within that context. On completion restores the
  context classloader and pops
  `clojure.core/*use-context-classloader*` to the previous
  value. Returns value of `body` evaluation."

  [cl & body]
  `(binding [*use-context-classloader* true]
     (let [tccl# (.getContextClassLoader (Thread/currentThread))]
       (try (.setContextClassLoader (Thread/currentThread) ~cl)
            ~@body
            (finally
              (.setContextClassLoader (Thread/currentThread) tccl#))))))

(defn jndi-lookup
  "Looks up entry in JNDI and returns it. Throws if entry cannot be
  found."
  
  [jndi]
  (when-not jndi
    (throw (RuntimeException. "No `jndi` property set.")))
  (try 
    (with-open [ctx (InitialContext.)]
      (.lookup ctx jndi))
    (catch Throwable t
      (throw
       (RuntimeException.
        (format "JNDI lookup failed for '%s': %s" jndi t) t)))))
              

