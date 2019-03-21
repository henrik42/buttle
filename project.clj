;; re-enables http repository support in Leiningen 2.8
(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))

(defproject buttle/buttle "0.1.0-SNAPSHOT"

  :description "Buttle is a proxying JDBC driver with hooks."
  
  :url "https://github.com/henrik42/buttle/"
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.490"]]

  ;; Produce the "named classes" buttle.jdbc.Driver and
  ;; buttle.jdbc.DataSource that you'll be using
  :aot [buttle.driver
        buttle.data-source
        buttle.xa-data-source
        buttle.connection-pool-data-source]

  ;; we need buttle.SetContextClassLoaderInStaticInitializer as a base
  ;; class for buttle.jdbc.Driver/driver.clj - so we'll compile Java
  ;; classes first and then Clojure files
  :java-source-paths ["java"]
  
  :target-path "target/%s"
  
  :plugins [[lein-swank "1.4.5"]
            [lein-codox "0.10.3"]
            [lein-marginalia "0.9.1"]
            [camechis/deploy-uberjar "0.3.0"]]

  ;; Releasing _Buttle_
  :release-tasks [["vcs" "assert-committed"]

                  ;; Before trying to release we could/should test.
                  #_ ["test"]

                  ;; Bump SNAPSHOT to release version and tag.
                  ;;
                  ;; NOTE: Committing a non-SNAPSHOT version to master
                  ;; can be dangerous! If your release fails half-way
                  ;; you'll have a release version in master which may
                  ;; go unnoticed for while. You'll be building
                  ;; (compile, install, deploy) a **RELEASE** over and
                  ;; over again producing unrepeatable builds (because
                  ;; your local repo will _accept_ the new release but
                  ;; your remote Nexus hopefully will not.....but
                  ;; may..)
                  ;;
                  ;; So this should be changed to:
                  ;;
                  ;; * tag SNAPSHOT-revision on master with id
                  ;;   `master-for-<release-version>`.  This will hepl
                  ;;   you to see where your master was released.
                  ;;
                  ;; * branch & switch to revision id
                  ;;   `master-<release-version>` with id
                  ;;   `branch-<release-version>`.
                  ;;
                  ;; * bump version to <release-version>, commit and
                  ;;   tag `release-<release-version>`
                  ;;
                  ;; * Build/deploy release
                  ;; * Switch back to master
                  ;; * Bump to next SNAPSHOT
                  ;; * Build/deploy new SNAPSHOT
                  ;; 
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]

                  ;; Before releasing we could/should test. 
                  #_ ["test"]

                  ;; --------- Build & deploy RELEASE ---------
                  ["uberjar"]
                  ["deploy-uberjar" "releases"]

                  ;; --------- Bump version to next SNAPSHOT ---------
                  ;;
                  ;; NOTE: this could be a change from
                  ;; release-version->next-SNAPSHOT-version or
                  ;; SNAPSHOT-version->next-SNAPSHOT-version -
                  ;; depending on whether you release on master or on
                  ;; release-branch (see above).
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]

                  ;; --------- Build & deploy new SNAPSHOT ---------
                  #_ ["uberjar"]
                  #_ ["deploy-uberjar" "snapshots"]
                  ]

  :aliases {;; uberjar will contain clojure RT!!
            "uberjar" ["do" "clean," "uberjar"]

            "deploy-uberjar" ["with-profile" "+deploy-uberjar" "deploy-uberjar"]

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
             ;; lein with-profile +jaeger uberjar
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
             ;; e.g. lein with-profile +jaeger,+wildfly uberjar
             :wildfly {:uberjar-exclusions [#"org/apache/commons/"
                                            #"org/apache/http/"
                                            #"javax/"]}

             ;; Just for dev
             :swank {:dependencies [[swank-clojure/swank-clojure "1.4.3"]
                                    [org.clojure/tools.nrepl "0.2.12"]]}

             :uberjar {:uberjar-name "buttle-standalone.jar"}
             
             :deploy-uberjar {:target-path "target/uberjar/"
                              :uberjar-name "buttle-standalone.jar"}
             
             :test {:dependencies [[org.postgresql/postgresql "9.4.1212"]
                                   [opentracing-clj "0.1.2"]
                                   
                                   ;; https://github.com/h-thurow/Simple-JNDI/tree/master
                                   ;; https://github.com/hen/osjava/tree/master/simple-jndi
                                   [com.github.h-thurow/simple-jndi "0.17.2"]
                                   [io.jaegertracing/jaeger-client "0.33.1"]
                                   [org.slf4j/slf4j-jdk14 "1.7.25"]]}})
