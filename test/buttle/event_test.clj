(ns buttle.event-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a]
            [buttle.proxy :as proxy]
            [buttle.util :as util]
            [buttle.event :as event]))

(deftest produce-and-consume-event-test
  (let [p (promise)
        c (let [ch (a/chan)]
            (a/tap event/event-mult ch)
            (a/go
             (when-let [e (a/<! ch)]
               (deliver p e))
             (a/close! ch)
             (a/untap event/event-mult ch))
            ch)]
    (event/send-event {:foo :bar})
    (is (= {:foo :bar} @p))))

;; ----------------------------------------------------------------------------------

(defn map-map [m mappings]
  (into {}
        (map
         (fn [[k v]]
           [k (or (mappings k) v)])
         m)))

(deftest map-map-test
  (is (= {:foo "FOO" :bar "BAR" :fred 3}
         (map-map {:foo 1 :bar 2 :fred 3} {:foo "FOO" :bar "BAR" :qux "QUX"}))))

(defn type-of
  [x]
  ;;{:post [(or (.println System/out (format "(type-of %s) --> %s" x %)) true)]}
  (cond
    (vector? x) :vector
    (map? x) :map
    (keyword? x) :echo
    :else :else))

(defmulti project-event #'type-of)

(defmethod project-event :default [x]
  (throw (RuntimeException. (format "Unknown event : %s" (pr-str x)))))

(defmethod project-event :echo [x]
  x)

(defmethod project-event :vector [x]
  (into [] (map project-event x)))

(defmethod project-event :map [x]
  ;;{:post [(or (.println System/out (format "(project-event :map %s) --> %s" x %)) true)]}
  (condp = (:type x)
    nil x
    :invoke (map-map x {:thread "THREAD" :ts "TS"})
    :return (map-map x {:ts "TS" :dur-msec "DUR-MSEC" :invoke "INVOKE"})
    :throw x))

(deftest project-event-test
  (let [i-evt {:type :invoke :ts 1}]
    (is (= {:type :invoke :ts "TS"}
           (project-event i-evt)))))

(deftest call-method-events
  (let [events (atom [])
        p (promise)
        c (let [ch (a/chan)]
            (a/tap event/event-mult ch)
            (a/go
             (loop []
               (when-let [e (a/<! ch)]
                 (swap! events conj e)
                 (when (= :done e)
                   (deliver p e))
                 (recur))))
            ch)]
    (proxy/handle
     (.getMethod java.sql.Connection "getCatalog" nil)
     (proxy [java.sql.Connection] []
       (getCatalog []
         "bar"))
     nil)
    (event/send-event :done)
    @p
    (a/close! c)
    ;; (.println System/out (project-event @events))
    (is (= [{:type :invoke, :invoke :java.sql.Connection/getCatalog, :args [], :thread "THREAD", :ts "TS"}
            {:type :return, :invoke "INVOKE", :return "bar", :ts "TS", :dur-msec "DUR-MSEC"}
            :done]
           (project-event @events)))))
