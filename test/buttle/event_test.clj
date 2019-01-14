(ns buttle.event-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a]
            [buttle.proxy :as proxy]
            [buttle.event :as event]))

;; ----------------------------------------------------------------------------------
;; For all the tests I use a promise to synchronize the
;; event-receiving part with the test-driving (send event and compare
;; values) part.
;;
;; This test is most simple. Just tap a channel onto
;; `event/event-mult`, go-consume one value from that channel and
;; deliver it to the test-driving thread. Close and untap (which we do
;; not really need here). The test-driver just sends an event and
;; checks that it receives it through the promise.
;; ----------------------------------------------------------------------------------

(deftest produce-and-consume-event-test
  (let [p (promise)]
    (let [ch (a/chan)]
      (a/tap event/event-mult ch)
      (a/go
       (when-let [e (a/<! ch)]
         (deliver p e))
       (a/close! ch)
       (a/untap event/event-mult ch)))
    (event/send-event {:foo :bar})
    (is (= {:foo :bar} @p))))

;; ----------------------------------------------------------------------------------
;; Functions for projecting events onto event-like-maps with some
;; dynamic values replaced by given substitutes. Tests are then run
;; against the less-detailed event-maps since we cannot know some of
;; the values (like timestamps and duration).
;; ----------------------------------------------------------------------------------

(defn map-map [m mappings]
  (into {} (map
            (fn [[k v]]
              [k (or (mappings k) v)])
            m)))

(deftest map-map-test
  (is (= {:foo "FOO" :bar "BAR" :fred 3}
         (map-map {:foo 1 :bar 2 :fred 3} {:foo "FOO" :bar "BAR" :qux "QUX"}))))

(defn type-of [x]
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
    :invoke (map-map x {:ts "TS" :thread "THREAD"})
    :return (map-map x {:ts "TS" :dur-msec "DUR-MSEC" :invoke "INVOKE"})
    :throw (map-map x  {:ts "TS" :dur-msec "DUR-MSEC" :invoke "INVOKE" :throw "THROW"})))

(deftest project-event-test
  (let [i-evt {:type :invoke :ts 1}]
    (is (= {:type :invoke :ts "TS"}
           (project-event i-evt)))))

(deftest event-factory-test
  (let [i-evt (proxy/->invoke-event (.getMethod java.sql.Connection "getCatalog" nil) "foo" nil)
        r-evt (proxy/->return-event i-evt "foo")
        t-evt (proxy/->throw-event i-evt (RuntimeException. "oops"))]
    (is (= (project-event i-evt)
           {:type :invoke,
            :invoke :java.sql.Connection/getCatalog,
            :args [],
            :thread "THREAD"
            :ts "TS"}))
    (is (= (project-event r-evt)
           {:type :return,
            :invoke "INVOKE",
            :return "foo",
            :ts "TS",
            :dur-msec "DUR-MSEC"}))
    (is (= (project-event t-evt)
           {:type :throw,
            :invoke "INVOKE",
            :throw "THROW",
            :ts "TS",
            :dur-msec "DUR-MSEC"}))))

;; ----------------------------------------------------------------------------------
;; Helper function that pulls events out of event/event-mult and
;; conjoins them to bag ref until :done event. Returns the channel.
;; ----------------------------------------------------------------------------------

(defn consume-until-done [prms bag]
  (let [ch (a/chan)]
    (a/tap event/event-mult ch)
    (a/go
     (loop []
       (when-let [e (a/<! ch)]
         (swap! bag conj e)
         (when (= :done e)
           (deliver prms e))
         (recur))))
    ch))
    
(deftest invoke-return-test
  (let [bag (atom [])
        p (promise)
        ch (consume-until-done p bag)]
    (proxy/handle
     (.getMethod java.sql.Connection "getCatalog" nil)
     (proxy [java.sql.Connection] []
       (getCatalog []
         "bar"))
     nil)
    (event/send-event :done)
    @p
    (a/close! ch)
    (is (= [{:type :invoke, :invoke :java.sql.Connection/getCatalog, :args [], :thread "THREAD", :ts "TS"}
            {:type :return, :invoke "INVOKE", :return "bar", :ts "TS", :dur-msec "DUR-MSEC"}
            :done]
           (project-event @bag)))))

(deftest invoke-throw-test
  (let [bag (atom [])
        p (promise)
        ch (consume-until-done p bag)]
    (try 
      (proxy/handle
       (.getMethod java.sql.Connection "getCatalog" nil)
       (proxy [java.sql.Connection] []
         (getCatalog []
           (throw (RuntimeException. "oops"))))
       nil)
      (catch Throwable t))
    (event/send-event :done)
    @p
    (a/close! ch)
    (is (= [{:type :invoke, :invoke :java.sql.Connection/getCatalog, :args [], :thread "THREAD", :ts "TS"}
            {:type :throw, :throw "THROW" :invoke "INVOKE", :ts "TS", :dur-msec "DUR-MSEC"}
            :done]
           (project-event @bag)))))

