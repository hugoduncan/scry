(ns scry.cli-kaocha-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.cli :as cli]
   [scry.temp-ns :as temp-ns]))

(defn- test-boundary
  [overrides]
  (merge (#'cli/default-boundary) overrides))

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
        boundary (test-boundary {:cwd (.getPath dir) :out out :err err})
        outcome (#'cli/run-cli opts boundary)]
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
           (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
                                              {:runner :kaocha :suite :unit}))]
             (is (= 0 (:exit-code outcome)))
             (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))
             (is (= "" (:stderr outcome)))
             (is (= [] (result-files project)))))
         (testing "selected failing suite writes adapter-shaped result file"
           (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
                                              {:runner :kaocha :suite :integration}))
                 files (result-files project)
                 expected-file (result-file-name failing-var)
                 result-file (io/file project ".scry-results" expected-file)
                 result-data (edn/read-string (slurp result-file))]
             (is (= 1 (:exit-code outcome)))
             (is (str/includes? (:stdout outcome) "Assertions: 0 passed, 1 failed, 0 errored"))
             (is (str/starts-with? (:stderr outcome) "failing-test\n"))
             (is (str/includes? (:stderr outcome) "for failure details"))
             (is (= [expected-file] files))
             (is (= failing-var (:var result-data)))
             (is (= :fail (:status result-data)))
             (is (str/includes? (:out result-data) "integration out"))
             (is (str/includes? (:out result-data) "integration err"))
             (is (= "" (:err result-data))))))))))

(deftest kaocha-cli-surfaces-randomize-seed-on-failure-test
  ;; A failing Kaocha CLI run surfaces the randomize seed on stdout as its own
  ;; line after the summary (replacing Kaocha's stray "Randomized with --seed N"
  ;; print, which the adapter suppresses). A passing run omits the seed line,
  ;; mirroring Kaocha's failure-only seed reporting.
  (when-kaocha-available
   (with-temp-dir [project]
     (let [unit-ns (unique-ns "seed" "unit-test")
           integration-ns (unique-ns "seed" "integration-test")]
       (write-suite-test-ns!
        project
        unit-ns
        "(deftest passing-test\n  (is true))\n")
       (write-suite-test-ns!
        project
        integration-ns
        "(deftest failing-test\n  (is (= 1 2)))\n")
       (write-tests-edn! project unit-ns integration-ns)
       (with-user-dir-and-ns-cleanup project [unit-ns integration-ns]
         (testing "failing run prints the seed on its own trailing line"
           (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
                                              {:runner :kaocha :suite :integration}))]
             (is (= 1 (:exit-code outcome)))
             (is (re-find #"(?m)^Randomized with --seed \d+\n?\z" (:stdout outcome))
                 "seed line is the final stdout line")
             (is (not (str/starts-with? (:stdout outcome) "Randomized"))
                 "seed does not precede/abut the summary")
             (is (str/includes? (:stdout outcome)
                                "Assertions: 0 passed, 1 failed, 0 errored"))))
         (testing "passing run omits the seed line"
           (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
                                              {:runner :kaocha :suite :unit}))]
             (is (= 0 (:exit-code outcome)))
             (is (not (str/includes? (:stdout outcome) "Randomized"))))))))))

(deftest kaocha-cli-positional-suite-run-test
  ;; Positional suite selectors on the -m wrapper forward verbatim as
  ;; :kaocha-argv and flow through parse -> normalize -> scry.kaocha/run, where
  ;; the adapter routes them through the same exact-id-then-unique-text
  ;; select-suites resolution as :suite/:suites: a single positional selects one
  ;; suite, multiple positionals select many.
  (when-kaocha-available
   (with-temp-dir [project]
     (let [unit-ns (unique-ns "pos" "unit-test")
           integration-ns (unique-ns "pos" "integration-test")
           failing-var (symbol (str integration-ns) "failing-test")]
       (write-suite-test-ns!
        project
        unit-ns
        "(deftest passing-test\n  (is true))\n")
       (write-suite-test-ns!
        project
        integration-ns
        "(deftest failing-test\n  (is (= 1 2)))\n")
       (write-tests-edn! project unit-ns integration-ns)
       (with-user-dir-and-ns-cleanup project [unit-ns integration-ns]
         (testing "single positional selector runs only the selected suite"
           (let [opts (#'cli/parse-main-args ["--runner" "kaocha" "unit"])
                 outcome (run-cli-in project opts)]
             (is (= ["unit"] (:kaocha-argv opts)))
             (is (= 0 (:exit-code outcome)))
             (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))
             (is (= [] (result-files project)))))
         (testing "multiple positional selectors run both suites"
           (let [opts (#'cli/parse-main-args ["--runner" "kaocha"
                                              "unit" "integration"])
                 outcome (run-cli-in project opts)]
             (is (= ["unit" "integration"] (:kaocha-argv opts)))
             (is (= 1 (:exit-code outcome)))
             (is (str/includes? (:stdout outcome)
                                "Assertions: 1 passed, 1 failed, 0 errored"))
             (is (= [(result-file-name failing-var)]
                    (result-files project))))))))))

(deftest kaocha-cli-load-error-stderr-test
  ;; A test namespace that fails to load surfaces an inline stderr diagnostic
  ;; (the load-error message + root cause) and a pointer at the results dir,
  ;; while still exiting non-zero and writing the synthetic suite-error file.
  (when-kaocha-available
   (with-temp-dir [project]
     (let [broken-ns (unique-ns "loaderr" "broken-test")]
       (write-suite-test-ns!
        project
        broken-ns
        "(deftest boom\n  (is true))\n\n(this-symbol-does-not-resolve-at-load)\n")
       (with-user-dir-and-ns-cleanup project [broken-ns]
         (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
                                            {:runner :kaocha
                                             :dirs "test"
                                             :ns-patterns [(exact-ns-pattern broken-ns)]}))
               stderr (:stderr outcome)]
           (is (= 1 (:exit-code outcome)))
           (is (= :scry.cli/load-error (:scry.cli/outcome-kind outcome)))
           (is (str/includes? (:stdout outcome) "1 errored"))
           ;; The suite-level error fires a live progress label during the run.
           (is (str/includes? stderr "suite-error-1"))
           ;; The inline diagnostic names the load failure and its root cause.
           (is (str/includes? stderr "Load error:"))
           (is (str/includes? stderr "this-symbol-does-not-resolve-at-load"))
           (is (str/includes? stderr "for failure details"))
           (is (= ["suite-error-1.edn"] (result-files project)))))))))

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
         (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
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
             (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
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
             (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
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
               (is (str/starts-with? (:stderr outcome) "config-failing-test\n"))
               (is (str/includes? (:stderr outcome) "for failure details"))
               (is (= [expected-file] files))
               (is (= failing-var (:var result-data)))
               (is (= :fail (:status result-data)))
               (is (str/includes? (:out result-data) "explicit out"))))))))))

(deftest kaocha-cli-focus-pass-through-test
  ;; --focus (-m) and :focus (-X) pass-through reach the Kaocha runner and
  ;; actually filter execution to the focused var, end-to-end through the CLI.
  (when-kaocha-available
   (with-temp-dir [project]
     (let [unit-ns (unique-ns "focus" "unit-test")
           keep-var (symbol (str unit-ns) "keep-test")]
       (write-suite-test-ns!
        project
        unit-ns
        "(deftest keep-test\n  (is true))\n\n(deftest drop-test\n  (is (= 1 2)))\n")
       (write-project-file!
        project
        "tests.edn"
        (str "#kaocha/v1\n"
             "{:tests [{:id :unit\n"
             "          :type :kaocha.type/clojure.test\n"
             "          :test-paths [\"test\"]\n"
             "          :ns-patterns [" (pr-str (exact-ns-pattern unit-ns)) "]}]}"))
       (with-user-dir-and-ns-cleanup project [unit-ns]
         (testing "without focus the failing var runs and the run fails"
           (let [outcome (run-cli-in project (#'cli/normalize-exec-opts
                                              {:runner :kaocha}))]
             (is (= 1 (:exit-code outcome)))))
         (testing "-m --runner kaocha --focus filters to the focused var"
           (let [opts (#'cli/parse-main-args
                       ["--runner" "kaocha" "--focus" (str keep-var)])
                 outcome (run-cli-in project opts)]
             (is (= 0 (:exit-code outcome)))
             (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))
             (is (= [keep-var]
                    (mapv :var (:canonical-results (:result outcome)))))))
         (testing "-X :runner :kaocha :focus filters to the focused var"
           (let [opts (#'cli/normalize-exec-opts
                       {:runner :kaocha :focus (str keep-var)})
                 outcome (run-cli-in project opts)]
             (is (= 0 (:exit-code outcome)))
             (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))
             (is (= [keep-var]
                    (mapv :var (:canonical-results (:result outcome))))))))))))

(deftest kaocha-cli-forwarded-option-reaches-kaocha-test
  ;; A previously-unsupported Kaocha option (`--no-randomize`) forwards verbatim
  ;; to Kaocha's own parser and demonstrably affects the run: with randomization
  ;; disabled, no `:seed` is surfaced in the result summary. A focused selector
  ;; forwarded alongside still filters execution, proving the full
  ;; -m -> :kaocha-argv -> Kaocha parse -> run chain for a non-scry option.
  (when-kaocha-available
   (with-temp-dir [project]
     (let [unit-ns (unique-ns "forward-opt" "unit-test")
           keep-var (symbol (str unit-ns) "keep-test")]
       (write-suite-test-ns!
        project
        unit-ns
        "(deftest keep-test\n  (is true))\n\n(deftest drop-test\n  (is (= 1 2)))\n")
       (write-project-file!
        project
        "tests.edn"
        (str "#kaocha/v1\n"
             "{:tests [{:id :unit\n"
             "          :type :kaocha.type/clojure.test\n"
             "          :test-paths [\"test\"]\n"
             "          :ns-patterns [" (pr-str (exact-ns-pattern unit-ns)) "]}]}"))
       (with-user-dir-and-ns-cleanup project [unit-ns]
         (testing "--no-randomize forwards and disables seed; --focus still filters"
           (let [opts (#'cli/parse-main-args
                       ["--runner" "kaocha" "--no-randomize"
                        "--focus" (str keep-var)])
                 outcome (run-cli-in project opts)]
             (is (= ["--no-randomize" "--focus" (str keep-var)]
                    (:kaocha-argv opts)))
             (is (= 0 (:exit-code outcome)))
             (is (str/starts-with? (:stdout outcome) ".Assertions: 1 passed"))
             (is (= [keep-var]
                    (mapv :var (:canonical-results (:result outcome)))))
             (is (nil? (get-in outcome [:result :summary :seed]))
                 "disabling randomization suppresses the surfaced seed"))))))))

(deftest kaocha-cli-malformed-option-is-runner-error-test
  ;; A malformed -m Kaocha option is no longer an scry argument error: it
  ;; forwards to Kaocha's own parser, which rejects it, so scry surfaces a
  ;; runner error (the accepted -m reclassification trade-off).
  (when-kaocha-available
   (with-temp-dir [project]
     (let [unit-ns (unique-ns "typo" "unit-test")]
       (write-suite-test-ns!
        project
        unit-ns
        "(deftest passing-test\n  (is true))\n")
       (write-tests-edn! project unit-ns unit-ns)
       (with-user-dir-and-ns-cleanup project [unit-ns]
         (let [opts (#'cli/parse-main-args
                     ["--runner" "kaocha" "--no-such-kaocha-flag"])
               outcome (run-cli-in project opts)]
           (is (= ["--no-such-kaocha-flag"] (:kaocha-argv opts)))
           (is (= 1 (:exit-code outcome)))
           (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
           ;; The otherwise-silent runner-error path emits a minimal,
           ;; clearly-labelled summary on stdout (supplementary human output);
           ;; the authoritative signals and the stderr diagnostic are unchanged.
           ;; The malformed flag is rejected by Kaocha's parser before any test
           ;; runs, so stdout carries exactly the minimal summary line and we
           ;; assert it with byte-stable equality (matching the core
           ;; cli_test.clj contract).
           (is (= "No tests run — scry CLI error outcome: :scry.cli/runner-error\n"
                  (:stdout outcome)))
           (is (str/includes? (:stderr outcome) "scry CLI error:"))
           (is (nil? (:summary outcome)))))))))

(deftest kaocha-cli-core-only-selectors-rejected-test
  ;; Core-only namespace/var/ns-pattern selectors are not Kaocha concepts; in
  ;; Kaocha mode they stay scry-owned and parsed-then-rejected with a clean
  ;; argument error, distinct from the runner/load error of an unknown forwarded
  ;; Kaocha flag.
  (when-kaocha-available
   (with-temp-dir [project]
     (doseq [args [["--runner" "kaocha" "--namespace" "my.ns"]
                   ["--runner" "kaocha" "--var" "my.ns/test-foo"]
                   ["--runner" "kaocha" "--ns-pattern" "my\\.ns"]]]
       (let [ex (try
                  (#'cli/parse-main-args args)
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
         (is (= :scry.cli/argument-error (:type (ex-data ex)))
             (str "expected argument-error for " (pr-str args))))))))

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
