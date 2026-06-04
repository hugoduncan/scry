(ns scry.build-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]])
  (:import
   (java.util.jar JarFile)))

(defn build-tools-available? []
  (try
    (require 'clojure.tools.build.api)
    true
    (catch java.io.FileNotFoundException _
      false)))

(defn load-build! []
  (when (build-tools-available?)
    (load-file "build.clj")))

(defn git-rev-count []
  (let [{:keys [exit out err]} (shell/sh "git" "rev-list" "--count" "HEAD")]
    (when-not (zero? exit)
      (throw (ex-info "git rev-list --count HEAD failed" {:exit exit :err err})))
    (str/trim out)))

(defn deps-edn []
  (edn/read-string (slurp "deps.edn")))

(defn deps-kaocha-version []
  (get-in (deps-edn) [:aliases :kaocha :extra-deps 'lambdaisland/kaocha :mvn/version]))

(defn jar-entries [jar-file]
  (with-open [jar (JarFile. jar-file)]
    (set (map #(.getName %) (enumeration-seq (.entries jar))))))

(defn slurp-jar-entry [jar-file entry]
  (with-open [jar (JarFile. jar-file)
              in (.getInputStream jar (.getEntry jar entry))]
    (slurp in)))

(defn pom-dependencies
  "Returns dependency maps parsed from the small generated pom shape used by the build."
  [pom]
  (->> (re-seq #"(?s)<dependency>\s*(.*?)\s*</dependency>" pom)
       (map (fn [[_ dependency]]
              {:group-id (second (re-find #"(?s)<groupId>\s*([^<]+)\s*</groupId>" dependency))
               :artifact-id (second (re-find #"(?s)<artifactId>\s*([^<]+)\s*</artifactId>" dependency))
               :version (second (re-find #"(?s)<version>\s*([^<]+)\s*</version>" dependency))}))))

(defn pom-dependency
  [pom group-id artifact-id]
  (first (filter #(and (= group-id (:group-id %))
                       (= artifact-id (:artifact-id %)))
                 (pom-dependencies pom))))

(deftest version-uses-git-rev-count-test
  ;; The build version is fixed at major/minor 0.1 and uses the current Git
  ;; commit count as its patch component.
  (testing "version"
    (if-not (load-build!)
      (is true "skipping focused build check because :build alias is not active")
      (let [expected-count (git-rev-count)
            actual-version ((requiring-resolve 'build/version))]
        (is (str/starts-with? actual-version "0.1."))
        (is (= (str "0.1." expected-count) actual-version))))))

(deftest git-rev-count-failure-behavior-test
  ;; The version helper should fail clearly when the Git process boundary
  ;; reports failure or emits a non-numeric count.
  (testing "clear version failures"
    (if-not (load-build!)
      (is true "skipping focused build check because :build alias is not active")
      (let [git-rev-count (requiring-resolve 'build/git-rev-count)]
        (testing "non-zero Git command exit"
          (let [failure (try
                          (git-rev-count (constantly {:exit 128
                                                      :out ""
                                                      :err "fatal: not a git repository"}))
                          nil
                          (catch clojure.lang.ExceptionInfo ex
                            ex))]
            (is failure)
            (is (str/includes? (ex-message failure)
                               "git rev-list --count HEAD failed"))
            (is (= {:exit 128 :err "fatal: not a git repository"}
                   (ex-data failure)))))
        (testing "invalid non-numeric count"
          (let [failure (try
                          (git-rev-count (constantly {:exit 0
                                                      :out "not-a-number\n"
                                                      :err ""}))
                          nil
                          (catch clojure.lang.ExceptionInfo ex
                            ex))]
            (is failure)
            (is (str/includes? (ex-message failure)
                               "returned an invalid count"))
            (is (= {:out "not-a-number\n" :err ""}
                   (ex-data failure)))))))))

(deftest deploy-support-test
  ;; The deploy tasks require Clojars credentials and expose an injectable
  ;; deps-deploy boundary so focused tests can assert state without mocks.
  (testing "deploy"
    (if-not (load-build!)
      (is true "skipping focused build check because :build alias is not active")
      (let [assert-deploy-credentials! (requiring-resolve 'build/assert-deploy-credentials!)
            deploy (requiring-resolve 'build/deploy)
            deploy-all (requiring-resolve 'build/deploy-all)]
        (testing "requires Clojars credentials"
          (let [failure (try
                          (assert-deploy-credentials! {"CLOJARS_USERNAME" "user"
                                                       "CLOJARS_PASSWORD" ""})
                          nil
                          (catch clojure.lang.ExceptionInfo ex
                            ex))]
            (is failure)
            (is (= {:missing-env ["CLOJARS_PASSWORD"]}
                   (ex-data failure)))))
        (testing "builds and deploys the core artifact"
          (let [calls (atom [])
                {:keys [version jar-file pom-file]} (deploy {:env {"CLOJARS_USERNAME" "user"
                                                                   "CLOJARS_PASSWORD" "token"}
                                                            :deploy-fn #(swap! calls conj %)})
                entries (jar-entries jar-file)
                pom (slurp pom-file)]
            (is (= [{:installer :remote
                     :artifact jar-file
                     :pom-file pom-file}]
                   @calls))
            (is (= (str "target/scry-" version ".jar") jar-file))
            (is (str/includes? pom "<artifactId>scry</artifactId>"))
            (is (str/includes? pom (str "<version>" version "</version>")))
            (is (not (str/includes? pom "lambdaisland/kaocha")))
            (is (contains? entries "scry/core.clj"))
            (is (not (contains? entries "scry/kaocha.clj")))))
        (testing "deploy-all requires credentials"
          (let [failure (try
                          (deploy-all {:env {"CLOJARS_USERNAME" ""
                                             "CLOJARS_PASSWORD" "token"}
                                       :deploy-fn identity})
                          nil
                          (catch clojure.lang.ExceptionInfo ex
                            ex))]
            (is failure)
            (is (= {:missing-env ["CLOJARS_USERNAME"]}
                   (ex-data failure)))))
        (testing "deploy-all deploys core before adapter"
          (let [calls (atom [])
                {:keys [version artifacts]} (deploy-all {:env {"CLOJARS_USERNAME" "user"
                                                               "CLOJARS_PASSWORD" "token"}
                                                        :deploy-fn #(swap! calls conj %)})
                [core adapter] artifacts]
            (is (= 2 (count artifacts)))
            (is (= 'org.hugoduncan/scry (:lib core)))
            (is (= 'org.hugoduncan/scry-kaocha (:lib adapter)))
            (is (= [{:installer :remote
                     :artifact (:jar-file core)
                     :pom-file (:pom-file core)}
                    {:installer :remote
                     :artifact (:jar-file adapter)
                     :pom-file (:pom-file adapter)}]
                   @calls))
            (is (= (str "target/scry-" version ".jar") (:jar-file core)))
            (is (= (str "target/scry-kaocha-" version ".jar") (:jar-file adapter)))))))))

(deftest jar-build-produces-core-artifact-test
  ;; The jar task creates a core-only artifact with the expected coordinate,
  ;; version, and repository-only paths excluded by construction.
  (testing "jar build output"
    (if-not (load-build!)
      (is true "skipping focused build check because :build alias is not active")
      (let [{:keys [version jar-file lib pom-file]} ((requiring-resolve 'build/jar) nil)
            entries (jar-entries jar-file)
            pom-entry "META-INF/maven/org.hugoduncan/scry/pom.xml"
            pom (slurp-jar-entry jar-file pom-entry)]
        (testing "jar path and coordinate"
          (is (= 'org.hugoduncan/scry lib))
          (is (.exists (io/file jar-file)))
          (is (= (str "target/scry-" version ".jar") jar-file))
          (is (= "target/classes/META-INF/maven/org.hugoduncan/scry/pom.xml"
                 (str/replace pom-file #"^\./" ""))))
        (testing "generated pom coordinate"
          (is (str/includes? pom "<groupId>org.hugoduncan</groupId>"))
          (is (str/includes? pom "<artifactId>scry</artifactId>"))
          (is (str/includes? pom (str "<version>" version "</version>"))))
        (testing "generated pom keeps Kaocha optional boundary"
          (is (not (str/includes? pom "<groupId>lambdaisland</groupId>")))
          (is (not (str/includes? pom "<artifactId>kaocha</artifactId>")))
          (is (not (str/includes? pom "lambdaisland/kaocha"))))
        (testing "core namespaces are packaged"
          (is (contains? entries "scry/core.clj"))
          (is (contains? entries "scry/capture.clj"))
          (is (contains? entries "scry/clojure_test.clj")))
        (testing "non-artifact paths are excluded"
          (is (not-any? #(str/starts-with? % "test/") entries))
          (is (not-any? #(str/starts-with? % "munera/") entries))
          (is (not-any? #(str/starts-with? % "mementum/") entries))
          (is (not-any? #(str/starts-with? % ".psi/") entries))
          (is (not-any? #(str/starts-with? % ".cpcache/") entries))
          (is (not (contains? entries "scry/kaocha.clj"))))))))

(deftest kaocha-jar-build-produces-adapter-artifact-test
  ;; The Kaocha adapter jar packages only adapter source and writes a pom with
  ;; external dependencies on same-version core scry and the :kaocha alias dep.
  (testing "kaocha-jar build output"
    (if-not (load-build!)
      (is true "skipping focused build check because :build alias is not active")
      (let [jar (requiring-resolve 'build/jar)
            kaocha-jar (requiring-resolve 'build/kaocha-jar)
            core-artifact (jar nil)
            stale-adapter-jar (io/file "target/scry-kaocha-0.0.stale.jar")
            _ (spit stale-adapter-jar "stale adapter jar")
            {:keys [version jar-file lib pom-file]} (kaocha-jar nil)
            entries (jar-entries jar-file)
            pom-entry "META-INF/maven/org.hugoduncan/scry-kaocha/pom.xml"
            pom (slurp-jar-entry jar-file pom-entry)
            core-dependency (pom-dependency pom "org.hugoduncan" "scry")
            kaocha-dependency (pom-dependency pom "lambdaisland" "kaocha")
            alias-kaocha-version (deps-kaocha-version)]
        (testing "standalone adapter build removes stale adapter jars and preserves existing core jar"
          (is (not (.exists stale-adapter-jar)))
          (is (.exists (io/file (:jar-file core-artifact))))
          (is (= (str "target/scry-" version ".jar") (:jar-file core-artifact))))
        (testing "adapter jar path and coordinate"
          (is (= 'org.hugoduncan/scry-kaocha lib))
          (is (.exists (io/file jar-file)))
          (is (= (str "target/scry-kaocha-" version ".jar") jar-file))
          (is (= "target/classes-kaocha/META-INF/maven/org.hugoduncan/scry-kaocha/pom.xml"
                 (str/replace pom-file #"^\./" ""))))
        (testing "adapter contents"
          (is (contains? entries "scry/kaocha.clj"))
          (is (not (contains? entries "scry/core.clj")))
          (is (not (contains? entries "scry/capture.clj")))
          (is (not (contains? entries "scry/clojure_test.clj"))))
        (testing "adapter pom metadata"
          (is (str/includes? pom "<groupId>org.hugoduncan</groupId>"))
          (is (str/includes? pom "<artifactId>scry-kaocha</artifactId>"))
          (is (str/includes? pom (str "<version>" version "</version>")))
          (is (= {:group-id "org.hugoduncan"
                  :artifact-id "scry"
                  :version version}
                 core-dependency))
          (is (= {:group-id "lambdaisland"
                  :artifact-id "kaocha"
                  :version alias-kaocha-version}
                 kaocha-dependency)))))))

(deftest combined-jars-build-produces-both-artifacts-test
  ;; The combined release build cleans once, retains explicit paths for both
  ;; artifacts, and keeps core and adapter pom output isolated.
  (testing "jars build output"
    (if-not (load-build!)
      (is true "skipping focused build check because :build alias is not active")
      (let [{:keys [version artifacts]} ((requiring-resolve 'build/jars) nil)
            [core adapter] artifacts]
        (is (= 2 (count artifacts)))
        (is (= 'org.hugoduncan/scry (:lib core)))
        (is (= 'org.hugoduncan/scry-kaocha (:lib adapter)))
        (is (= (str "target/scry-" version ".jar") (:jar-file core)))
        (is (= (str "target/scry-kaocha-" version ".jar") (:jar-file adapter)))
        (is (.exists (io/file (:jar-file core))))
        (is (.exists (io/file (:jar-file adapter))))
        (is (.exists (io/file (:pom-file core))))
        (is (.exists (io/file (:pom-file adapter))))
        (is (not= (:pom-file core) (:pom-file adapter)))
        (is (str/includes? (:pom-file core) "target/classes/"))
        (is (str/includes? (:pom-file adapter) "target/classes-kaocha/"))))))
