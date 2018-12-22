(defproject buttle "0.1.0-SNAPSHOT"
  :description "_Buttle_ is a proxying JDBC driver which wraps JDBC drivers."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:test {:dependencies [[org.postgresql/postgresql "9.4-1206-jdbc41"]]}}
  :plugins [[lein-swank "1.4.5"]]
  :aot [buttle.core])
