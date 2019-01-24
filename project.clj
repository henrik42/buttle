(defproject buttle "0.1.0-SNAPSHOT"
  
  :description "Buttle is a proxying JDBC driver which wraps real JDBC drivers."
  
  :url "https://github.com/henrik42/buttle/"
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.490"]]
  
  :plugins [[lein-swank "1.4.5"]
            [lein-codox "0.10.3"]
            [lein-marginalia "0.9.1"]]
  
  :aliases {"make-doc" ["with-profile" "+make-doc" "do"
                        ["clean"]
                        ["codox"]
                        ["marg"
                         "-d" "resources/public/generated-doc/"
                         "-f" "buttle-source.html"
                         "src"]]}
  
  :profiles {:make-doc {:codox {:metadata {:doc/format :markdown}
                                :themes [:rdash]
                                :doc-files ["README.md" "doc/intro_de.md"]
                                :output-path "resources/public/generated-doc/"}
                        :dependencies [[codox-theme-rdash "0.1.2"]]
                        :clean-targets ^{:protect false} ["resources/public/generated-doc"]}

             :jaeger {:dependencies [[opentracing-clj "0.1.2"]
                                     [io.jaegertracing/jaeger-client "0.33.1"]]}

             :swank {:dependencies [[swank-clojure/swank-clojure "1.4.3"]
                                    [org.clojure/tools.nrepl "0.2.12"]]}

             :test {:dependencies [[org.postgresql/postgresql "9.4.1212"]
                                   [opentracing-clj "0.1.2"]
                                   [io.jaegertracing/jaeger-client "0.33.1"]
                                   [org.slf4j/slf4j-jdk14 "1.7.25"]]}}

  :aot [buttle.driver buttle.data-source])
