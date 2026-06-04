(ns scry.cli-kaocha-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.cli :as cli]
   [scry.temp-ns :as temp-ns]))

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

(defn- remove-namespaces!
  [ns-syms]
  (doseq [ns-sym ns-syms]
    (remove-ns ns-sym)))

(defmacro with-user-dir-and-ns-cleanup
  [dir ns-syms & body]
  `(let [old-dir# (System/getProperty "user.dir")
         ns-syms# ~ns-syms]
     (System/setProperty "user.dir" (.getAbsolutePath (io/file ~dir)))
     (try
       ~@body
       (finally
         (System/setProperty "user.dir" old-dir#)
         (remove-namespaces! ns-syms#)))))

(defn- unique-ns
  [prefix leaf]
  (temp-ns/unique-ns prefix leaf))

(defn- exact-ns-pattern
  [ns-name]
  (java.util.regex.Pattern/quote (str ns-name)))

(defn- result-file-name
  [var-sym]
  (str (namespace var-sym) "__" (name var-sym) ".edn"))

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
  [project unit-ns integration-ns]
  (write-project-file!
   project
   "tests.edn"
   (str "#kaocha/v1\n"
        "{:tests [{:id :unit\n"
        "          :type :kaocha.type/clojure.test\n"
        "          :test-paths [\"test\"]\n"
        "          :ns-patterns [" (pr-str (exact-ns-pattern unit-ns)) "]}\n"
        "         {:id :integration\n"
        "          :type :kaocha.type/clojure.test\n"
        "          :test-paths [\"test\"]\n"
        "          :ns-patterns [" (pr-str (exact-ns-pattern integration-ns)) "]}]}")))

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
      (let [unit-ns (unique-ns "demo" "unit-test")
            integration-ns (unique-ns "demo" "integration-test")
            failing-var (symbol (str integration-ns) "failing-test")]
        (write-suite-test-ns!
         project
         unit-ns
         "(deftest passing-test\n  (println \"unit out\")\n  (is true))\n")
        (write-suite-test-ns!
         project
         integration-ns
         "(deftest failing-test\n  (println \"integration out\")\n  (binding [*err* *out*] (println \"integration err\"))\n  (is (= 1 2)))\n")
        (write-tests-edn! project unit-ns integration-ns)
        (with-user-dir-and-ns-cleanup project [unit-ns integration-ns]
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
                  expected-file (result-file-name failing-var)
                  result-file (io/file project ".scry-results" expected-file)
                  result-data (edn/read-string (slurp result-file))]
              (is (= 1 (:exit-code outcome)))
              (is (str/includes? (:stdout outcome) "Assertions: 0 passed, 1 failed, 0 errored"))
              (is (= "failing-test\n" (:stderr outcome)))
              (is (= [expected-file] files))
              (is (= failing-var (:var result-data)))
              (is (= :fail (:status result-data)))
              (is (str/includes? (:out result-data) "integration out"))
              (is (str/includes? (:out result-data) "integration err"))
              (is (= "" (:err result-data))))))))))

(deftest kaocha-cli-fallback-dirs-test
  ;; In Kaocha mode CLI :dirs maps to fallback :test-paths when there is no
  ;; explicit config/tests.edn, and core-only selectors still fail in normalize.
  (when-kaocha-available
    (with-temp-dir [project]
      (let [sample-ns (unique-ns "fallback" "sample-test")]
        (write-suite-test-ns!
         project
         sample-ns
         "(deftest sample-test\n  (is true))\n")
        (with-user-dir-and-ns-cleanup project [sample-ns]
          (let [outcome (run-cli-in project (cli/normalize-exec-opts
                                             {:runner :kaocha
                                              :dirs "test"
                                              :ns-patterns [(exact-ns-pattern sample-ns)]}))]
            (is (= 0 (:exit-code outcome)))
            (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))))))))

(deftest kaocha-cli-explicit-config-run-test
  ;; Explicit :config maps are a documented Kaocha CLI selector path. Exercise
  ;; them end-to-end so coverage is distinct from tests.edn loading and fallback
  ;; :dirs/:test-paths normalization.
  (when-kaocha-available
    (with-temp-dir [project]
      (let [unit-ns (unique-ns "explicit" "unit-test")
            integration-ns (unique-ns "explicit" "integration-test")
            passing-var (symbol (str unit-ns) "config-passing-test")
            failing-var (symbol (str integration-ns) "config-failing-test")]
        (write-suite-test-ns!
         project
         unit-ns
         "(deftest config-passing-test\n  (is true))\n")
        (write-suite-test-ns!
         project
         integration-ns
         "(deftest config-failing-test\n  (println \"explicit out\")\n  (is (= 1 2)))\n")
        (with-user-dir-and-ns-cleanup project [unit-ns integration-ns]
          (let [config (normalize-kaocha-config
                        {:kaocha/tests [{:kaocha.testable/id :unit
                                         :kaocha.testable/type :kaocha.type/clojure.test
                                         :kaocha/source-paths []
                                         :kaocha/test-paths [(.getAbsolutePath (io/file project "test"))]
                                         :kaocha/ns-patterns [(exact-ns-pattern unit-ns)]}
                                        {:kaocha.testable/id :integration
                                         :kaocha.testable/type :kaocha.type/clojure.test
                                         :kaocha/source-paths []
                                         :kaocha/test-paths [(.getAbsolutePath (io/file project "test"))]
                                         :kaocha/ns-patterns [(exact-ns-pattern integration-ns)]}]})]
            (testing "explicit config with suite selection runs only the selected suite"
              (let [outcome (run-cli-in project (cli/normalize-exec-opts
                                                 {:runner :kaocha
                                                  :config config
                                                  :suite :unit}))]
                (is (= 0 (:exit-code outcome)))
                (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))
                (is (= "" (:stderr outcome)))
                (is (= [passing-var]
                       (mapv :var (:canonical-results (:result outcome)))))
                (is (= [] (result-files project)))))
            (testing "the same explicit config can select a failing suite and write details"
              (let [outcome (run-cli-in project (cli/normalize-exec-opts
                                                 {:runner :kaocha
                                                  :config config
                                                  :suite :integration}))
                    files (result-files project)
                    expected-file (result-file-name failing-var)
                    result-data (edn/read-string
                                 (slurp (io/file project
                                                 ".scry-results"
                                                 expected-file)))]
                (is (= 1 (:exit-code outcome)))
                (is (str/includes? (:stdout outcome)
                                   "Assertions: 0 passed, 1 failed, 0 errored"))
                (is (= "config-failing-test\n" (:stderr outcome)))
                (is (= [expected-file] files))
                (is (= failing-var (:var result-data)))
                (is (= :fail (:status result-data)))
                (is (str/includes? (:out result-data) "explicit out"))))))))))

(deftest kaocha-adapter-progress-callback-test
  ;; The optional adapter exposes a live end-of-var progress callback before the
  ;; final scry result is transformed.
  (when-kaocha-available
    (with-temp-dir [project]
      (let [sample-ns (unique-ns "progress" "sample-test")
            first-var (symbol (str sample-ns) "first-test")
            second-var (symbol (str sample-ns) "second-test")]
        (write-suite-test-ns!
         project
         sample-ns
         "(deftest first-test\n  (is true))\n\n(deftest second-test\n  (is (= 1 2)))\n")
        (with-user-dir-and-ns-cleanup project [sample-ns]
          (let [events (atom [])
                run-var (requiring-resolve 'scry.kaocha/run)
                result (run-var {:test-paths ["test"]
                                 :ns-patterns [(exact-ns-pattern sample-ns)]
                                 :progress-callback #(swap! events conj (select-keys % [:var :status]))})]
            (is (false? (:pass? result)))
            (is (= [{:var first-var :status :pass}
                    {:var second-var :status :fail}]
                   @events))))))))
