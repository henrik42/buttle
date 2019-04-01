(ns leiningen.deploy-driver
  (:require [cemerick.pomegranate.aether :as aether]
            [leiningen.deploy :as ldeploy]
            [leiningen.core.main :as main]))

(defn deploy-driver
  "Deploys the Buttle driver (UBERJAR) to the given repo.

   Deploys the JAR **only**. The `pom.xml` will not be deployed. Use
   this to deploy just one file to a repository when you need a
   `classifier`.

   Example: build UBERJAR and deploy (works for snapshots and releases)

      lein uberjar 
      lein deploy-driver :leiningen/repository buttle/buttle :leiningen/version driver target/uberjar/buttle-driver.jar

   "
  
  [project repository identifier version classifier file]
  (let [identifier (symbol identifier)
        artifact-id (name identifier)
        group-id (namespace identifier)
        version (if (= version ":leiningen/version")
                  (:version project)
                  version)
        repository (if (= repository ":leiningen/repository")
                     (if (.endsWith version "-SNAPSHOT")
                       "snapshots"
                       "releases")
                     repository)
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

