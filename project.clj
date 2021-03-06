;; re-enables http repository support in Leiningen 2.8
(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))

(defproject buttle/buttle "1.0.1-SNAPSHOT"

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
            [lein-marginalia "0.9.1"]]

  :aliases {"uberjar" ["do" "clean," "uberjar"]                      ;; builds driver/UBERJAR to target/uberjar/buttle-driver.jar
            "deploy"  ["do" "clean," "deploy"]                       ;; deploys lib jar to snapshots/releases depending on version
            
            "deploy-driver" ["deploy-driver"                         ;; calls Buttle's plugin/leiningen/deploy_driver.clj
                             ":leiningen/repository"                 ;; pseudo repository -- see plugin/leiningen/deploy_driver.clj
                             "buttle/buttle"                         ;; group/artefact-id
                             ":leiningen/version"                    ;; pseudo version number -- see plugin/leiningen/deploy_driver.clj
                             "driver"                                ;; classifier
                             "target/uberjar/buttle-driver.jar"      ;; file -- see :uberjar-name
                             ]

            "deploy-all" ["do" "deploy," "uberjar," "deploy-driver"] ;; depoy everything to snapshots/releases depending on version

            ;; --------------------------------------------------------
            ;; RELEASE PROCEDURE
            ;;
            ;; A release requires invoking lein three times. See
            ;; comments on "release-broken!" below for some details
            ;; for why this is done this way.
            ;;
            ;; 1/3: lein with-profile +skip-test release-prepare!
            ;;   or buttle_user=<postgres-user> buttle_password=<postgres-password> lein release-prepare!
            ;;
            ;; 2/3: lein with-profile +local release-deploy!
            ;;
            ;; 3/3: lein with-profile +local release-finish!
            ;; 
            ;; After that you just need to push commits to github.
            ;; --------------------------------------------------------
            
            "release-prepare!" ["do"
                                ["vcs" "assert-committed"]
                                ["test"] ;; skip via `with-profile +skip-test`
                                
                                ;; bump to release version --> next step!
                                ["change" "version" "leiningen.release/bump-version" "release"]]
            
            "release-deploy!" ["do" 
                               ["make-doc"]
                               ["vcs" "commit"]
                               ["vcs" "tag" "--no-sign"]
                               
                               ;; build & deploy release version
                               ["deploy-all"] ;; use +with-profile <target-repo-profile> -- see :local profile
                               
                               ;; bump to next SNAPSHOT version --> next step!
                               ["change" "version" "leiningen.release/bump-version"]
                               ]
            
            "release-finish!" ["do" 
                               ["make-doc"]
                               ["vcs" "commit"]
                               
                               ;; build & deploy new SNAPSHOT
                               ["deploy-all"]]
  
            ;; -----------------------------------------------------------------------------------
            ;;
            ;; ************   THIS IS BROKEN!    ************
            ;;
            ;; (kept just for future reference)
            ;;
            ;; The lein `do` task loads `project.clj` only ONCE. So
            ;; after ["change" "version" ,,,] the project version is
            ;; unchanged for the tasks which are invoked by `do`. The
            ;; `release` task loads `project.clj` before invoking each
            ;; of the `:release-tasks`. But the `release` task does
            ;; not honor `with-profile` "settings". So none of these
            ;; works.
            ;;
            ;; Workaround: split up the `do` tasks and invoke lein
            ;; from shell for each "release-step" (see above). Each
            ;; time lein is invoked `project.clj` will be read and so
            ;; the new/changed version info will be visible to the
            ;; invoked tasks.
            ;;
            ;; -----------------------------------------------------------------------------------
            
            "release-broken!"
            ["do" 
                       
             ;; prepare / build and test
             ["vcs" "assert-committed"]
             ["test"]

             ;; bump to release version and commit
             ["change" "version" "leiningen.release/bump-version" "release"]
             ["make-doc"]
             ["vcs" "commit"]
             ["vcs" "tag" "--no-sign"]

             ;; build & deploy release version
             ["deploy-all"]
             
             ;; bump to next SNAPSHOT and commit
             ["change" "version" "leiningen.release/bump-version"]
             ["make-doc"]
             ["vcs" "commit"]
             ["vcs" "push"]
             
             ;; build & deploy SNASHOT version
             ["deploy-all"]]
            ;; -------- END BROKEN --------------------------------------
            
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

             :uberjar {:uberjar-name "buttle-driver.jar"}

             ;; use for `release-prepare!`
             :skip-test {:aliases {"test" ["do"]}}
             
             :test {:dependencies [[org.postgresql/postgresql "9.4.1212"]
                                   [opentracing-clj "0.1.2"]
                                   
                                   ;; https://github.com/h-thurow/Simple-JNDI/tree/master
                                   ;; https://github.com/hen/osjava/tree/master/simple-jndi
                                   [com.github.h-thurow/simple-jndi "0.17.2"]
                                   [io.jaegertracing/jaeger-client "0.33.1"]
                                   [org.slf4j/slf4j-jdk14 "1.7.25"]]}

             ;; run a local Nexus in a docker container with:
             ;; docker run -d -p 8081:8081 --name nexus sonatype/nexus:oss
             ;;
             ;; Then you can deploy a SNAPSHOT or release via
             ;; lein with-profile +local deploy-all
             :local {:repositories [["snapshots" {:url "http://localhost:8081/nexus/content/repositories/snapshots/"
                                                  :sign-releases false :username "admin" :password "admin123"}]
                                    ["releases" {:url "http://localhost:8081/nexus/content/repositories/releases/"
                                                 :sign-releases false :username "admin" :password "admin123"}]]}})

