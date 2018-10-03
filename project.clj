(defproject buttle "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :resource-paths ["resources/postgresql-9.4-1201-jdbc41.jar"]
  :plugins [[lein-swank "1.4.5"]]
  :__swank {:target "swank-taget"
          :resource ["target"]}
  :aot [buttle.core])
