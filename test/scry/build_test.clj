(ns scry.build-test
  (:require
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

(defn jar-entries [jar-file]
  (with-open [jar (JarFile. jar-file)]
    (set (map #(.getName %) (enumeration-seq (.entries jar))))))

(defn slurp-jar-entry [jar-file entry]
  (with-open [jar (JarFile. jar-file)
              in (.getInputStream jar (.getEntry jar entry))]
    (slurp in)))

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
  ;; The deploy task should require Clojars credentials, build the same
  ;; core-only artifact as jar, and pass that jar plus its generated pom to
  ;; deps-deploy through an injectable process boundary.
  (testing "deploy"
    (if-not (load-build!)
      (is true "skipping focused build check because :build alias is not active")
      (let [assert-deploy-credentials! (requiring-resolve 'build/assert-deploy-credentials!)
            deploy (requiring-resolve 'build/deploy)]
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
                jar-path (io/file jar-file)
                entries (jar-entries jar-path)
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
            (is (not (contains? entries "scry/kaocha.clj")))))))))

(deftest jar-build-produces-core-artifact-test
  ;; The jar task creates a core-only artifact with the expected coordinate,
  ;; version, and repository-only paths excluded by construction.
  (testing "jar build output"
    (if-not (load-build!)
      (is true "skipping focused build check because :build alias is not active")
      (let [{:keys [version jar-file]} ((requiring-resolve 'build/jar) nil)
            jar-path (io/file jar-file)
            entries (jar-entries jar-path)
            pom-entry "META-INF/maven/org.hugoduncan/scry/pom.xml"
            pom (slurp-jar-entry jar-path pom-entry)]
        (testing "jar path"
          (is (.exists jar-path))
          (is (= (str "target/scry-" version ".jar") jar-file)))
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
