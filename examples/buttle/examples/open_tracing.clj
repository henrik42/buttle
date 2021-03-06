(ns buttle.examples.open-tracing
  (:require [buttle.proxy :as proxy]
            [opentracing-clj.core :as tracing])
  (:import [io.jaegertracing.spi Reporter]
           [io.jaegertracing.internal.reporters LoggingReporter CompositeReporter RemoteReporter$Builder]
           [io.jaegertracing.internal.samplers ConstSampler]
           [io.jaegertracing.thrift.internal.senders UdpSender]
           [io.jaegertracing.internal JaegerTracer$Builder]))

;; `root-span-name` will be presented as `Service` in the Jaeger UI.
(defn build-jaeger-tracer [host root-span-name]
  (let [sender (UdpSender. host ;; note: UdpSender connects to host/port during construction!
                           UdpSender/DEFAULT_AGENT_UDP_COMPACT_PORT ;; default port is 6831
                           0) ;; 0 means use ThriftUdpTransport#MAX_PACKET_SIZE
        remote-reporter (-> (doto (RemoteReporter$Builder.)
                              (.withSender sender))
                            .build)
        ;; reporter sends and logs so when see what's going on
        logging-remote-reporter (CompositeReporter. 
                                 (into-array Reporter [(LoggingReporter.) remote-reporter]))
        ;; sample (i.e. keep and report) *every* span
        sampler (ConstSampler. true)]
    (-> (doto (JaegerTracer$Builder. root-span-name)
          (.withSampler sampler)
          (.withReporter logging-remote-reporter))
        .build)))

;; at the moment we're creating/using just one global Jaeger tracer
(def jaeger-tracer
  (build-jaeger-tracer
   (System/getProperty "buttle_jaeger_agent_host")
   
   ;; this will be presented as "Service" in the Jaeger GUI -- Service
   ;; is the top-most (and mandatory -- no "all" option available)
   ;; qualification when searching for data/spans in the Jager GUI
   "buttle-trace"))

;; this will be presented as "Operation" in the Jaeger GUI --
;; Operation is a secondary (and optional -- "all" option available)
;; qualification when searching for data/spans in the Jager GUI
(defn method->operation [m]
  (format "%s/%s"
          (-> (.getDeclaringClass m)
              .getName)
          (.getName m)))

;; tags for all spans - search in Jaeger GUI with for example
;; `class-name=ResultSet method-name=wasNull`
(defn invocation->tags [the-method target-obj the-args]
  {:method-name (.getName the-method)
   :method (format "%s/%s"
                   (-> (.getDeclaringClass the-method)
                       .getName)
                   (.getName the-method))
   :class-name (-> (.getDeclaringClass the-method)
                   .getSimpleName)
   :class (-> (.getDeclaringClass the-method)
                   .getName)
   :args (into [] the-args)
   ;; our app may have different _layers_ (like database,
   ;; business-logic) that we want to be able to do a search on.
   :layer "database"})

(defn throwable->tags [t]
  (loop [t t]
    (if-not (instance? java.lang.reflect.InvocationTargetException t)
      {:type 'throw
       :throw (pr-str t)}
      (recur (.getTargetException t)))))

(defn result->tags [r]
  {:type 'return
   :return (pr-str r)})

(defn parse->vector [s]
  (try
    (read-string s)
    (catch Throwable t
      [(format "not a vector: '%s'" s)])))

(defn invoke-with-tracing [the-method target-obj the-args]
  (binding [tracing/*tracer* jaeger-tracer]
    (let [parent-path (parse->vector
                       (or (some-> (.activeSpan tracing/*tracer*)
                                   (.getBaggageItem "path"))
                           "[]"))]
      (tracing/with-span [s {:name (method->operation the-method)}]
        (tracing/set-tags (invocation->tags the-method target-obj the-args))
        (tracing/set-baggage-item "path" (pr-str (conj parent-path (str s))))
        (let [r (try
                  (proxy/handle-default the-method target-obj the-args)
                  (catch Throwable t
                    (do
                      (tracing/set-tags (throwable->tags t))
                      (throw t))))]
          (tracing/set-tags (result->tags r))
          r)))))

#_ ;; trace all/any class/method
(defmethod proxy/handle :default [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

(proxy/def-handle [java.sql.Driver :buttle/default] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

(proxy/def-handle [java.sql.Connection :buttle/default] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

(proxy/def-handle [java.sql.Statement :buttle/default] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

;; Note that there are two interfaces that javax.sql.DataSource
;; extends (javax.sql.CommonDataSource, java.sql.Wrapper), so we have
;; to name them

(proxy/def-handle [javax.sql.DataSource :buttle/default] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

;; Note: getConnection is defined in CommonDataSource *and* Wrapper!
;; For some reason we get the following exception when calling
;; javax.sql.DataSource/getConnection:
;;
;; Multiple methods in multimethod 'handle' match dispatch value:
;; [javax.sql.DataSource :buttle/getConnection] ->
;; [javax.sql.CommonDataSource :buttle/default]
;; [java.sql.Wrapper :buttle/default], 
;;
;; There must be a bug in buttle.proxy. For now we workaround this by
;; def-handling this invocation key explicitly.
(proxy/def-handle [javax.sql.DataSource :buttle/getConnection] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

(proxy/def-handle [javax.sql.CommonDataSource :buttle/default] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

(proxy/def-handle [java.sql.Wrapper :buttle/default] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))
