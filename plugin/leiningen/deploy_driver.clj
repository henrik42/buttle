(ns leiningen.deploy-driver
  (:require [cemerick.pomegranate.aether :as aether]
            [leiningen.deploy :as ldeploy]
            [leiningen.core.main :as main]))

(defn deploy-driver
  "Deploys the Buttle driver (UBERJAR) to the given repo.

   Deploys the JAR **only**. The `pom.xml` will not be deployed. Use
   this to deploy just one file to a repo when you need a
   `classifier`.

   Example: build UBERJAR and deploy 

      lein deploy-driver snapshots buttle/buttle 0.1.1-SNAPSHOT driver target/uberjar/buttle-0.1.1-SNAPSHOT-standalone.jar

   "
  
  [project repository identifier version classifier file]
  (let [identifier (symbol identifier)
        artifact-id (name identifier)
        group-id (namespace identifier)
        repo (ldeploy/repo-for project repository)
        artifact-map {[:extension "jar"
                       :classifier classifier] file}
        deploy-args [:coordinates [(symbol group-id artifact-id) version]
                     :artifact-map artifact-map
                     :transfer-listener :stdout
                     :repository [repo]
                     :local-repo (:local-repo project)]]
    (System/setProperty "aether.checksums.forSignature" "true")
    ;; WARNING: prints passwords in repo map
    (main/info "deploy-driver: Deploying " deploy-args)
    (apply aether/deploy deploy-args)))

