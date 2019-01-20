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

(def jaeger-tracer
  (build-jaeger-tracer
   (System/getProperty "buttle_jaeger_agent_host")
   "buttle-connection-trace"))

(defn invoke-with-tracing [the-method target-obj the-args]
  (binding [tracing/*tracer* jaeger-tracer]
    (tracing/with-span [s {:name (format "method:%s" the-method)}]
      (let [r (try
                (proxy/handle-default the-method target-obj the-args)
                (catch Throwable t
                  (do
                    (throw t))))]
        r))))

;; trace all/any class/method
(defmethod proxy/handle :default [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

#_
(proxy/def-handle [java.sql.Connection :buttle/default] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))

#_
(proxy/def-handle [java.sql.Driver :buttle/default] [the-method target-obj the-args]
  (invoke-with-tracing the-method target-obj the-args))
