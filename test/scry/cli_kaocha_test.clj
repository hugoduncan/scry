(ns scry.cli-kaocha-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.cli :as cli]))

(defmacro when-kaocha-available
  [& body]
  `(if (try
         (requiring-resolve 'scry.kaocha/run)
         (catch java.io.FileNotFoundException _# nil))
     (do ~@body)
     (is true "Kaocha CLI tests are skipped unless the :kaocha alias is active")))

(defn- temp-dir
  []
  (.toFile (java.nio.file.Files/createTempDirectory
            "scry-cli-kaocha-test"
            (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- delete-recursive!
  [file]
  (when (.exists file)
    (when (.isDirectory file)
      (doseq [child (.listFiles file)]
        (delete-recursive! child)))
    (.delete file)))

(defmacro with-temp-dir
  [[sym] & body]
  `(let [~sym (temp-dir)]
     (try
       ~@body
       (finally
         (delete-recursive! ~sym)))))

(defmacro with-user-dir
  [dir & body]
  `(let [old-dir# (System/getProperty "user.dir")]
     (System/setProperty "user.dir" (.getAbsolutePath (io/file ~dir)))
     (try
       ~@body
       (finally
         (System/setProperty "user.dir" old-dir#)))))

(defn- write-project-file!
  [project relative-path content]
  (let [file (io/file project relative-path)]
    (io/make-parents file)
    (spit file content)
    file))

(defn- write-suite-test-ns!
  [project ns-name body]
  (write-project-file!
   project
   (str "test/" (-> ns-name
                     (str/replace "." "/")
                     (str/replace "-" "_"))
        ".clj")
   (str "(ns " ns-name "\n"
        "  (:require [clojure.test :refer [deftest is]]))\n\n"
        body)))

(defn- write-tests-edn!
  [project]
  (write-project-file!
   project
   "tests.edn"
   "#kaocha/v1
    {:tests [{:id :unit
              :type :kaocha.type/clojure.test
              :test-paths [\"test\"]
              :ns-patterns [\"demo\\\\.unit-test\"]}
             {:id :integration
              :type :kaocha.type/clojure.test
              :test-paths [\"test\"]
              :ns-patterns [\"demo\\\\.integration-test\"]}]}"))

(defn- normalize-kaocha-config
  [cfg]
  ((requiring-resolve 'kaocha.config/normalize) cfg))

(defn- string-writer
  []
  (java.io.StringWriter.))

(defn- run-cli-in
  [dir opts]
  (let [out (string-writer)
        err (string-writer)
        outcome (cli/run-cli opts {:cwd (.getPath dir) :out out :err err})]
    (assoc outcome :stdout (str out) :stderr (str err))))

(defn- result-files
  [dir]
  (->> (.listFiles (io/file dir ".scry-results"))
       (map #(.getName %))
       sort
       vec))

(deftest kaocha-cli-suite-run-test
  ;; Kaocha CLI mode uses the optional adapter dynamically, prints live
  ;; per-var progress, writes detailed EDN files, and preserves merged output.
  (when-kaocha-available
    (with-temp-dir [project]
      (write-suite-test-ns!
       project
       "demo.unit-test"
       "(deftest passing-test\n  (println \"unit out\")\n  (is true))\n")
      (write-suite-test-ns!
       project
       "demo.integration-test"
       "(deftest failing-test\n  (println \"integration out\")\n  (binding [*err* *out*] (println \"integration err\"))\n  (is (= 1 2)))\n")
      (write-tests-edn! project)
      (with-user-dir project
        (testing "selected passing suite succeeds"
          (let [outcome (run-cli-in project (cli/normalize-exec-opts
                                             {:runner :kaocha :suite :unit}))]
            (is (= 0 (:exit-code outcome)))
            (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))
            (is (= "" (:stderr outcome)))
            (is (= [] (result-files project)))))
        (testing "selected failing suite writes adapter-shaped result file"
          (let [outcome (run-cli-in project (cli/normalize-exec-opts
                                             {:runner :kaocha :suite :integration}))
                files (result-files project)
                result-file (io/file project ".scry-results" "demo.integration-test__failing-test.edn")
                result-data (edn/read-string (slurp result-file))]
            (is (= 1 (:exit-code outcome)))
            (is (str/includes? (:stdout outcome) "Assertions: 0 passed, 1 failed, 0 errored"))
            (is (= "failing-test\n" (:stderr outcome)))
            (is (= ["demo.integration-test__failing-test.edn"] files))
            (is (= 'demo.integration-test/failing-test (:var result-data)))
            (is (= :fail (:status result-data)))
            (is (str/includes? (:out result-data) "integration out"))
            (is (str/includes? (:out result-data) "integration err"))
            (is (= "" (:err result-data)))))))))

(deftest kaocha-cli-fallback-dirs-test
  ;; In Kaocha mode CLI :dirs maps to fallback :test-paths when there is no
  ;; explicit config/tests.edn, and core-only selectors still fail in normalize.
  (when-kaocha-available
    (with-temp-dir [project]
      (write-suite-test-ns!
       project
       "fallback.sample-test"
       "(deftest sample-test\n  (is true))\n")
      (with-user-dir project
        (let [outcome (run-cli-in project (cli/normalize-exec-opts
                                           {:runner :kaocha
                                            :dirs "test"
                                            :ns-patterns ["fallback\\.sample-test"]}))]
          (is (= 0 (:exit-code outcome)))
          (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed")))))))

(deftest kaocha-cli-explicit-config-run-test
  ;; Explicit :config maps are a documented Kaocha CLI selector path. Exercise
  ;; them end-to-end so coverage is distinct from tests.edn loading and fallback
  ;; :dirs/:test-paths normalization.
  (when-kaocha-available
    (with-temp-dir [project]
      (write-suite-test-ns!
       project
       "explicit.unit-test"
       "(deftest config-passing-test\n  (is true))\n")
      (write-suite-test-ns!
       project
       "explicit.integration-test"
       "(deftest config-failing-test\n  (println \"explicit out\")\n  (is (= 1 2)))\n")
      (with-user-dir project
        (let [config (normalize-kaocha-config
                      {:kaocha/tests [{:kaocha.testable/id :unit
                                       :kaocha.testable/type :kaocha.type/clojure.test
                                       :kaocha/source-paths []
                                       :kaocha/test-paths [(.getAbsolutePath (io/file project "test"))]
                                       :kaocha/ns-patterns ["explicit\\.unit-test"]}
                                      {:kaocha.testable/id :integration
                                       :kaocha.testable/type :kaocha.type/clojure.test
                                       :kaocha/source-paths []
                                       :kaocha/test-paths [(.getAbsolutePath (io/file project "test"))]
                                       :kaocha/ns-patterns ["explicit\\.integration-test"]}]})]
          (testing "explicit config with suite selection runs only the selected suite"
            (let [outcome (run-cli-in project (cli/normalize-exec-opts
                                               {:runner :kaocha
                                                :config config
                                                :suite :unit}))]
              (is (= 0 (:exit-code outcome)))
              (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))
              (is (= "" (:stderr outcome)))
              (is (= ['explicit.unit-test/config-passing-test]
                     (mapv :var (:canonical-results (:result outcome)))))
              (is (= [] (result-files project)))))
          (testing "the same explicit config can select a failing suite and write details"
            (let [outcome (run-cli-in project (cli/normalize-exec-opts
                                               {:runner :kaocha
                                                :config config
                                                :suite :integration}))
                  files (result-files project)
                  result-data (edn/read-string
                               (slurp (io/file project
                                               ".scry-results"
                                               "explicit.integration-test__config-failing-test.edn")))]
              (is (= 1 (:exit-code outcome)))
              (is (str/includes? (:stdout outcome)
                                 "Assertions: 0 passed, 1 failed, 0 errored"))
              (is (= "config-failing-test\n" (:stderr outcome)))
              (is (= ["explicit.integration-test__config-failing-test.edn"] files))
              (is (= 'explicit.integration-test/config-failing-test (:var result-data)))
              (is (= :fail (:status result-data)))
              (is (str/includes? (:out result-data) "explicit out")))))))))

(deftest kaocha-adapter-progress-callback-test
  ;; The optional adapter exposes a live end-of-var progress callback before the
  ;; final scry result is transformed.
  (when-kaocha-available
    (with-temp-dir [project]
      (write-suite-test-ns!
       project
       "progress.sample-test"
       "(deftest first-test\n  (is true))\n\n(deftest second-test\n  (is (= 1 2)))\n")
      (with-user-dir project
        (let [events (atom [])
              run-var (requiring-resolve 'scry.kaocha/run)
              result (run-var {:test-paths ["test"]
                               :ns-patterns ["progress\\.sample-test"]
                               :progress-callback #(swap! events conj (select-keys % [:var :status]))})]
          (is (false? (:pass? result)))
          (is (= [{:var 'progress.sample-test/first-test :status :pass}
                  {:var 'progress.sample-test/second-test :status :fail}]
                 @events)))))))
