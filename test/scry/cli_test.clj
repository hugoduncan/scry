(ns scry.cli-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.cli :as cli]
   [scry.cli.results :as cli-results]
   [scry.clojure-test :as clojure-test]
   [scry.fixtures.arbitrary]
   [scry.fixtures.background-output]
   [scry.fixtures.asserting-fixtures]
   [scry.fixtures.colliding-a]
   [scry.fixtures.colliding-b]
   [scry.fixtures.erroring]
   [scry.fixtures.failing]
   [scry.fixtures.mixed]
   [scry.fixtures.output]
   [scry.fixtures.pathological]
   [scry.fixtures.passing]
   [scry.fixtures.short-circuiting-fixtures]
   [scry.fixtures.unknown]))

(defn not-a-test
  []
  :not-a-test)

(defn- test-boundary
  [overrides]
  (merge (#'cli/default-boundary) overrides))

(defn- argument-error?
  [f]
  (try
    (f)
    false
    (catch clojure.lang.ExceptionInfo e
      (= :scry.cli/argument-error (:type (ex-data e))))))

(deftest normalize-exec-opts-core-test
  ;; -X option normalization accepts core runner aliases, selectors, and
  ;; detailed CLI result retention without running tests.
  (testing "default runner and detailed result-format retention"
    (let [opts (#'cli/normalize-exec-opts {})]
      (is (= :clojure-test (:runner opts)))
      (is (contains? (set (get-in opts [:result-format :suite :top-level-keys]))
                     :canonical-results))
      (is (contains? (set (get-in opts [:result-format :namespace :top-level-keys]))
                     :canonical-results))
      (is (contains? (set (get-in opts [:result-format :var :top-level-keys]))
                     :canonical-results))))
  (testing "core selector coercions"
    (let [opts (#'cli/normalize-exec-opts
                {:runner "core"
                 :dirs "test"
                 :namespaces "scry.fixtures.passing"
                 :vars "scry.fixtures.passing/arithmetic-passes"
                 :namespace-regex "scry\\.fixtures\\..*"
                 :result-format {:suite {:top-level-keys [:summary]
                                         :entry-keys [:var]}}})]
      (is (= :clojure-test (:runner opts)))
      (is (= ["test"] (:dirs opts)))
      (is (= ['scry.fixtures.passing] (:namespaces opts)))
      (is (= [#'scry.fixtures.passing/arithmetic-passes] (:vars opts)))
      (is (instance? java.util.regex.Pattern (:ns-pattern opts)))
      (is (= [:summary :pass? :canonical-results]
             (get-in opts [:result-format :suite :top-level-keys]))))))

(deftest normalize-exec-opts-var-errors-test
  ;; CLI var selectors reject ambiguous, unresolved, and non-test var values
  ;; before runner execution.
  (testing "var selector errors"
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:vars 'arithmetic-passes})))
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:vars 'scry.fixtures.passing/missing})))
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:vars #'not-a-test}))))
  (testing "invalid regex errors"
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:ns-pattern "["})))))

(deftest normalize-exec-opts-runner-validation-test
  ;; Runner-specific options fail early when supplied to the wrong runner.
  (testing "core mode rejects Kaocha-only options"
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :clojure-test :suite :unit}))))
  (testing "Kaocha mode rejects core-only selectors"
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha
                                      :namespaces ['scry.fixtures.passing]})))
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha
                                      :vars ['scry.fixtures.passing/arithmetic-passes]})))
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha
                                      :ns-pattern #".*"})))
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha
                                      :namespace-pattern ".*"})))
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha
                                      :namespace-regex ".*"}))))
  (testing "Kaocha directory conflicts fail clearly"
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha
                                      :dirs ["test"]
                                      :test-paths ["other-test"]})))
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha
                                      :dirs ["test"]
                                      :config {:kaocha/tests []}}))))
  (testing "unknown runner fails"
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :unknown})))))

(deftest normalize-exec-opts-kaocha-test
  ;; Kaocha normalization accepts suite/config/fallback options and maps dirs
  ;; to fallback test paths when no explicit config is supplied.
  (testing "Kaocha options"
    (let [opts (#'cli/normalize-exec-opts {:runner "kaocha"
                                           :dirs ["test" "integration-test"]
                                           :suite :unit})]
      (is (= :kaocha (:runner opts)))
      (is (= ["test" "integration-test"] (:test-paths opts)))
      (is (= :unit (:suite opts)))))
  (testing "Kaocha suites must be a non-empty collection"
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha :suites []})))
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :kaocha :suites :unit})))))

(deftest normalize-exec-opts-kaocha-pass-through-test
  ;; -X map normalization forwards unrecognized top-level keys as raw
  ;; `:kaocha-extra` pass-through instead of dropping them.
  (testing "unknown top-level key collected into :kaocha-extra"
    (let [opts (#'cli/normalize-exec-opts {:runner :kaocha
                                           :focus "my.ns/test-foo"})]
      (is (= {:focus "my.ns/test-foo"} (:kaocha-extra opts)))))
  (testing "scry-managed keys never leak into :kaocha-extra"
    (let [opts (#'cli/normalize-exec-opts {:runner :kaocha
                                           :result-format {:suite {:top-level-keys [:summary]}}
                                           :suite :unit
                                           :dirs ["test"]
                                           :focus "my.ns/test-foo"})]
      (is (= {:focus "my.ns/test-foo"} (:kaocha-extra opts)))
      (is (= :unit (:suite opts)))
      (is (= ["test"] (:test-paths opts)))))
  (testing "the full scry-managed closed set never leaks into :kaocha-extra"
    ;; Exercise every scry-managed key reachable in Kaocha mode (excluding
    ;; core-only selectors, which are rejected earlier, and :dirs, which
    ;; conflicts with explicit :config) alongside a single unknown key. Each
    ;; scry-managed key must route to its normalized destination or be
    ;; excluded; only the unknown key may appear under :kaocha-extra. The most
    ;; dangerous omission is :progress-callback (a function value) which would
    ;; otherwise silently leak into :kaocha/cli-options if dropped from
    ;; scry-managed-keys.
    (let [config {:kaocha/tests []}
          opts (#'cli/normalize-exec-opts {:runner :kaocha
                                           :result-format {:suite {:top-level-keys [:summary]}}
                                           :progress-callback (fn [_] nil)
                                           :source-paths ["src"]
                                           :ns-patterns ["foo.*"]
                                           :config config
                                           :suites ["unit"]
                                           :kaocha-argv ["--focus" "my.ns/test-foo"]
                                           :focus "my.ns/test-foo"})]
      ;; Only the unknown key is forwarded as pass-through.
      (is (= {:focus "my.ns/test-foo"} (:kaocha-extra opts)))
      ;; :kaocha-argv is scry-managed: it routes to its own normalized
      ;; destination and must never leak into :kaocha-extra (the -X path must
      ;; not forward raw -m argv), guarding Slice 4's "no :kaocha-argv leakage
      ;; into -X".
      (is (= ["--focus" "my.ns/test-foo"] (:kaocha-argv opts)))
      (is (not (contains? (:kaocha-extra opts) :kaocha-argv)))
      ;; Each scry-managed key routes to its normalized destination ...
      (is (= :kaocha (:runner opts)))
      (is (contains? opts :result-format))
      (is (= ["src"] (:source-paths opts)))
      (is (= ["foo.*"] (:ns-patterns opts)))
      (is (= config (:config opts)))
      (is (= ["unit"] (:suites opts)))
      ;; ... or is excluded entirely (:progress-callback is added later in the
      ;; run pipeline, never by normalization).
      (is (not (contains? opts :progress-callback)))))
  (testing "pre-existing :kaocha-extra map survives and scattered extras merge in"
    (let [opts (#'cli/normalize-exec-opts {:runner :kaocha
                                           :kaocha-extra {:focus ["my.ns/test-foo"]}
                                           :threads 4})]
      (is (= {:focus ["my.ns/test-foo"] :threads 4} (:kaocha-extra opts)))))
  (testing "no :kaocha-extra when no extra keys supplied"
    (let [opts (#'cli/normalize-exec-opts {:runner :kaocha :suite :unit})]
      (is (not (contains? opts :kaocha-extra)))))
  (testing "Kaocha pass-through rejected in core mode"
    (is (argument-error?
         #(#'cli/normalize-exec-opts {:runner :clojure-test
                                      :kaocha-extra {:focus ["x"]}})))))

(deftest parse-main-args-test
  ;; -m string flags normalize to the same option map shape as -X options.
  (testing "accepted core flags"
    (let [opts (#'cli/parse-main-args ["--runner" "test"
                                       "--dir" "test"
                                       "--namespace" "scry.fixtures.passing"
                                       "--var" "scry.fixtures.passing/arithmetic-passes"
                                       "--ns-pattern" "scry\\.fixtures\\..*"
                                       "--result-format" "{:suite {:top-level-keys [:summary]}}"])]
      (is (= :clojure-test (:runner opts)))
      (is (= ["test"] (:dirs opts)))
      (is (= ['scry.fixtures.passing] (:namespaces opts)))
      (is (= [#'scry.fixtures.passing/arithmetic-passes] (:vars opts)))
      (is (= [:summary :pass? :canonical-results]
             (get-in opts [:result-format :suite :top-level-keys])))))
  (testing "accepted core short and alias flags"
    (let [opts (#'cli/parse-main-args ["-r" "core"
                                       "-d" "test"
                                       "--ns" "scry.fixtures.passing"
                                       "-n" "scry.fixtures.failing"
                                       "-v" "scry.fixtures.passing/arithmetic-passes"
                                       "--namespace-pattern" "scry\\.fixtures\\..*"])]
      (is (= :clojure-test (:runner opts)))
      (is (= ["test"] (:dirs opts)))
      (is (= ['scry.fixtures.passing 'scry.fixtures.failing] (:namespaces opts)))
      (is (= [#'scry.fixtures.passing/arithmetic-passes] (:vars opts)))
      (is (instance? java.util.regex.Pattern (:ns-pattern opts)))))
  (testing "accepted namespace-regex parser alias"
    (let [opts (#'cli/parse-main-args ["--namespace-regex" "scry\\.fixtures\\..*"])]
      (is (= :clojure-test (:runner opts)))
      (is (instance? java.util.regex.Pattern (:ns-pattern opts)))))
  (testing "single positional suite selector forwards as :kaocha-argv"
    (let [opts (#'cli/parse-main-args ["--runner" "kaocha"
                                       "unit"])]
      (is (= :kaocha (:runner opts)))
      (is (= ["unit"] (:kaocha-argv opts)))
      (is (not (contains? opts :suite)))
      (is (not (contains? opts :suites)))))
  (testing "multiple positional suite selectors forward as :kaocha-argv"
    (let [opts (#'cli/parse-main-args ["--runner" "kaocha"
                                       "unit" "integration"])]
      (is (= :kaocha (:runner opts)))
      (is (= ["unit" "integration"] (:kaocha-argv opts)))))
  (testing "Kaocha options and positionals forward verbatim in original order"
    (let [opts (#'cli/parse-main-args ["--runner" "kaocha"
                                       "unit"
                                       "--focus" "my.ns/test-foo"
                                       "integration"])]
      (is (= :kaocha (:runner opts)))
      (is (= ["unit" "--focus" "my.ns/test-foo" "integration"]
             (:kaocha-argv opts)))
      (is (not (contains? opts :kaocha-extra)))))
  (testing "accepted Kaocha config EDN flag stays scry-owned"
    (let [opts (#'cli/parse-main-args ["--runner" "kaocha"
                                       "--config" "{:kaocha/tests []}"])]
      (is (= {:kaocha/tests []} (:config opts)))
      (is (not (contains? opts :kaocha-argv)))))
  (testing "former --focus flag now forwards as raw :kaocha-argv"
    (let [opts (#'cli/parse-main-args ["--runner" "kaocha"
                                       "--focus" "my.ns/test-foo"])]
      (is (= :kaocha (:runner opts)))
      (is (= ["--focus" "my.ns/test-foo"] (:kaocha-argv opts)))
      (is (not (contains? opts :kaocha-extra)))))
  (testing "repeated forwarded Kaocha options accumulate in order"
    (let [opts (#'cli/parse-main-args ["--runner" "kaocha"
                                       "--focus" "my.ns/test-foo"
                                       "--focus" "my.ns/test-bar"])]
      (is (= ["--focus" "my.ns/test-foo" "--focus" "my.ns/test-bar"]
             (:kaocha-argv opts)))))
  (testing "former --kaocha-opt and arbitrary flags forward as raw :kaocha-argv"
    (let [opts (#'cli/parse-main-args ["--runner" "kaocha"
                                       "--kaocha-opt" "foo" "bar"
                                       "--no-randomize"])]
      (is (= ["--kaocha-opt" "foo" "bar" "--no-randomize"]
             (:kaocha-argv opts)))))
  (testing "a repeated runner flag is rejected as an argument error"
    ;; argv-runner resolves the forward/reject mode from the first occurrence
    ;; while the executed runner would otherwise come from the last; rejecting
    ;; a repeat keeps runner resolution authoritative and consistent.
    (is (argument-error?
         #(#'cli/parse-main-args ["--runner" "kaocha"
                                  "--runner" "clojure-test" "foo"])))
    (is (argument-error?
         #(#'cli/parse-main-args ["-r" "kaocha" "-r" "kaocha"]))))
  (testing "former scry-specific Kaocha flag is rejected in core mode"
    (is (argument-error?
         #(#'cli/parse-main-args ["--runner" "clojure-test"
                                  "--focus" "my.ns/test-foo"]))))
  (testing "positional suite selector rejected in core mode"
    (is (argument-error?
         #(#'cli/parse-main-args ["--runner" "clojure-test"
                                  "foo"]))))
  (testing "former -m Kaocha suite flags now forward to Kaocha verbatim"
    ;; The removed --suite/-s/--suites flags are no longer scry concepts; in
    ;; Kaocha mode they forward verbatim and Kaocha's own parser decides their
    ;; fate (a runner/load error if unknown there), rather than scry rejecting
    ;; them as argument errors.
    (doseq [[args expected] [[["--runner" "kaocha" "--suite" "unit"]
                              ["--suite" "unit"]]
                             [["--runner" "kaocha" "-s" "unit"]
                              ["-s" "unit"]]
                             [["--runner" "kaocha" "--suites" "[:unit]"]
                              ["--suites" "[:unit]"]]]]
      (let [opts (#'cli/parse-main-args args)]
        (is (= expected (:kaocha-argv opts))
            (str "expected verbatim forwarding for " (pr-str args))))))
  (testing "help does not normalize or run"
    (let [parsed (#'cli/parse-main-args ["--help"])]
      (is (= true (:help? parsed)))
      (is (str/includes? (:usage parsed) "Usage:"))))
  (testing "help with no runner lists both modes' options"
    (let [usage (:usage (#'cli/parse-main-args ["--help"]))]
      (is (str/includes? usage "core mode only"))
      (is (str/includes? usage "Kaocha mode only"))
      (is (str/includes? usage "--var"))
      (is (str/includes? usage "--focus"))))
  (testing "help is sensitive to an explicit core --runner"
    (doseq [args [["--runner" "clojure-test" "--help"]
                  ["--help" "--runner" "clojure-test"]
                  ["--runner" "core" "--help"]]]
      (let [usage (:usage (#'cli/parse-main-args args))]
        (is (str/includes? usage "--var")
            (str "expected core options for " (pr-str args)))
        (is (not (str/includes? usage "--focus"))
            (str "expected no Kaocha options for " (pr-str args)))
        (is (not (str/includes? usage "mode only"))
            (str "expected no mode annotations for " (pr-str args))))))
  (testing "help is sensitive to an explicit Kaocha --runner"
    (doseq [args [["--runner" "kaocha" "--help"]
                  ["--help" "--runner" "kaocha"]]]
      (let [usage (:usage (#'cli/parse-main-args args))]
        (is (str/includes? usage "--focus")
            (str "expected Kaocha options for " (pr-str args)))
        (is (str/includes? usage "[SUITE]...")
            (str "expected suite positional docs for " (pr-str args)))
        (is (not (str/includes? usage "--var"))
            (str "expected no core selector options for " (pr-str args)))
        (is (not (str/includes? usage "mode only"))
            (str "expected no mode annotations for " (pr-str args))))))
  (testing "help with an unrecognized runner falls back to general help"
    (let [usage (:usage (#'cli/parse-main-args ["--runner" "bogus" "--help"]))]
      (is (str/includes? usage "core mode only"))
      (is (str/includes? usage "Kaocha mode only"))))
  (testing "parser errors"
    (is (argument-error? #(#'cli/parse-main-args ["--unknown"])))
    (is (argument-error? #(#'cli/parse-main-args ["--focus"])))
    (is (argument-error? #(#'cli/parse-main-args ["--kaocha-opt" "foo"])))
    (is (argument-error? #(#'cli/parse-main-args ["--dir"])))
    (is (argument-error? #(#'cli/parse-main-args ["--result-format" "["])))
    (is (argument-error? #(#'cli/parse-main-args ["--ns-pattern" "a"
                                                  "--namespace-pattern" "b"])))
    (is (argument-error? #(#'cli/parse-main-args ["--namespace-pattern" "a"
                                                  "--namespace-regex" "b"])))))

(defn- temp-dir
  []
  (let [dir (java.nio.file.Files/createTempDirectory "scry-cli-test" (make-array java.nio.file.attribute.FileAttribute 0))]
    (.toFile dir)))

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

(defn- string-writer
  []
  (java.io.StringWriter.))

(defn- run-cli-in
  ([dir opts]
   (run-cli-in dir opts {}))
  ([dir opts boundary-overrides]
   (let [out (string-writer)
         err (string-writer)
         boundary (test-boundary (merge {:cwd (.getPath dir) :out out :err err}
                                        boundary-overrides))
         outcome (#'cli/run-cli opts boundary)]
     (assoc outcome :stdout (str out) :stderr (str err)))))

(defn- result-files
  [dir]
  (->> (.listFiles (io/file dir ".scry-results"))
       (map #(.getName %))
       sort
       vec))

(defn- shell-command
  [& args]
  (let [opts (when (map? (last args)) (last args))
        cmd (if opts (butlast args) args)
        opts (merge {:dir (System/getProperty "user.dir")} opts)
        env (merge (into {} (System/getenv)) (:env opts))
        opts (assoc opts :env env)
        result (apply sh/sh (concat cmd (mapcat identity opts)))]
    (update result :exit int)))

(defn- write-stale-result!
  [dir]
  (let [results-dir (io/file dir ".scry-results")]
    (.mkdirs results-dir)
    (spit (io/file results-dir "stale.edn") "{:stale true}")))

(defn- runner-result
  [entries]
  {:summary (reduce (fn [summary entry]
                      (merge-with + summary (:assertion-summary entry)))
                    {:test (count entries)
                     :pass 0
                     :fail 0
                     :error 0}
                    entries)
   :pass? (not-any? #(contains? #{:fail :error :unknown} (:status %)) entries)
   :canonical-results (vec entries)})

(defn- create-symlink!
  [link target]
  (try
    (java.nio.file.Files/createSymbolicLink
     (.toPath link)
     (.toPath target)
     (make-array java.nio.file.attribute.FileAttribute 0))
    true
    (catch UnsupportedOperationException _ false)
    (catch java.nio.file.FileSystemException _ false)
    (catch java.io.IOException _ false)))

(deftest run-cli-passing-run-test
  ;; A passing CLI run prints live dots, creates an empty results directory,
  ;; prints a summary, and exits successfully.
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.passing/arithmetic-passes]}))]
      (is (= 0 (:exit-code outcome)))
      (is (= :scry.cli/pass (:scry.cli/outcome-kind outcome)))
      (is (= ".Assertions: 2 passed, 0 failed, 0 errored\nTests: 1 passed, 0 failed, 0 errored\n"
             (:stdout outcome)))
      (is (= "" (:stderr outcome)))
      (is (= [] (result-files dir)))
      (is (= {:assertions {:pass 2 :fail 0 :error 0}
              :tests {:pass 1 :fail 0 :error 0 :unknown 0}
              :var-count 1}
             (:summary outcome))))))

(deftest run-cli-results-dir-symlink-cleanup-test
  ;; Run-start cleanup removes a .scry-results symlink itself without
  ;; recursing into or deleting files from the symlink target directory.
  (with-temp-dir [dir]
    (with-temp-dir [target]
      (spit (io/file target "outside.edn") "{:outside true}")
      (let [link (io/file dir ".scry-results")]
        (when (create-symlink! link target)
          (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                         {:vars ['scry.fixtures.passing/arithmetic-passes]}))]
            (is (= 0 (:exit-code outcome)))
            (is (= "{:outside true}" (slurp (io/file target "outside.edn"))))
            (is (false?
                 (java.nio.file.Files/isSymbolicLink (.toPath link))))
            (is (.isDirectory link))
            (is (= [] (result-files dir)))))))))

(def ^:private non-writable-dir-permissions
  #{java.nio.file.attribute.PosixFilePermission/OWNER_READ
    java.nio.file.attribute.PosixFilePermission/OWNER_EXECUTE})

(defn- posix-file-permissions-supported?
  []
  (contains? (.supportedFileAttributeViews
              (java.nio.file.FileSystems/getDefault))
             "posix"))

(deftest run-cli-results-dir-preparation-failure-test
  ;; If the CLI cannot prepare .scry-results, it reports a runner-error
  ;; outcome before invoking the selected test runner.
  (testing "create failure"
    (with-temp-dir [dir]
      (let [cwd-file (io/file dir "not-a-directory")
            runner-called? (atom false)
            out (string-writer)
            err (string-writer)]
        (spit cwd-file "not a directory")
        (let [outcome (#'cli/run-cli
                       (#'cli/normalize-exec-opts {})
                       (test-boundary
                        {:cwd (.getPath cwd-file)
                         :out out
                         :err err
                         :run-clojure-test
                         (fn [_]
                           (reset! runner-called? true)
                           (runner-result [{:var 'scry.fixtures.passing/arithmetic-passes
                                            :ns 'scry.fixtures.passing
                                            :status :pass
                                            :assertion-summary {:pass 1 :fail 0 :error 0}
                                            :assertions []}]))}))]
          (is (= 1 (:exit-code outcome)))
          (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
          (is (= "No tests run — scry CLI error outcome: :scry.cli/runner-error\n"
                 (str out)))
          (is (str/includes? (str err) "scry CLI error: Could not create"))
          (is (= false @runner-called?))
          (is (= nil (:result outcome)))
          (is (= nil (:summary outcome)))
          (is (= [] (:result-files outcome)))
          (is (false? (.exists (io/file cwd-file ".scry-results"))))))))
  (testing "clear/delete failure"
    (if-not (posix-file-permissions-supported?)
      (is true "POSIX file permissions are not supported on this filesystem")
      (with-temp-dir [dir]
        (let [results-dir (io/file dir ".scry-results")
              stale-file (io/file results-dir "stale.edn")
              runner-called? (atom false)
              out (string-writer)
              err (string-writer)]
          (.mkdirs results-dir)
          (spit stale-file "{:stale true}")
          (let [original-permissions (java.nio.file.Files/getPosixFilePermissions
                                      (.toPath results-dir)
                                      (make-array java.nio.file.LinkOption 0))]
            (java.nio.file.Files/setPosixFilePermissions
             (.toPath results-dir)
             non-writable-dir-permissions)
            (try
              (let [outcome (#'cli/run-cli
                             (#'cli/normalize-exec-opts {})
                             (test-boundary
                              {:cwd (.getPath dir)
                               :out out
                               :err err
                               :run-clojure-test
                               (fn [_]
                                 (reset! runner-called? true)
                                 (runner-result [{:var 'scry.fixtures.passing/arithmetic-passes
                                                  :ns 'scry.fixtures.passing
                                                  :status :pass
                                                  :assertion-summary {:pass 1 :fail 0 :error 0}
                                                  :assertions []}]))}))]
                (is (= 1 (:exit-code outcome)))
                (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
                (is (= "No tests run — scry CLI error outcome: :scry.cli/runner-error\n"
                       (str out)))
                (is (str/includes? (str err) "scry CLI error: Could not delete"))
                (is (= false @runner-called?))
                (is (= nil (:result outcome)))
                (is (= nil (:summary outcome)))
                (is (= [] (:result-files outcome)))
                (is (.exists stale-file)))
              (finally
                (java.nio.file.Files/setPosixFilePermissions
                 (.toPath results-dir)
                 original-permissions)))))))))

(deftest run-cli-successful-core-selectors-test
  ;; Successful namespace and directory/ns-pattern selectors run end-to-end,
  ;; verifying documented core CLI selectors beyond explicit vars.
  (testing "namespace selector"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                     {:namespaces ['scry.fixtures.passing]}))]
        (is (= 0 (:exit-code outcome)))
        (is (= ".Assertions: 2 passed, 0 failed, 0 errored\nTests: 1 passed, 0 failed, 0 errored\n"
               (:stdout outcome)))
        (is (= "" (:stderr outcome)))
        (is (= [] (result-files dir)))
        (is (= ['scry.fixtures.passing/arithmetic-passes]
               (mapv :var (:canonical-results (:result outcome))))))))
  (testing "directory plus namespace-pattern discovery selector"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                     {:dirs ["test"]
                                      :ns-pattern #"scry\.fixtures\.passing"}))]
        (is (= 0 (:exit-code outcome)))
        (is (= ".Assertions: 2 passed, 0 failed, 0 errored\nTests: 1 passed, 0 failed, 0 errored\n"
               (:stdout outcome)))
        (is (= "" (:stderr outcome)))
        (is (= [] (result-files dir)))
        (is (= ['scry.fixtures.passing/arithmetic-passes]
               (mapv :var (:canonical-results (:result outcome)))))))))

(deftest result-file-assignments-synthetic-entries-test
  ;; Synthetic suite-level entries without concrete vars receive deterministic
  ;; result-file names while var-backed names remain unchanged.
  (testing "non-concrete var shapes and optional namespace prefixes"
    (let [entries [{:var 'scry.fixtures.failing/equality-fails
                    :status :fail}
                   {:var nil
                    :status :error}
                   {:status :fail}
                   {:var 'not-qualified
                    :ns 'loader.demo
                    :status :error}]
          assignments (cli-results/result-file-assignments entries)]
      (is (= ["scry.fixtures.failing__equality-fails.edn"
              "suite-error-1.edn"
              "suite-fail-1.edn"
              "loader.demo__suite-error-2.edn"]
             (mapv :filename assignments)))
      (is (= entries (mapv :entry assignments)))))
  (testing "synthetic filenames avoid var-backed collisions"
    (let [entries [{:var 'loader.demo/suite-error-1
                    :status :pass}
                   {:var 'not-qualified
                    :ns 'loader.demo
                    :status :error}]
          assignments (cli-results/result-file-assignments entries)]
      (is (= ["loader.demo__suite-error-1--2.edn"]
             (mapv :filename assignments)))))
  (testing "synthetic filename collisions advance past reserved suffixes"
    (let [entries [{:var 'loader.demo/suite-error-1
                    :status :pass}
                   {:var 'loader.demo/suite-error-1--2
                    :status :pass}
                   {:var nil
                    :ns 'loader.demo
                    :status :error}]
          assignments (cli-results/result-file-assignments entries)]
      (is (= ["loader.demo__suite-error-1--3.edn"]
             (mapv :filename assignments))))))

(deftest run-cli-synthetic-nil-var-results-test
  ;; Synthetic nil/absent/non-concrete entries write readable result files and
  ;; print useful progress labels instead of deriving names from nil vars.
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [synthetic-error {:var nil
                           :ns 'loader.demo
                           :status :error
                           :assertion-summary {:pass 0 :fail 0 :error 1}
                           :assertions [{:type :error
                                         :message "could not load tests"}]
                           :out "load out\n"
                           :err "load err\n"}
          synthetic-fail {:status :fail
                          :assertion-summary {:pass 0 :fail 1 :error 0}
                          :assertions [{:type :fail
                                        :message "suite failed"}]
                          :out ""
                          :err ""}
          synthetic-unknown {:var 'not-qualified
                             :ns 'loader.demo
                             :status :unknown
                             :assertion-summary {:pass 0 :fail 0 :error 0}
                             :assertions []
                             :out ""
                             :err ""}
          outcome (run-cli-in dir
                              (#'cli/normalize-exec-opts {})
                              {:run-clojure-test
                               (fn [opts]
                                 (doseq [entry [synthetic-error
                                                synthetic-fail
                                                synthetic-unknown]]
                                   ((:progress-callback opts) entry))
                                 (runner-result [synthetic-error
                                                 synthetic-fail
                                                 synthetic-unknown]))})
          files (result-files dir)
          error-data (edn/read-string
                      (slurp (io/file dir ".scry-results"
                                      "loader.demo__suite-error-1.edn")))
          fail-data (edn/read-string
                     (slurp (io/file dir ".scry-results"
                                     "suite-fail-1.edn")))]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/load-error (:scry.cli/outcome-kind outcome)))
      (is (= "Assertions: 0 passed, 1 failed, 1 errored\nTests: 0 passed, 1 failed, 1 errored, 1 unknown\n"
             (:stdout outcome)))
      ;; Live progress labels stream first, then the failure diagnostic. The
      ;; outcome is a load error, so the diagnostic includes the assertion
      ;; message and a pointer at the results directory.
      (is (str/starts-with?
           (:stderr outcome)
           "loader.demo/suite-error-1\nsuite-fail-1\nloader.demo/suite-unknown-1\n"))
      (is (str/includes? (:stderr outcome) "Load error: could not load tests"))
      (is (str/includes? (:stderr outcome) "for failure details"))
      (is (= ["loader.demo__suite-error-1.edn" "suite-fail-1.edn"] files))
      (is (= nil (:var error-data)))
      (is (= 'loader.demo (:ns error-data)))
      (is (= :error (:status error-data)))
      (is (= :fail (:status fail-data))))))

(deftest run-cli-load-error-stderr-diagnostic-test
  ;; A load/suite error surfaces an inline stderr diagnostic: the failing
  ;; assertion message plus its root-cause class/message, and a pointer at the
  ;; results directory. stdout still receives only the terse summary, the exit
  ;; code/outcome-kind are unchanged, and the synthetic result file is written.
  (with-temp-dir [dir]
    (let [root-cause (RuntimeException.
                      "Unable to resolve symbol: this-does-not-exist in this context")
          load-failure (ex-info "compile failed" {} root-cause)
          synthetic-error {:var nil
                           :ns nil
                           :status :error
                           :assertion-summary {:pass 0 :fail 0 :error 1}
                           :assertions [{:type :error
                                         :message "Failed loading tests:"
                                         :expected nil
                                         :actual load-failure}]
                           :out ""
                           :err ""}
          outcome (run-cli-in dir
                              (#'cli/normalize-exec-opts {})
                              {:run-clojure-test
                               (fn [opts]
                                 ((:progress-callback opts) synthetic-error)
                                 (runner-result [synthetic-error]))})
          stdout (:stdout outcome)
          stderr (:stderr outcome)]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/load-error (:scry.cli/outcome-kind outcome)))
      ;; stdout is the terse summary only (no diagnostic leakage).
      (is (= "Assertions: 0 passed, 0 failed, 1 errored\nTests: 0 passed, 0 failed, 1 errored\n"
             stdout))
      ;; stderr carries the inline cause and a pointer at the results dir.
      (is (str/includes? stderr "Load error: Failed loading tests:"))
      (is (str/includes? stderr "java.lang.RuntimeException"))
      (is (str/includes? stderr
                         "Unable to resolve symbol: this-does-not-exist in this context"))
      (is (str/includes? stderr (.getPath (io/file (.getPath dir) ".scry-results"))))
      (is (str/includes? stderr "for failure details"))
      (is (= ["suite-error-1.edn"] (result-files dir))))))

(deftest run-cli-failing-run-test
  ;; A failing CLI run prints unqualified failing names to stderr, writes
  ;; detailed EDN files, removes stale files, and exits non-zero.
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.failing/also-passes
                                           'scry.fixtures.failing/equality-fails]}))
          files (result-files dir)
          failure-file (io/file dir ".scry-results" "scry.fixtures.failing__equality-fails.edn")
          failure-data (edn/read-string (slurp failure-file))]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
      (is (= ".Assertions: 1 passed, 1 failed, 0 errored\nTests: 1 passed, 1 failed, 0 errored\n"
             (:stdout outcome)))
      (is (str/starts-with? (:stderr outcome) "equality-fails\n"))
      (is (str/includes? (:stderr outcome) "for failure details"))
      (is (= ["scry.fixtures.failing__equality-fails.edn"] files))
      (is (= ['scry.fixtures.failing/equality-fails]
             (map :var (filter #(= :fail (:status %)) (:canonical-results (:result outcome))))))
      (is (= 'scry.fixtures.failing/equality-fails (:var failure-data)))
      (is (= 'scry.fixtures.failing (:ns failure-data)))
      (is (= :fail (:status failure-data)))
      (is (= {:pass 0 :fail 1 :error 0} (:assertion-summary failure-data)))
      (is (= ["outer context" "inner context"]
             (:contexts (first (:assertions failure-data)))))
      (is (contains? failure-data :out))
      (is (contains? failure-data :err)))))

(deftest run-cli-background-output-does-not-abort-run-test
  ;; Regression: a failing namespace whose tests emit output from a non-owned
  ;; background thread (mirroring the downstream native/JNI case) must still
  ;; complete with a normal failing summary, not collapse the whole run into
  ;; :scry.cli/runner-error with an empty diagnostic.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in
                   dir
                   (#'cli/normalize-exec-opts
                    {:namespaces ['scry.fixtures.background-output]}))
          statuses (->> (:canonical-results (:result outcome))
                        (map (juxt :var :status))
                        (into {}))]
      (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
      (is (not= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
      (is (= 1 (:exit-code outcome)))
      (is (some? (:summary outcome)))
      (is (= :pass
             (statuses 'scry.fixtures.background-output/passing-with-unowned-background-output)))
      (is (= :fail
             (statuses 'scry.fixtures.background-output/failing-with-unowned-background-output))))))

(deftest run-cli-runner-error-diagnostic-is-non-empty-test
  ;; A genuine runner-level error must surface a non-empty diagnostic carrying
  ;; the underlying exception, even when the Throwable's own message is blank.
  (with-temp-dir [dir]
    (with-redefs [clojure-test/run (fn [& _]
                                     (throw (Exception.)))]
      (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                     {:namespaces ['scry.fixtures.passing]}))]
        (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
        (is (seq (get-in outcome [:error :message])))
        (is (instance? Throwable (get-in outcome [:error :exception])))
        (is (str/includes? (:stderr outcome) "scry CLI error: "))
        (is (not (str/includes? (:stderr outcome) "scry CLI error: \n")))))))

(deftest run-cli-error-result-files-are-readable-edn-test
  ;; Error result files sanitize raw Throwables so clojure.edn/read-string can
  ;; read the file while preserving exception class/message/stacktrace detail.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.erroring/throws-exception]}))
          error-file (io/file dir ".scry-results" "scry.fixtures.erroring__throws-exception.edn")
          error-data (edn/read-string (slurp error-file))
          assertion (first (:assertions error-data))]
      (is (= 1 (:exit-code outcome)))
      (is (str/starts-with? (:stderr outcome) "throws-exception\n"))
      (is (str/includes? (:stderr outcome) "for failure details"))
      (is (= ["scry.fixtures.erroring__throws-exception.edn"] (result-files dir)))
      (is (= 'scry.fixtures.erroring/throws-exception (:var error-data)))
      (is (= :error (:status error-data)))
      (is (= :error (:type assertion)))
      (is (= 'java.lang.ArithmeticException (get-in assertion [:actual :type])))
      (is (str/includes? (-> assertion :actual :type str)
                         "ArithmeticException"))
      (is (vector? (get-in assertion [:actual :trace])))
      (is (str/includes? (:stacktrace assertion)
                         "java.lang.ArithmeticException")))))

(deftest run-cli-mixed-fail-and-error-status-test
  ;; A test var with both failure and error assertions is treated as errored
  ;; throughout the CLI contract: one stderr progress name, one result file,
  ;; errored var summary, and non-zero exit.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.mixed/fail-then-error]}))
          error-file (io/file dir ".scry-results" "scry.fixtures.mixed__fail-then-error.edn")
          error-data (edn/read-string (slurp error-file))]
      (is (= 1 (:exit-code outcome)))
      (is (= "Assertions: 0 passed, 1 failed, 1 errored\nTests: 0 passed, 0 failed, 1 errored\n"
             (:stdout outcome)))
      (is (str/starts-with? (:stderr outcome) "fail-then-error\n"))
      (is (str/includes? (:stderr outcome) "for failure details"))
      (is (= ["scry.fixtures.mixed__fail-then-error.edn"] (result-files dir)))
      (is (= {:assertions {:pass 0 :fail 1 :error 1}
              :tests {:pass 0 :fail 0 :error 1 :unknown 0}
              :var-count 1}
             (:summary outcome)))
      (is (= 'scry.fixtures.mixed/fail-then-error (:var error-data)))
      (is (= :error (:status error-data)))
      (is (= {:pass 0 :fail 1 :error 1} (:assertion-summary error-data)))
      (is (= [:fail :error] (mapv :type (:assertions error-data))))
      (is (= [:error]
             (mapv :status (:canonical-results (:result outcome))))))))

(deftest run-cli-arbitrary-object-result-files-are-readable-edn-test
  ;; Failure result files recursively sanitize arbitrary objects captured by
  ;; the real clojure.test runner so clojure.edn/read-string can read the file
  ;; without #object forms.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.arbitrary/arbitrary-object-fails]}))
          failure-file (io/file dir ".scry-results" "scry.fixtures.arbitrary__arbitrary-object-fails.edn")
          failure-data (edn/read-string (slurp failure-file))
          assertion (first (:assertions failure-data))]
      (is (= 1 (:exit-code outcome)))
      (is (str/starts-with? (:stderr outcome) "arbitrary-object-fails\n"))
      (is (str/includes? (:stderr outcome) "for failure details"))
      (is (= ["scry.fixtures.arbitrary__arbitrary-object-fails.edn"] (result-files dir)))
      (is (= 'scry.fixtures.arbitrary/arbitrary-object-fails (:var failure-data)))
      (is (= :fail (:status failure-data)))
      (let [object-leaves (filter #(and (map? %)
                                        (= "java.lang.Object" (:scry/non-edn-class %)))
                                  (tree-seq coll? seq assertion))]
        (is (= 2 (count object-leaves)))
        (is (every? #(string? (:str %)) object-leaves))))))

(deftest edn-readable-data-bounds-pathological-values-test
  ;; Sanitizer output is bounded, readable EDN for cyclic data, long sequences,
  ;; deep structures, non-EDN objects, and Throwables with cyclic ex-data.
  (testing "cycles, depth, sequence length, shared identities, and arbitrary objects"
    (let [m (java.util.HashMap.)]
      (.put m "self" m)
      (is (= {"self" {:scry/cycle true :class "java.util.HashMap"}}
             (cli-results/edn-readable-data m {:max-depth 4}))))
    (is (= [0 1 {:scry/truncated :max-seq-length}]
           (cli-results/edn-readable-data (range 5) {:max-seq-length 2})))
    (is (= {:a {:b {{:scry/truncated :max-depth} {:scry/truncated :max-depth}}}}
           (cli-results/edn-readable-data {:a {:b {:c 1}}} {:max-depth 2})))
    (let [shared (java.util.HashMap.)]
      (.put shared "value" 1)
      (is (= {"left" {"value" 1}
              "right" {"value" 1}}
             (cli-results/edn-readable-data {"left" shared
                                             "right" shared}))))
    (is (= "java.lang.Object"
           (:scry/non-edn-class (cli-results/edn-readable-data (Object.)))))
    (let [hostile-string (apply str (repeat 20 "x"))
          hostile-object (proxy [Object] []
                           (toString [] hostile-string))
          sanitized (cli-results/edn-readable-data hostile-object
                                                   {:max-string-length 5})]
      (is (str/includes? (:scry/non-edn-class sanitized) "proxy"))
      (is (str/starts-with? (:str sanitized) "xxxxx…"))
      (is (str/includes? (:str sanitized) ":max-string-length"))))
  (testing "throwables use controlled bounded shape"
    (let [data (java.util.IdentityHashMap.)
          _ (.put data :self data)
          cause (ex-info "root" {:cyclic data})
          error (ex-info "boom" {} cause)
          sanitized (cli-results/edn-readable-data error {:max-stack-frames 2})]
      (is (= 'clojure.lang.ExceptionInfo (:type sanitized)))
      (is (= "boom" (:message sanitized)))
      (is (= 'clojure.lang.ExceptionInfo (get-in sanitized [:cause :type])))
      (is (= "root" (get-in sanitized [:cause :message])))
      (is (<= (count (:trace sanitized)) 2))
      (is (= {:scry/cycle true :class "java.util.IdentityHashMap"}
             (get-in sanitized [:cause :data :cyclic :self])))))
  (testing "repeated shared Throwable identities in separate branches are not cycles"
    (let [shared (ex-info "shared" {})]
      (is (= {:left {:type 'clojure.lang.ExceptionInfo
                     :message "shared"}
              :right {:type 'clojure.lang.ExceptionInfo
                      :message "shared"}}
             (-> (cli-results/edn-readable-data {:left shared :right shared}
                                                {:max-stack-frames 0})
                 (update :left select-keys [:type :message])
                 (update :right select-keys [:type :message]))))))
  (testing "Throwable cause depth is explicitly bounded"
    (let [root (RuntimeException. "root")
          middle (RuntimeException. "middle" root)
          top (RuntimeException. "top" middle)
          sanitized (cli-results/edn-readable-data top {:max-throwable-depth 1
                                                        :max-stack-frames 0})]
      (is (= 'java.lang.RuntimeException (:type sanitized)))
      (is (= "top" (:message sanitized)))
      (is (= {:scry/truncated :throwable-cause-depth}
             (:cause sanitized)))))
  (testing "Throwable suppressed exceptions are capped"
    (let [top (RuntimeException. "top")]
      (.addSuppressed top (RuntimeException. "suppressed-1"))
      (.addSuppressed top (RuntimeException. "suppressed-2"))
      (let [sanitized (cli-results/edn-readable-data top {:max-suppressed 1
                                                          :max-stack-frames 0})]
        (is (= 1 (count (:suppressed sanitized))))
        (is (= "suppressed-1" (get-in sanitized [:suppressed 0 :message]))))))
  (testing "Throwable ex-data depth is bounded independently"
    (let [error (ex-info "boom" {:outer {:inner {:leaf :value}}})
          sanitized (cli-results/edn-readable-data error {:max-depth 20
                                                          :max-ex-data-depth 1
                                                          :max-stack-frames 0})]
      (is (= {:outer {{:scry/truncated :max-depth}
                      {:scry/truncated :max-depth}}}
             (:data sanitized))))))

(deftest edn-readable-data-max-seq-length-sentinels-test
  ;; Every supported collection family emits an explicit truncation sentinel
  ;; instead of silently dropping excess values.
  (testing "sequential and vector collections"
    (is (= [0 1 {:scry/truncated :max-seq-length}]
           (cli-results/edn-readable-data (range 5) {:max-seq-length 2})))
    (is (= [0 1 {:scry/truncated :max-seq-length}]
           (cli-results/edn-readable-data [0 1 2 3] {:max-seq-length 2}))))
  (testing "sets"
    (let [sanitized (cli-results/edn-readable-data (apply sorted-set [0 1 2])
                                                   {:max-seq-length 2})]
      (is (= 3 (count sanitized)))
      (is (contains? sanitized {:scry/truncated :max-seq-length}))))
  (testing "persistent and Java maps"
    (is (= {0 0 1 1
            {:scry/truncated :max-seq-length} {:scry/truncated :max-seq-length}}
           (cli-results/edn-readable-data (array-map 0 0 1 1 2 2)
                                          {:max-seq-length 2})))
    (let [m (java.util.LinkedHashMap.)]
      (.put m 0 0)
      (.put m 1 1)
      (.put m 2 2)
      (is (= {0 0 1 1
              {:scry/truncated :max-seq-length} {:scry/truncated :max-seq-length}}
             (cli-results/edn-readable-data m {:max-seq-length 2})))))
  (testing "arrays and Iterable values"
    (is (= [0 1 {:scry/truncated :max-seq-length}]
           (cli-results/edn-readable-data (int-array [0 1 2])
                                          {:max-seq-length 2})))
    (let [values (java.util.ArrayList. [0 1 2])]
      (is (= [0 1 {:scry/truncated :max-seq-length}]
             (cli-results/edn-readable-data values {:max-seq-length 2}))))))

(deftest edn-readable-data-throwable-frame-shape-test
  ;; Throwable frame data is a bounded map shape for both :at and :trace.
  (let [error (RuntimeException. "boom")
        sanitized (cli-results/edn-readable-data error {:max-stack-frames 1})
        frame-keys #{:class :method :file :line}]
    (is (= frame-keys (set (keys (:at sanitized)))))
    (is (string? (:class (:at sanitized))))
    (is (string? (:method (:at sanitized))))
    (is (or (nil? (:file (:at sanitized)))
            (string? (:file (:at sanitized)))))
    (is (integer? (:line (:at sanitized))))
    (is (= 1 (count (:trace sanitized))))
    (is (= frame-keys (set (keys (first (:trace sanitized))))))))

(deftest edn-readable-data-cyclic-throwable-cause-chain-test
  ;; Throwable cause cycles use the controlled cycle placeholder instead of
  ;; relying on incidental cause-depth truncation or recursing forever.
  (let [a (RuntimeException. "a")
        b (RuntimeException. "b")]
    (.initCause a b)
    (.initCause b a)
    (let [sanitized (cli-results/edn-readable-data a {:max-throwable-depth 10
                                                      :max-stack-frames 0})]
      (is (= "a" (:message sanitized)))
      (is (= "b" (get-in sanitized [:cause :message])))
      (is (= {:scry/cycle true :class "java.lang.RuntimeException"}
             (get-in sanitized [:cause :cause]))))))

(deftest edn-readable-data-truncation-sentinel-collisions-test
  ;; If user data already equals the explicit max-seq-length sentinel, the
  ;; sentinel remains observable after truncation for set and map shapes.
  (let [sentinel {:scry/truncated :max-seq-length}]
    (testing "persistent sets containing the sentinel outside the retained prefix still expose it after truncation"
      (let [rank {1 0, 2 1, sentinel 2}
            values (into (sorted-set-by (fn [a b]
                                          (compare (rank a) (rank b))))
                         [sentinel 2 1])
            sanitized (cli-results/edn-readable-data values {:max-seq-length 2})]
        (is (= 3 (count sanitized)))
        (is (some #(= sentinel %) sanitized))))
    (testing "generic Iterable sets containing the sentinel outside the retained prefix still expose it after truncation"
      (let [values (doto (java.util.LinkedHashSet.)
                     (.add 1)
                     (.add 2)
                     (.add sentinel))
            sanitized (cli-results/edn-readable-data values {:max-seq-length 2})]
        (is (= 3 (count sanitized)))
        (is (some #(= sentinel %) sanitized))))
    (testing "maps containing the sentinel key still expose the sentinel entry after truncation"
      (is (= {sentinel sentinel
              :kept :value}
             (cli-results/edn-readable-data (array-map sentinel :user
                                                       :kept :value
                                                       :dropped :value)
                                            {:max-seq-length 2}))))))

(deftest edn-readable-data-hostile-collection-boundaries-test
  ;; Hostile collection implementations fall back to bounded placeholders
  ;; instead of escaping sanitizer traversal as unstructured write failures.
  (testing "Java Map whose entrySet throws"
    (let [value (proxy [java.util.AbstractMap] []
                  (entrySet []
                    (throw (RuntimeException. "entrySet exploded"))))
          sanitized (cli-results/edn-readable-data value {:max-string-length 80})]
      (is (str/includes? (:scry/non-edn-class sanitized) "proxy"))
      (is (string? (:str sanitized)))))
  (testing "Iterable whose iterator throws"
    (let [value (proxy [Iterable] []
                  (iterator []
                    (throw (RuntimeException. "iterator exploded"))))
          sanitized (cli-results/edn-readable-data value {:max-string-length 80})]
      (is (str/includes? (:scry/non-edn-class sanitized) "proxy"))
      (is (string? (:str sanitized)))))
  (testing "Java Map whose entry access throws"
    (let [entry (proxy [java.util.Map$Entry] []
                  (getKey []
                    (throw (RuntimeException. "key exploded")))
                  (getValue [] :value)
                  (setValue [_] nil))
          value (proxy [java.util.AbstractMap] []
                  (entrySet []
                    (java.util.Collections/singleton entry)))
          sanitized (cli-results/edn-readable-data value {:max-string-length 80})]
      (is (str/includes? (:scry/non-edn-class sanitized) "proxy"))
      (is (string? (:str sanitized)))))
  (testing "one-shot Iterable is traversed once while detecting truncation"
    (let [used? (atom false)
          value (proxy [Iterable] []
                  (iterator []
                    (if (compare-and-set! used? false true)
                      (.iterator [0 1 2])
                      (throw (RuntimeException. "iterator reused")))))
          sanitized (cli-results/edn-readable-data value {:max-seq-length 2})]
      (is (= [0 1 {:scry/truncated :max-seq-length}] sanitized)))))

(deftest edn-readable-data-hostile-throwable-boundaries-test
  ;; Hostile Throwable accessors are represented as bounded placeholders inside
  ;; the controlled Throwable shape instead of escaping to diagnostic fallback.
  (testing "message, cause, and stack-trace accessors throwing"
    (let [value (proxy [RuntimeException] ["outer"]
                  (getMessage []
                    (throw (RuntimeException. "message exploded")))
                  (getCause []
                    (throw (RuntimeException. "cause exploded")))
                  (getStackTrace []
                    (throw (RuntimeException. "stack exploded"))))
          sanitized (cli-results/edn-readable-data value {:max-string-length 80})]
      (is (str/includes? (str (:type sanitized)) "proxy"))
      (is (= "java.lang.RuntimeException"
             (get-in sanitized [:message :scry/non-edn-class])))
      (is (= "java.lang.RuntimeException"
             (get-in sanitized [:cause :scry/non-edn-class])))
      (is (= "java.lang.RuntimeException"
             (get-in sanitized [:trace :scry/non-edn-class])))
      (is (= "java.lang.RuntimeException"
             (get-in sanitized [:at :scry/non-edn-class])))))
  (testing "ex-data access throwing"
    (let [value (proxy [RuntimeException clojure.lang.IExceptionInfo] ["outer"]
                  (getData []
                    (throw (RuntimeException. "data exploded"))))
          sanitized (cli-results/edn-readable-data value {:max-string-length 80})]
      (is (= "java.lang.RuntimeException"
             (get-in sanitized [:data :scry/non-edn-class])))
      (is (str/includes? (get-in sanitized [:data :str]) "data exploded")))))

(deftest run-cli-pathological-failures-keep-test-outcome-test
  ;; Pathological cyclic assertion and Throwable data must not turn a test
  ;; failure into a runner-level StackOverflowError.
  (with-temp-dir [dir]
    (let [cyclic-map (java.util.HashMap.)
          cyclic-data (java.util.IdentityHashMap.)
          _ (.put cyclic-map "self" cyclic-map)
          _ (.put cyclic-data :self cyclic-data)
          entries [{:var 'scry.fixtures.pathological/cyclic-failure-actual-does-not-crash-cli
                    :ns 'scry.fixtures.pathological
                    :status :fail
                    :assertion-summary {:pass 0 :fail 1 :error 0}
                    :assertions [{:type :fail :expected {} :actual cyclic-map}]}
                   {:var 'scry.fixtures.pathological/throwable-with-cyclic-ex-data-does-not-crash-cli
                    :ns 'scry.fixtures.pathological
                    :status :error
                    :assertion-summary {:pass 0 :fail 0 :error 1}
                    :assertions [{:type :error
                                  :actual (ex-info "boom" {:cyclic cyclic-data})}]}]
          outcome (run-cli-in dir (#'cli/normalize-exec-opts {})
                              {:run-clojure-test (fn [_] (runner-result entries))})
          files (result-files dir)
          data (mapv #(edn/read-string (slurp (io/file dir ".scry-results" %))) files)]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
      (is (str/includes? (:stdout outcome) "Assertions:"))
      (is (not (str/includes? (:stderr outcome) "StackOverflowError")))
      (is (= #{"scry.fixtures.pathological__cyclic-failure-actual-does-not-crash-cli.edn"
               "scry.fixtures.pathological__throwable-with-cyclic-ex-data-does-not-crash-cli.edn"}
             (set files)))
      (is (some #(= {:scry/cycle true :class "java.util.HashMap"} %)
                (mapcat #(tree-seq coll? seq %) data)))
      (is (some #(= {:scry/cycle true :class "java.util.IdentityHashMap"} %)
                (mapcat #(tree-seq coll? seq %) data)))
      (is (some #(= "boom" %) (mapcat #(tree-seq coll? seq %) data))))))

(defn- assert-pathological-cli-outcome
  [outcome files data stdout stderr]
  (let [flattened (mapcat #(tree-seq coll? seq %) data)]
    (is (= 1 (:exit-code outcome)))
    (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
    (is (= {:pass 0 :fail 1 :error 1}
           (get-in outcome [:summary :assertions])))
    (is (= {:pass 0 :fail 1 :error 1 :unknown 0}
           (get-in outcome [:summary :tests])))
    (is (= 2 (get-in outcome [:summary :var-count])))
    (is (= #{{:scry/cycle true :class "java.util.HashMap"}
             {:scry/cycle true :class "java.util.IdentityHashMap"}}
           (->> flattened
                (filter #(and (map? %)
                              (:scry/cycle %)))
                set)))
    (is (some #(= "boom" %) flattened))
    (is (= #{"scry.fixtures.pathological__cyclic-failure-actual-does-not-crash-cli.edn"
             "scry.fixtures.pathological__throwable-with-cyclic-ex-data-does-not-crash-cli.edn"}
           (set files)))
    (is (str/includes? stdout "Assertions:"))
    (is (not (str/includes? stderr "StackOverflowError")))))

(deftest run-cli-pathological-fixtures-through-real-runner-test
  ;; Exercise the pathological fixtures through the real clojure-test runner so
  ;; capture, canonical result construction, CLI classification, and result-file
  ;; serialization are proven safe together.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:namespaces ['scry.fixtures.pathological]}))
          files (result-files dir)
          data (mapv #(edn/read-string (slurp (io/file dir ".scry-results" %))) files)]
      (assert-pathological-cli-outcome outcome files data (:stdout outcome) (:stderr outcome)))))

(deftest main-cli-pathological-fixtures-subprocess-test
  ;; Exercise the actual `-m scry.cli` entrypoint in a subprocess. This proves
  ;; the pathological namespace exits non-zero, prints the normal summary, and
  ;; writes structured failure files without surfacing StackOverflowError as the
  ;; primary failure.
  (with-temp-dir [dir]
    (let [project-dir (System/getProperty "user.dir")
          classpath (-> (shell-command "clojure" "-Spath" "-A:test") :out str/trim (str/replace #"^test:" (str project-dir "/test:")) (str/replace #":src:" (str ":" project-dir "/src:")))
          result (shell-command "java" "-cp" classpath "clojure.main" "-m" "scry.cli"
                                "--dir" (str project-dir "/test")
                                "--namespace" "scry.fixtures.pathological"
                                {:dir (.getPath dir)})
          files (result-files dir)
          data (mapv #(edn/read-string (slurp (io/file dir ".scry-results" %))) files)
          outcome {:exit-code (:exit result)
                   :scry.cli/outcome-kind :scry.cli/test-failure
                   :summary {:assertions {:pass 0 :fail 1 :error 1}
                             :tests {:pass 0 :fail 1 :error 1 :unknown 0}
                             :var-count 2}}]
      (is (= 1 (:exit result)))
      (assert-pathological-cli-outcome outcome files data (:out result) (:err result))
      (is (str/includes? (:err result) "for failure details")))))

(deftest cli-diagnostic-fallback-is-bounded-and-cycle-safe-test
  ;; Fallback diagnostics must not recurse forever or emit unbounded strings when
  ;; result-file serialization itself fails or the first failing assertion is
  ;; pathological.
  (testing "root-cause helpers tolerate cyclic and deep cause chains"
    (let [cyclic (proxy [RuntimeException] ["cycle"]
                   (getCause [] this))
          deep (reduce (fn [cause n]
                         (RuntimeException. (str "cause-" n) cause))
                       (RuntimeException. "root")
                       (range 20))]
      (is (str/includes? (#'cli/throwable-cause-text cyclic) "cycle"))
      (is (str/includes? (#'cli/throwable-cause-text deep) "cause-"))))
  (testing "diagnostic-error and stderr fallback bound hostile strings"
    (with-temp-dir [dir]
      (let [long-message (apply str (repeat 21000 "x"))
            cyclic (proxy [RuntimeException] [long-message]
                     (getCause [] this))
            entries [{:var 'scry.fixtures.pathological/bounded-diagnostic
                      :ns 'scry.fixtures.pathological
                      :status :error
                      :assertion-summary {:pass 0 :fail 0 :error 1}
                      :assertions [{:type :error
                                    :message long-message
                                    :actual cyclic}]}]
            out (string-writer)
            err (string-writer)]
        (let [outcome (#'cli/run-cli
                       (#'cli/normalize-exec-opts {})
                       (test-boundary {:cwd (.getPath dir)
                                       :out out
                                       :err err
                                       :write-result-files (fn [& _]
                                                             (throw cyclic))
                                       :run-clojure-test (fn [_]
                                                           (runner-result entries))}))
              diagnostic (:scry.cli/diagnostic-error outcome)
              stderr (str err)]
          (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
          (is (<= (count (:message diagnostic)) 20100))
          (is (<= (count (:root-message diagnostic)) 20100))
          (is (<= (count (:first-root-cause diagnostic)) 20100))
          (is (str/includes? (:first-root-cause diagnostic) ":max-string-length"))
          (is (<= (count stderr) 20500))
          (is (str/includes? stderr ":max-string-length"))))))
  (testing "fallback diagnostics tolerate throwing toString values"
    (with-temp-dir [dir]
      (let [hostile (proxy [Object] []
                      (toString []
                        (throw (RuntimeException. "toString exploded"))))
            entries [{:var 'scry.fixtures.pathological/hostile-diagnostic
                      :ns 'scry.fixtures.pathological
                      :status :error
                      :assertion-summary {:pass 0 :fail 0 :error 1}
                      :assertions [{:type :error
                                    :message hostile
                                    :actual {:via [{:type 'pathological.Root
                                                    :message hostile}]
                                             :cause hostile}}]}]
            out (string-writer)
            err (string-writer)]
        (let [outcome (#'cli/run-cli
                       (#'cli/normalize-exec-opts {})
                       (test-boundary {:cwd (.getPath dir)
                                       :out out
                                       :err err
                                       :write-result-files (fn [& _]
                                                             (throw (ex-info "write exploded" {})))
                                       :run-clojure-test (fn [_]
                                                           (runner-result entries))}))
              diagnostic (:scry.cli/diagnostic-error outcome)
              stderr (str err)]
          (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
          (is (= 'scry.fixtures.pathological/hostile-diagnostic
                 (:first-failing-var diagnostic)))
          (is (str/includes? (:first-root-cause diagnostic) "<unprintable"))
          (is (str/includes? stderr "<unprintable"))
          (is (not (str/includes? stderr "runner-error")))))))
  (testing "fallback diagnostics tolerate Throwable actual accessors that throw"
    (with-temp-dir [dir]
      (let [hostile (proxy [RuntimeException] ["outer"]
                      (getMessage []
                        (throw (RuntimeException. "message exploded")))
                      (getCause []
                        (throw (RuntimeException. "cause exploded"))))
            entries [{:var 'scry.fixtures.pathological/hostile-throwable-diagnostic
                      :ns 'scry.fixtures.pathological
                      :status :error
                      :assertion-summary {:pass 0 :fail 0 :error 1}
                      :assertions [{:type :error
                                    :message "outer"
                                    :actual hostile}]}]
            out (string-writer)
            err (string-writer)]
        (let [outcome (#'cli/run-cli
                       (#'cli/normalize-exec-opts {})
                       (test-boundary {:cwd (.getPath dir)
                                       :out out
                                       :err err
                                       :write-result-files (fn [& _]
                                                             (throw (ex-info "write exploded" {})))
                                       :run-clojure-test (fn [_]
                                                           (runner-result entries))}))
              diagnostic (:scry.cli/diagnostic-error outcome)
              stderr (str err)]
          (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
          (is (= 'scry.fixtures.pathological/hostile-throwable-diagnostic
                 (:first-failing-var diagnostic)))
          (is (str/includes? (:first-root-cause diagnostic)
                             "<unavailable message: java.lang.RuntimeException>"))
          (is (str/includes? stderr "First root cause:"))
          (is (not (str/includes? stderr "runner-error"))))))))

(deftest map-shaped-assertion-actual-via-is-bounded-test
  (testing "map-shaped assertion actual via is bounded and tolerant"
    (with-temp-dir [dir]
      (let [long-message (apply str (repeat 21000 "y"))
            cyclic-via (cycle [{:type 'pathological.Root
                                :message long-message}])
            entries [{:var 'scry.fixtures.pathological/map-shaped-actual
                      :ns 'scry.fixtures.pathological
                      :status :error
                      :assertion-summary {:pass 0 :fail 0 :error 1}
                      :assertions [{:type :error
                                    :message "outer"
                                    :actual {:via cyclic-via
                                             :cause long-message}}]}]
            out (string-writer)
            err (string-writer)]
        (let [outcome (#'cli/run-cli
                       (#'cli/normalize-exec-opts {})
                       (test-boundary {:cwd (.getPath dir)
                                       :out out
                                       :err err
                                       :write-result-files (fn [& _]
                                                             (throw (ex-info "write exploded" {})))
                                       :run-clojure-test (fn [_]
                                                           (runner-result entries))}))
              diagnostic (:scry.cli/diagnostic-error outcome)
              stderr (str err)]
          (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
          (is (= 'scry.fixtures.pathological/map-shaped-actual
                 (:first-failing-var diagnostic)))
          (is (<= (count (:first-root-cause diagnostic)) 20100))
          (is (str/includes? (:first-root-cause diagnostic)
                             ":max-string-length"))
          (is (str/includes? stderr "First root cause:"))
          (is (<= (count stderr) 20500)))))))

(deftest run-cli-result-file-write-failure-is-diagnostic-test
  ;; Result-file serialization failure is post-run diagnostics: the summary and
  ;; test-derived outcome survive, with bounded diagnostic metadata attached.
  (with-temp-dir [dir]
    (let [out (string-writer)
          err (string-writer)
          summary-before-write? (atom false)]
      (let [outcome (#'cli/run-cli
                     (#'cli/normalize-exec-opts
                      {:vars ['scry.fixtures.failing/equality-fails]})
                     (test-boundary {:cwd (.getPath dir)
                                     :out out
                                     :err err
                                     :write-result-files (fn [& _]
                                                           (reset! summary-before-write?
                                                                   (str/includes? (str out) "Assertions:"))
                                                           (throw (ex-info "write exploded" {})))}))]
        (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
        (is (= [] (:result-files outcome)))
        (is (= :result-file-writing
               (get-in outcome [:scry.cli/diagnostic-error :phase])))
        (is (= 1 (get-in outcome [:scry.cli/diagnostic-error :failed-entry-count])))
        (is (= 'scry.fixtures.failing/equality-fails
               (get-in outcome [:scry.cli/diagnostic-error :first-failing-var])))
        (is @summary-before-write?)
        (is (str/includes? (str out) "Assertions:"))
        (is (str/includes? (str err)
                           "Failure diagnostics failed while serializing 1 failing entries."))))))

(deftest run-exec-pathological-fixtures-through-real-runner-test
  ;; Exercise the -X/run-with-boundary path through the real clojure-test runner
  ;; so structured non-zero ex-data preserves the test-derived outcome for
  ;; pathological cyclic assertion and ex-data failures.
  (with-temp-dir [dir]
    (let [out (string-writer)
          err (string-writer)
          thrown (try
                   (#'cli/run-with-boundary
                    {:namespaces ['scry.fixtures.pathological]}
                    (test-boundary {:cwd (.getPath dir)
                                    :out out
                                    :err err}))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))
          data (ex-data thrown)
          outcome (:outcome data)
          files (result-files dir)
          result-data (mapv #(edn/read-string (slurp (io/file dir ".scry-results" %))) files)]
      (is (some? thrown))
      (is (= :scry.cli/non-zero (:type data)))
      (is (= :scry.cli/test-failure (:scry.cli/outcome-kind data)))
      (assert-pathological-cli-outcome outcome files result-data (str out) (str err))
      (is (not (contains? outcome :scry.cli/diagnostic-error))))))

(deftest run-exec-result-file-write-failure-is-diagnostic-test
  ;; The -X path preserves the same post-run diagnostic failure semantics in
  ;; structured non-zero ex-data: no duplicate summary fields and no runner-error.
  (with-temp-dir [dir]
    (let [out (string-writer)
          err (string-writer)
          thrown (try
                   (#'cli/run-with-boundary
                    {:vars ['scry.fixtures.failing/equality-fails]}
                    (test-boundary {:cwd (.getPath dir)
                                    :out out
                                    :err err
                                    :write-result-files (fn [& _]
                                                          (throw (ex-info "write exploded" {})))}))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))
          data (ex-data thrown)
          outcome (:outcome data)]
      (is (some? thrown))
      (is (= :scry.cli/non-zero (:type data)))
      (is (= :scry.cli/test-failure (:scry.cli/outcome-kind data)))
      (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
      (is (= [] (:result-files outcome)))
      (is (= (:summary data) (:summary outcome)))
      (is (= :result-file-writing
             (get-in outcome [:scry.cli/diagnostic-error :phase])))
      (is (= 1 (get-in outcome [:scry.cli/diagnostic-error :failed-entry-count])))
      (is (= 'scry.fixtures.failing/equality-fails
             (get-in outcome [:scry.cli/diagnostic-error :first-failing-var])))
      (is (not (contains? outcome :summary-text)))
      (is (str/includes? (str out) "Assertions:"))
      (is (str/includes? (str err)
                         "Failure diagnostics failed while serializing 1 failing entries.")))))

(deftest run-cli-load-error-result-file-write-failure-is-diagnostic-test
  ;; Diagnostic-write failures preserve synthetic load-error outcomes and still
  ;; emit both the bounded fallback warning and the normal load-error stderr
  ;; detail/pointer semantics.
  (with-temp-dir [dir]
    (let [root-cause (RuntimeException. "load root cause")
          load-failure (ex-info "compile failed" {} root-cause)
          synthetic-error {:var nil
                           :ns nil
                           :status :error
                           :assertion-summary {:pass 0 :fail 0 :error 1}
                           :assertions [{:type :error
                                         :message "Failed loading tests:"
                                         :expected nil
                                         :actual load-failure}]
                           :out ""
                           :err ""}
          out (string-writer)
          err (string-writer)
          outcome (#'cli/run-cli
                   (#'cli/normalize-exec-opts {})
                   (test-boundary {:cwd (.getPath dir)
                                   :out out
                                   :err err
                                   :write-result-files (fn [& _]
                                                         (throw (ex-info "write exploded" {})))
                                   :run-clojure-test (fn [opts]
                                                       ((:progress-callback opts) synthetic-error)
                                                       (runner-result [synthetic-error]))}))
          diagnostic (:scry.cli/diagnostic-error outcome)
          stderr (str err)]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/load-error (:scry.cli/outcome-kind outcome)))
      (is (= [] (:result-files outcome)))
      (is (= "Assertions: 0 passed, 0 failed, 1 errored\nTests: 0 passed, 0 failed, 1 errored\n"
             (str out)))
      (is (= :result-file-writing (:phase diagnostic)))
      (is (= 1 (:failed-entry-count diagnostic)))
      (is (not (contains? diagnostic :first-failing-var)))
      (is (str/includes? (:first-root-cause diagnostic) "java.lang.RuntimeException"))
      (is (str/includes? (:first-root-cause diagnostic) "load root cause"))
      (is (str/includes? stderr "suite-error-1"))
      (is (str/includes? stderr
                         "Failure diagnostics failed while serializing 1 failing entries."))
      (is (str/includes? stderr "First root cause: java.lang.RuntimeException: load root cause"))
      (is (str/includes? stderr "Load error: Failed loading tests:"))
      (is (str/includes? stderr "java.lang.RuntimeException: load root cause"))
      (is (str/includes? stderr "See "))
      (is (str/includes? stderr "for failure details")))))

(deftest run-cli-unknown-result-file-write-failure-is-diagnostic-test
  ;; Diagnostic-write failures preserve unknown-result outcomes and still emit
  ;; the existing unknown-result result-directory pointer semantics.
  (with-temp-dir [dir]
    (let [unknown-entry {:var 'scry.fixtures.unknown/no-assertions
                         :ns 'scry.fixtures.unknown
                         :status :unknown
                         :assertion-summary {:pass 0 :fail 0 :error 0}
                         :assertions []
                         :out ""
                         :err ""}
          out (string-writer)
          err (string-writer)
          outcome (#'cli/run-cli
                   (#'cli/normalize-exec-opts {})
                   (test-boundary {:cwd (.getPath dir)
                                   :out out
                                   :err err
                                   :write-result-files (fn [& _]
                                                         (throw (ex-info "write exploded" {})))
                                   :run-clojure-test (fn [opts]
                                                       ((:progress-callback opts) unknown-entry)
                                                       (runner-result [unknown-entry]))}))
          diagnostic (:scry.cli/diagnostic-error outcome)
          stderr (str err)]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/unknown-result (:scry.cli/outcome-kind outcome)))
      (is (= [] (:result-files outcome)))
      (is (= "Assertions: 0 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored, 1 unknown\n"
             (str out)))
      (is (= :result-file-writing (:phase diagnostic)))
      (is (= 0 (:failed-entry-count diagnostic)))
      (is (not (contains? diagnostic :first-failing-var)))
      (is (not (contains? diagnostic :first-root-cause)))
      (is (str/starts-with? stderr "no-assertions\n"))
      (is (str/includes? stderr
                         "Failure diagnostics failed while serializing 0 failing entries."))
      (is (str/includes? stderr "See "))
      (is (str/includes? stderr "for failure details")))))

(deftest run-cli-pass-result-file-write-failure-is-diagnostic-test
  ;; Diagnostic-write failures are post-run even for green runs: they attach
  ;; bounded diagnostic metadata, but do not make a passing test run fail.
  (with-temp-dir [dir]
    (let [out (string-writer)
          err (string-writer)
          outcome (#'cli/run-cli
                   (#'cli/normalize-exec-opts
                    {:vars ['scry.fixtures.failing/also-passes]})
                   (test-boundary {:cwd (.getPath dir)
                                   :out out
                                   :err err
                                   :write-result-files (fn [& _]
                                                         (throw (ex-info "write exploded" {})))}))
          diagnostic (:scry.cli/diagnostic-error outcome)
          stderr (str err)]
      (is (= 0 (:exit-code outcome)))
      (is (= :scry.cli/pass (:scry.cli/outcome-kind outcome)))
      (is (= [] (:result-files outcome)))
      (is (str/includes? (str out)
                         "Assertions: 1 passed, 0 failed, 0 errored\nTests: 1 passed, 0 failed, 0 errored\n"))
      (is (= :result-file-writing (:phase diagnostic)))
      (is (= 0 (:failed-entry-count diagnostic)))
      (is (= 'clojure.lang.ExceptionInfo (:type diagnostic)))
      (is (= 'clojure.lang.ExceptionInfo (:root-type diagnostic)))
      (is (= "write exploded" (:message diagnostic)))
      (is (= "write exploded" (:root-message diagnostic)))
      (is (not (contains? diagnostic :first-failing-var)))
      (is (not (contains? diagnostic :first-root-cause)))
      (is (str/includes? stderr
                         "Failure diagnostics failed while serializing 0 failing entries."))
      (is (not (str/includes? stderr "for failure details"))))))

(deftest run-cli-zero-tests-result-file-write-failure-is-diagnostic-test
  ;; Diagnostic-write failures preserve zero-tests outcomes and do not emit the
  ;; failure-details pointer text because zero-tests is not a failure-details outcome.
  (with-temp-dir [dir]
    (let [out (string-writer)
          err (string-writer)
          outcome (#'cli/run-cli
                   (#'cli/normalize-exec-opts
                    {:namespaces ['clojure.core]})
                   (test-boundary {:cwd (.getPath dir)
                                   :out out
                                   :err err
                                   :write-result-files (fn [& _]
                                                         (throw (ex-info "write exploded" {})))}))
          diagnostic (:scry.cli/diagnostic-error outcome)
          stderr (str err)]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/zero-tests (:scry.cli/outcome-kind outcome)))
      (is (= [] (:result-files outcome)))
      (is (= "Assertions: 0 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored\n"
             (str out)))
      (is (= :result-file-writing (:phase diagnostic)))
      (is (= 0 (:failed-entry-count diagnostic)))
      (is (= "write exploded" (:message diagnostic)))
      (is (= "write exploded" (:root-message diagnostic)))
      (is (not (contains? diagnostic :first-failing-var)))
      (is (not (contains? diagnostic :first-root-cause)))
      (is (str/includes? stderr
                         "Failure diagnostics failed while serializing 0 failing entries."))
      (is (not (str/includes? stderr "for failure details"))))))

(deftest run-cli-result-format-projection-keeps-detailed-result-files-test
  ;; User-supplied result-format projection is preserved for the returned
  ;; result, while CLI-retained canonical results still drive detailed EDN
  ;; result files with assertions and captured output.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.output/noisy-and-fails]
                                    :result-format {:var {:top-level-keys [:summary :pass?]
                                                          :entry-keys [:var]
                                                          :assertions? false
                                                          :output? false}}}))
          failure-file (io/file dir ".scry-results" "scry.fixtures.output__noisy-and-fails.edn")
          failure-data (edn/read-string (slurp failure-file))]
      (is (= 1 (:exit-code outcome)))
      (is (str/starts-with? (:stderr outcome) "noisy-and-fails\n"))
      (is (str/includes? (:stderr outcome) "for failure details"))
      (is (= #{:summary :pass? :canonical-results}
             (set (keys (:result outcome)))))
      (is (not (contains? (:result outcome) :results)))
      (is (= ["scry.fixtures.output__noisy-and-fails.edn"]
             (result-files dir)))
      (is (= 'scry.fixtures.output/noisy-and-fails (:var failure-data)))
      (is (= :fail (:status failure-data)))
      (is (= "stdout from failing test\n" (:out failure-data)))
      (is (= "stderr from failing test\n" (:err failure-data)))
      (is (= :fail (:type (first (:assertions failure-data)))))
      (is (= '(= :a :b) (:expected (first (:assertions failure-data))))))))

(deftest run-cli-colliding-result-file-names-test
  ;; Namespace-prefixed result filenames prevent same unqualified test names
  ;; from overwriting each other.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.colliding-a/same-name
                                           'scry.fixtures.colliding-b/same-name]}))]
      (is (= 1 (:exit-code outcome)))
      (is (str/starts-with? (:stderr outcome) "same-name\nsame-name\n"))
      (is (str/includes? (:stderr outcome) "for failure details"))
      (is (= ["scry.fixtures.colliding-a__same-name.edn"
              "scry.fixtures.colliding-b__same-name.edn"]
             (result-files dir))))))

(deftest run-cli-short-circuiting-each-fixture-test
  ;; A var skipped by a short-circuiting :each fixture does not become an
  ;; unknown CLI var or produce progress/result files, but zero executed tests
  ;; still make the CLI exit non-zero.
  (with-temp-dir [dir]
    (reset! scry.fixtures.short-circuiting-fixtures/events [])
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:namespaces ['scry.fixtures.short-circuiting-fixtures]}))]
      (is (= 1 (:exit-code outcome)))
      (is (= "Assertions: 1 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored\n"
             (:stdout outcome)))
      (is (= "" (:stderr outcome)))
      (is (= [] (result-files dir)))
      (is (= [] (:canonical-results (:result outcome))))
      (is (= [:fixture-ran]
             @scry.fixtures.short-circuiting-fixtures/events))
      (is (= {:assertions {:pass 1 :fail 0 :error 0}
              :tests {:pass 0 :fail 0 :error 0 :unknown 0}
              :var-count 0}
             (:summary outcome))))))

(deftest run-cli-unknown-status-test
  ;; An executed test var with no assertion events is unknown: it prints the
  ;; unqualified name to stderr, records unknown summary count, writes no
  ;; failure/error EDN file, and exits non-zero.
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.unknown/no-assertions]}))]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/unknown-result (:scry.cli/outcome-kind outcome)))
      (is (= "Assertions: 0 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored, 1 unknown\n"
             (:stdout outcome)))
      (is (str/starts-with? (:stderr outcome) "no-assertions\n"))
      (is (str/includes? (:stderr outcome) "for failure details"))
      (is (= [] (result-files dir)))
      (is (= {:assertions {:pass 0 :fail 0 :error 0}
              :tests {:pass 0 :fail 0 :error 0 :unknown 1}
              :var-count 1}
             (:summary outcome)))
      (is (= [{:var 'scry.fixtures.unknown/no-assertions
               :ns 'scry.fixtures.unknown
               :status :unknown
               :assertion-summary {:pass 0 :fail 0 :error 0}
               :assertions []
               :out ""
               :err ""}]
             (:canonical-results (:result outcome)))))))

(deftest run-cli-outcome-classification-test
  ;; Outcome-kind classification is machine-readable and authoritative for
  ;; exit code across synthetic and malformed runner-result edges.
  (testing "synthetic-only passing entries are zero executable tests"
    (with-temp-dir [dir]
      (let [synthetic-pass {:var nil
                            :ns 'loader.demo
                            :status :pass
                            :assertion-summary {:pass 1 :fail 0 :error 0}
                            :assertions []}
            outcome (run-cli-in
                     dir
                     (#'cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [opts]
                        ((:progress-callback opts) synthetic-pass)
                        (runner-result [synthetic-pass]))})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/zero-tests (:scry.cli/outcome-kind outcome)))
        (is (= "Assertions: 1 passed, 0 failed, 0 errored\nTests: 1 passed, 0 failed, 0 errored\n"
               (:stdout outcome)))
        (is (= "" (:stderr outcome)))
        (is (= {:pass 1 :fail 0 :error 0}
               (get-in outcome [:summary :assertions])))
        (is (= {:pass 1 :fail 0 :error 0 :unknown 0}
               (get-in outcome [:summary :tests]))))))
  (testing "unknown entries are not masked by bare pass? false"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in
                     dir
                     (#'cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [_]
                        {:summary {:test 1 :pass 0 :fail 0 :error 0}
                         :pass? false
                         :canonical-results [{:var 'scry.fixtures.unknown/no-assertions
                                              :ns 'scry.fixtures.unknown
                                              :status :unknown
                                              :assertion-summary {:pass 0 :fail 0 :error 0}
                                              :assertions []}]})})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/unknown-result (:scry.cli/outcome-kind outcome)))
        (is (= {:pass 0 :fail 0 :error 0}
               (get-in outcome [:summary :assertions])))
        (is (= [] (result-files dir))))))
  (testing "zero executable tests are not masked by bare pass? false"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in
                     dir
                     (#'cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [_]
                        {:summary {:test 0 :pass 0 :fail 0 :error 0}
                         :pass? false
                         :canonical-results []})})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/zero-tests (:scry.cli/outcome-kind outcome)))
        (is (= {:pass 0 :fail 0 :error 0}
               (get-in outcome [:summary :assertions])))
        (is (= [] (result-files dir))))))
  (testing "synthetic unknown entries classify before zero executable tests"
    (with-temp-dir [dir]
      (let [synthetic-unknown {:var nil
                               :ns 'loader.demo
                               :status :unknown
                               :assertion-summary {:pass 0 :fail 0 :error 0}
                               :assertions []
                               :out ""
                               :err ""}
            outcome (run-cli-in
                     dir
                     (#'cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [opts]
                        ((:progress-callback opts) synthetic-unknown)
                        (runner-result [synthetic-unknown]))})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/unknown-result (:scry.cli/outcome-kind outcome)))
        (is (= "Assertions: 0 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored, 1 unknown\n"
               (:stdout outcome)))
        (is (str/starts-with? (:stderr outcome) "loader.demo/suite-unknown-1\n"))
        (is (str/includes? (:stderr outcome) "for failure details"))
        (is (= [] (result-files dir)))
        (is (= {:pass 0 :fail 0 :error 0 :unknown 1}
               (get-in outcome [:summary :tests])))
        (is (= 1 (get-in outcome [:summary :var-count]))))))
  (testing "synthetic load errors take precedence over concrete test failures"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in
                     dir
                     (#'cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [_]
                        (runner-result [{:var nil
                                         :ns 'loader.demo
                                         :status :error
                                         :assertion-summary {:pass 0 :fail 0 :error 1}
                                         :assertions [{:type :error
                                                       :message "could not load tests"}]}
                                        {:var 'scry.fixtures.failing/equality-fails
                                         :ns 'scry.fixtures.failing
                                         :status :fail
                                         :assertion-summary {:pass 0 :fail 1 :error 0}
                                         :assertions [{:type :fail
                                                       :message "expected values to match"}]}]))})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/load-error (:scry.cli/outcome-kind outcome)))
        (is (= {:pass 0 :fail 1 :error 1}
               (get-in outcome [:summary :assertions])))
        (is (= ["loader.demo__suite-error-1.edn"
                "scry.fixtures.failing__equality-fails.edn"]
               (result-files dir))))))
  (testing "aggregate assertion failures classify as test failures"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in
                     dir
                     (#'cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [_]
                        {:summary {:test 1 :pass 1 :fail 1 :error 0}
                         :pass? false
                         :canonical-results [{:var 'scry.fixtures.passing/arithmetic-passes
                                              :ns 'scry.fixtures.passing
                                              :status :pass
                                              :assertion-summary {:pass 1 :fail 0 :error 0}
                                              :assertions []}]})})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
        (is (= {:pass 1 :fail 1 :error 0}
               (get-in outcome [:summary :assertions])))
        (is (= [] (result-files dir))))))
  (testing "aggregate assertion failures classify before zero executable tests"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in
                     dir
                     (#'cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [_]
                        {:summary {:test 0 :pass 0 :fail 1 :error 1}
                         :pass? false
                         :canonical-results []})})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
        (is (= "Assertions: 0 passed, 1 failed, 1 errored\nTests: 0 passed, 0 failed, 0 errored\n"
               (:stdout outcome)))
        ;; No concrete entries, so only the failure-diagnostic pointer reaches
        ;; stderr for this aggregate test-failure.
        (is (str/starts-with? (:stderr outcome) "See "))
        (is (str/includes? (:stderr outcome) "for failure details"))
        (is (= {:assertions {:pass 0 :fail 1 :error 1}
                :tests {:pass 0 :fail 0 :error 0 :unknown 0}
                :var-count 0}
               (:summary outcome)))
        (is (= [] (result-files dir))))))
  (testing "canonical entries with missing or invalid statuses are runner errors"
    (doseq [[label entry] [["missing status"
                            {:var 'scry.fixtures.passing/arithmetic-passes
                             :ns 'scry.fixtures.passing
                             :assertion-summary {:pass 1 :fail 0 :error 0}
                             :assertions []}]
                           ["invalid status"
                            {:var 'scry.fixtures.passing/arithmetic-passes
                             :ns 'scry.fixtures.passing
                             :status :bogus
                             :assertion-summary {:pass 1 :fail 0 :error 0}
                             :assertions []}]]]
      (testing label
        (with-temp-dir [dir]
          (let [outcome (run-cli-in
                         dir
                         (#'cli/normalize-exec-opts {})
                         {:run-clojure-test
                          (fn [_]
                            {:summary {:test 1 :pass 1 :fail 0 :error 0}
                             :pass? true
                             :canonical-results [entry]})})]
            (is (= 1 (:exit-code outcome)))
            (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
            (is (= nil (:result outcome)))
            (is (= [] (result-files dir)))
            (is (str/includes? (:stderr outcome)
                               "Runner result included malformed canonical entry")))))))
  (testing "malformed runner results are runner errors"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in
                     dir
                     (#'cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [_]
                        {:summary {:test 0 :pass 0 :fail 0 :error 0}
                         :pass? true
                         :canonical-results nil})})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
        (is (str/includes? (:stderr outcome)
                           "Runner result did not include :canonical-results"))))))

(deftest run-cli-core-runner-does-not-resolve-kaocha-test
  ;; Core runner execution is independent of optional Kaocha resolution: a
  ;; normal clojure-test run must not touch the Kaocha resolver boundary.
  (with-temp-dir [dir]
    (let [resolver-called? (atom false)
          outcome (run-cli-in
                   dir
                   (#'cli/normalize-exec-opts
                    {:runner :clojure-test
                     :vars ['scry.fixtures.passing/arithmetic-passes]})
                   {:resolve-kaocha-runner
                    (fn []
                      (reset! resolver-called? true)
                      (throw (IllegalStateException. "should not resolve Kaocha")))})]
      (is (= 0 (:exit-code outcome)))
      (is (= false @resolver-called?))
      (is (= "" (:stderr outcome)))
      (is (= [] (result-files dir))))))

(deftest run-cli-run-level-fixture-failures-test
  ;; Run-level fixture assertions outside public var entries still drive the
  ;; CLI assertion summary and non-zero exit without per-var failure progress or
  ;; result files.
  (let [original-once-setup-pass? @scry.fixtures.asserting-fixtures/once-setup-pass?
        original-once-teardown-pass? @scry.fixtures.asserting-fixtures/once-teardown-pass?
        original-each-setup-pass? @scry.fixtures.asserting-fixtures/each-setup-pass?
        original-each-teardown-pass? @scry.fixtures.asserting-fixtures/each-teardown-pass?]
    (try
      (reset! scry.fixtures.asserting-fixtures/once-setup-pass? false)
      (reset! scry.fixtures.asserting-fixtures/once-teardown-pass? false)
      (reset! scry.fixtures.asserting-fixtures/each-setup-pass? true)
      (reset! scry.fixtures.asserting-fixtures/each-teardown-pass? true)
      (with-temp-dir [dir]
        (write-stale-result! dir)
        (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts
                                       {:namespaces ['scry.fixtures.asserting-fixtures]}))]
          (is (= 1 (:exit-code outcome)))
          (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
          (is (= ".Assertions: 3 passed, 2 failed, 0 errored\nTests: 1 passed, 0 failed, 0 errored\n"
                 (:stdout outcome)))
          ;; Run-level fixture failures have no per-var entry, so only the
          ;; failure-diagnostic pointer reaches stderr.
          (is (str/starts-with? (:stderr outcome) "See "))
          (is (str/includes? (:stderr outcome) "for failure details"))
          (is (= [] (result-files dir)))
          (is (false? (get-in outcome [:result :pass?])))
          (is (= {:assertions {:pass 3 :fail 2 :error 0}
                  :tests {:pass 1 :fail 0 :error 0 :unknown 0}
                  :var-count 1}
                 (:summary outcome)))))
      (finally
        (reset! scry.fixtures.asserting-fixtures/once-setup-pass?
                original-once-setup-pass?)
        (reset! scry.fixtures.asserting-fixtures/once-teardown-pass?
                original-once-teardown-pass?)
        (reset! scry.fixtures.asserting-fixtures/each-setup-pass?
                original-each-setup-pass?)
        (reset! scry.fixtures.asserting-fixtures/each-teardown-pass?
                original-each-teardown-pass?)))))

(deftest run-cli-no-tests-and-runner-errors-test
  ;; No executable vars, runner exceptions, and unavailable optional Kaocha
  ;; mode produce non-zero structured outcomes without leaving stale files.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (#'cli/normalize-exec-opts {:namespaces ['clojure.core]}))]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/zero-tests (:scry.cli/outcome-kind outcome)))
      (is (= "Assertions: 0 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored\n"
             (:stdout outcome)))
      (is (= [] (result-files dir)))))
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [out (string-writer)
          err (string-writer)
          outcome (#'cli/run-cli (#'cli/normalize-exec-opts
                                  {:namespaces ['scry.fixtures.missing-runner-exception]})
                                 (test-boundary {:cwd (.getPath dir)
                                                 :out out
                                                 :err err}))]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
      (is (= [] (result-files dir)))
      (is (= "No tests run — scry CLI error outcome: :scry.cli/runner-error\n"
             (str out)))
      (is (str/includes? (str err) "scry CLI error:"))
      (is (instance? java.io.FileNotFoundException (-> outcome :error :exception)))))
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [outcome (run-cli-in
                   dir
                   (#'cli/normalize-exec-opts {:runner :kaocha})
                   {:resolve-kaocha-runner
                    (fn []
                      (throw (java.io.FileNotFoundException. "scry.kaocha")))})]
      (is (= 1 (:exit-code outcome)))
      (is (= "No tests run — scry CLI error outcome: :scry.cli/runner-error\n"
             (:stdout outcome)))
      (is (= [] (result-files dir)))
      (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
      (is (str/includes? (:stderr outcome)
                         "scry CLI error: Kaocha CLI mode requires the optional scry.kaocha adapter"))
      (is (= {:type :scry.cli/runner-error :runner :kaocha}
             (-> outcome :error :data)))
      (is (instance? java.io.FileNotFoundException
                     (ex-cause (-> outcome :error :exception))))))
  (with-temp-dir [dir]
    (let [outcome (run-cli-in
                   dir
                   (#'cli/normalize-exec-opts {:runner :kaocha})
                   {:resolve-kaocha-runner
                    (fn []
                      (throw (IllegalStateException. "resolver boom")))})]
      (is (= 1 (:exit-code outcome)))
      (is (= "No tests run — scry CLI error outcome: :scry.cli/runner-error\n"
             (:stdout outcome)))
      (is (= [] (result-files dir)))
      (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
      (is (str/includes? (:stderr outcome)
                         "scry CLI error: Could not load Kaocha CLI runner"))
      (is (= {:type :scry.cli/runner-error :runner :kaocha}
             (-> outcome :error :data)))
      (is (instance? IllegalStateException
                     (ex-cause (-> outcome :error :exception))))))
  (doseq [resolved-runner [nil "not-invokable"]]
    (testing (str "invalid Kaocha resolver return " (pr-str resolved-runner))
      (with-temp-dir [dir]
        (let [outcome (run-cli-in
                       dir
                       (#'cli/normalize-exec-opts {:runner :kaocha})
                       {:resolve-kaocha-runner (constantly resolved-runner)})]
          (is (= 1 (:exit-code outcome)))
          (is (= "No tests run — scry CLI error outcome: :scry.cli/runner-error\n"
                 (:stdout outcome)))
          (is (= [] (result-files dir)))
          (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
          (is (str/includes? (:stderr outcome)
                             "scry CLI error: Resolved Kaocha CLI runner is not invokable"))
          (is (= {:type :scry.cli/runner-error :runner :kaocha}
                 (-> outcome :error :data))))))))

(deftest run-exec-entry-point-test
  ;; The -X entry point uses the same normalized run-cli path, returning
  ;; successful outcomes and throwing structured ex-info for non-zero outcomes.
  (with-temp-dir [dir]
    (let [out (string-writer)
          err (string-writer)
          outcome (#'cli/run-with-boundary {:vars ['scry.fixtures.passing/arithmetic-passes]}
                                           (test-boundary {:cwd (.getPath dir)
                                                           :out out
                                                           :err err}))]
      (is (= 0 (:exit-code outcome)))
      (is (= :scry.cli/pass (:scry.cli/outcome-kind outcome)))
      (is (str/includes? (str out) "Assertions: 2 passed"))
      (is (= "" (str err)))))
  (with-temp-dir [dir]
    (let [out (string-writer)
          err (string-writer)
          thrown (try
                   (#'cli/run-with-boundary {:vars ['scry.fixtures.failing/equality-fails]}
                                            (test-boundary {:cwd (.getPath dir)
                                                            :out out
                                                            :err err}))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (some? thrown))
      (is (= :scry.cli/non-zero (:type (ex-data thrown))))
      (is (= 1 (:exit-code (ex-data thrown))))
      (is (= :scry.cli/test-failure
             (:scry.cli/outcome-kind (ex-data thrown))))
      (is (= :scry.cli/test-failure
             (get-in (ex-data thrown) [:outcome :scry.cli/outcome-kind])))
      (is (= {:pass 0 :fail 1 :error 0}
             (get-in (ex-data thrown) [:summary :assertions])))
      (is (str/includes? (str err) "equality-fails"))))
  (testing "synthetic load errors use the structured non-zero -X contract"
    (with-temp-dir [dir]
      (let [out (string-writer)
            err (string-writer)
            synthetic-error {:var nil
                             :ns 'loader.demo
                             :status :error
                             :assertion-summary {:pass 0 :fail 0 :error 1}
                             :assertions [{:type :error
                                           :message "could not load tests"}]
                             :out "load out\n"
                             :err "load err\n"}
            thrown (try
                     (#'cli/run-with-boundary {}
                                              (test-boundary
                                               {:cwd (.getPath dir)
                                                :out out
                                                :err err
                                                :run-clojure-test
                                                (fn [opts]
                                                  ((:progress-callback opts) synthetic-error)
                                                  (runner-result [synthetic-error]))}))
                     nil
                     (catch clojure.lang.ExceptionInfo e e))
            data (ex-data thrown)
            result-file (io/file dir ".scry-results" "loader.demo__suite-error-1.edn")
            result-data (edn/read-string (slurp result-file))]
        (is (some? thrown))
        (is (= :scry.cli/non-zero (:type data)))
        (is (= 1 (:exit-code data)))
        (is (= :scry.cli/load-error (:scry.cli/outcome-kind data)))
        (is (= :scry.cli/load-error
               (get-in data [:outcome :scry.cli/outcome-kind])))
        (is (= {:pass 0 :fail 0 :error 1}
               (get-in data [:summary :assertions])))
        (is (= [(.getPath result-file)]
               (get-in data [:outcome :result-files])))
        (is (= ["loader.demo__suite-error-1.edn"] (result-files dir)))
        (is (= :error (:status result-data)))
        (is (= nil (:var result-data)))
        (is (= 'loader.demo (:ns result-data)))
        (is (str/includes? (str err) "loader.demo/suite-error-1")))))
  (testing "argument errors emit a single minimal stdout summary on the -X path"
    (let [out (string-writer)
          err (string-writer)
          thrown (try
                   (#'cli/run-with-boundary
                    {:runner :unknown}
                    (test-boundary {:out out :err err}))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))
          stdout (str out)]
      (is (some? thrown))
      (is (= :scry.cli/argument-error
             (:scry.cli/outcome-kind (ex-data thrown))))
      ;; stdout carries exactly one minimal error-outcome summary line.
      (is (= "No tests run — scry CLI error outcome: :scry.cli/argument-error\n"
             stdout))
      ;; returned outcome map keeps :summary nil (stdout-text-only change).
      (is (nil? (:summary (ex-data thrown))))))
  (testing "argument errors use the structured non-zero -X contract"
    (let [thrown (try
                   (cli/run {:runner :unknown})
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (some? thrown))
      (is (= :scry.cli/non-zero (:type (ex-data thrown))))
      (is (= 1 (:exit-code (ex-data thrown))))
      (is (nil? (:summary (ex-data thrown))))
      (is (= :scry.cli/argument-error
             (:scry.cli/outcome-kind (ex-data thrown))))
      (is (= :scry.cli/argument-error
             (get-in (ex-data thrown) [:outcome :scry.cli/outcome-kind])))
      (is (= :scry.cli/argument-error
             (get-in (ex-data thrown) [:error :data :type])))
      (is (str/includes? (get-in (ex-data thrown) [:error :message])
                         "Unknown runner"))
      (is (= 1 (get-in (ex-data thrown) [:outcome :exit-code]))))))

(deftest run-exec-nil-opts-test
  ;; `clojure -X:alias` where the alias supplies only :exec-fn invokes the exec
  ;; fn with nil (no :exec-args), so cli/run must coerce nil to {} and not
  ;; reject it as an argument error.
  ;;
  ;; We stub run-with-boundary to capture the coerced opts and avoid triggering
  ;; test discovery: calling cli/run nil with empty opts would discover and run
  ;; the entire test suite via the JVM process CWD, making the test non-isolated.
  (let [received-opts (atom :not-called)]
    (with-redefs [cli/run-with-boundary
                  (fn [opts _boundary]
                    (reset! received-opts opts)
                    {:exit-code 0
                     :scry.cli/outcome-kind :scry.cli/pass
                     :result nil :summary nil :result-files [] :error nil})]
      (cli/run nil)
      (is (= {} @received-opts)
          "cli/run nil must coerce nil to {} before run-with-boundary"))))

(deftest main-outcome-entry-point-test
  ;; Main-style invocation parsing, help, and argument errors can be verified
  ;; through main-outcome without calling System/exit.
  (testing "help prints usage and exits successfully"
    (let [out (string-writer)
          err (string-writer)]
      (is (= 0 (#'cli/main-outcome ["--help"] (test-boundary {:out out :err err}))))
      (is (str/includes? (str out) "Usage:"))
      (is (not (str/includes? (str out) "scry CLI error outcome")))
      (is (= "" (str err)))))
  (testing "argument errors print terse diagnostics and exit non-zero"
    (let [out (string-writer)
          err (string-writer)]
      (is (= 1 (#'cli/main-outcome ["--unknown"] (test-boundary {:out out :err err}))))
      (is (= "No tests run — scry CLI error outcome: :scry.cli/argument-error\n"
             (str out)))
      (is (str/includes? (str err) "scry CLI argument error: Unknown option"))))
  (testing "test-running main path delegates to run-cli"
    (with-temp-dir [dir]
      (let [out (string-writer)
            err (string-writer)]
        (is (= 0 (#'cli/main-outcome ["--var" "scry.fixtures.passing/arithmetic-passes"]
                                     (test-boundary {:cwd (.getPath dir)
                                                     :out out
                                                     :err err}))))
        (is (str/includes? (str out) "Assertions: 2 passed"))
        (is (= [] (result-files dir)))))))
