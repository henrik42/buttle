(defproject buttle "0.1.0-SNAPSHOT"
  
  :description "Buttle is a proxying JDBC driver with hooks."
  
  :url "https://github.com/henrik42/buttle/"
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.490"]]

  ;; Produce the "named classes" that you'll be using
  :aot [buttle.driver buttle.data-source]

  ;; we need buttle.SetContextClassLoaderInStaticInitializer as a base
  ;; class for buttle.jdbc.Driver/driver.clj - so we'll compile Java
  ;; classes first and then Clojure files
  :java-source-paths ["java"]
  
  :plugins [[lein-swank "1.4.5"]
            [lein-codox "0.10.3"]
            [lein-marginalia "0.9.1"]]

  :aliases {;; uberjar will contain clojure RT!!
            "make-uberjar" ["do" "clean," "uberjar"]

            ;; make documenation which is kept in git repo
            "make-doc" ["with-profile" "+make-doc" "do"
                        ["clean"]
                        ["codox"]
                        ["marg"
                         "-d" "resources/public/generated-doc/"
                         "-f" "buttle-source.html"
                         "src"]]}

  :profiles {;; Build documentation from source. I check this into git
             ;; repo. Maybe thats not the best solution but this way I
             ;; see which things changed. There probably is a better
             ;; way.
             :make-doc {:codox {:metadata {:doc/format :markdown}
                                :themes [:rdash]
                                :doc-files ["README.md"]
                                :output-path "resources/public/generated-doc/"}
                        :dependencies [[codox-theme-rdash "0.1.2"]]
                        :clean-targets ^{:protect false} ["resources/public/generated-doc"]}

             ;; If you want to deliver the Open Tracing und Jaeger
             ;; things with the UBERJAR you can do:
             ;;
             ;; lein with-profile +jaeger make-uberjar
             ;;
             ;; Note that if you're targeting an application server
             ;; you will probably have to use +wildfly profile as
             ;; well.
             :jaeger {:dependencies [[opentracing-clj "0.1.2"]
                                     [io.jaegertracing/jaeger-client "0.33.1"]]}

             ;; when building for Wildfly we must not include some
             ;; packages since these interfer with the classes
             ;; supplied by Wildfly.
             ;;
             ;; e.g. lein with-profile jaeger,wildfly make-uberjar
             :wildfly {:uberjar-exclusions [#"org/apache/commons/"
                                            #"org/apache/http/"
                                            #"javax/"]}

             ;; Just for dev
             :swank {:dependencies [[swank-clojure/swank-clojure "1.4.3"]
                                    [org.clojure/tools.nrepl "0.2.12"]]}

             :test {:dependencies [[org.postgresql/postgresql "9.4.1212"]
                                   [opentracing-clj "0.1.2"]
                                   
                                   ;; https://github.com/h-thurow/Simple-JNDI/tree/master
                                   ;; https://github.com/hen/osjava/tree/master/simple-jndi
                                   [com.github.h-thurow/simple-jndi "0.17.2"]
                                   ;;[simple-jndi "0.11.4.1"]
                                   
                                   [io.jaegertracing/jaeger-client "0.33.1"]
                                   [org.slf4j/slf4j-jdk14 "1.7.25"]]}})
