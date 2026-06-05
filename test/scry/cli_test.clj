(ns scry.cli-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.cli :as cli]
   [scry.cli.results :as cli-results]
   [scry.fixtures.arbitrary]
   [scry.fixtures.asserting-fixtures]
   [scry.fixtures.colliding-a]
   [scry.fixtures.colliding-b]
   [scry.fixtures.erroring]
   [scry.fixtures.failing]
   [scry.fixtures.mixed]
   [scry.fixtures.output]
   [scry.fixtures.passing]
   [scry.fixtures.short-circuiting-fixtures]
   [scry.fixtures.unknown]))

(defn not-a-test
  []
  :not-a-test)

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
    (let [opts (cli/normalize-exec-opts {})]
      (is (= :clojure-test (:runner opts)))
      (is (contains? (set (get-in opts [:result-format :suite :top-level-keys]))
                     :canonical-results))
      (is (contains? (set (get-in opts [:result-format :namespace :top-level-keys]))
                     :canonical-results))
      (is (contains? (set (get-in opts [:result-format :var :top-level-keys]))
                     :canonical-results))))
  (testing "core selector coercions"
    (let [opts (cli/normalize-exec-opts
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
         #(cli/normalize-exec-opts {:vars 'arithmetic-passes})))
    (is (argument-error?
         #(cli/normalize-exec-opts {:vars 'scry.fixtures.passing/missing})))
    (is (argument-error?
         #(cli/normalize-exec-opts {:vars #'not-a-test}))))
  (testing "invalid regex errors"
    (is (argument-error?
         #(cli/normalize-exec-opts {:ns-pattern "["})))))

(deftest normalize-exec-opts-runner-validation-test
  ;; Runner-specific options fail early when supplied to the wrong runner.
  (testing "core mode rejects Kaocha-only options"
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :clojure-test :suite :unit}))))
  (testing "Kaocha mode rejects core-only selectors"
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha
                                    :namespaces ['scry.fixtures.passing]})))
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha
                                    :vars ['scry.fixtures.passing/arithmetic-passes]})))
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha
                                    :ns-pattern #".*"})))
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha
                                    :namespace-pattern ".*"})))
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha
                                    :namespace-regex ".*"}))))
  (testing "Kaocha directory conflicts fail clearly"
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha
                                    :dirs ["test"]
                                    :test-paths ["other-test"]})))
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha
                                    :dirs ["test"]
                                    :config {:kaocha/tests []}}))))
  (testing "unknown runner fails"
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :unknown})))))

(deftest normalize-exec-opts-kaocha-test
  ;; Kaocha normalization accepts suite/config/fallback options and maps dirs
  ;; to fallback test paths when no explicit config is supplied.
  (testing "Kaocha options"
    (let [opts (cli/normalize-exec-opts {:runner "kaocha"
                                         :dirs ["test" "integration-test"]
                                         :suite :unit})]
      (is (= :kaocha (:runner opts)))
      (is (= ["test" "integration-test"] (:test-paths opts)))
      (is (= :unit (:suite opts)))))
  (testing "Kaocha suites must be a non-empty collection"
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha :suites []})))
    (is (argument-error?
         #(cli/normalize-exec-opts {:runner :kaocha :suites :unit})))))

(deftest parse-main-args-test
  ;; -m string flags normalize to the same option map shape as -X options.
  (testing "accepted core flags"
    (let [opts (cli/parse-main-args ["--runner" "test"
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
    (let [opts (cli/parse-main-args ["-r" "core"
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
    (let [opts (cli/parse-main-args ["--namespace-regex" "scry\\.fixtures\\..*"])]
      (is (= :clojure-test (:runner opts)))
      (is (instance? java.util.regex.Pattern (:ns-pattern opts)))))
  (testing "accepted repeated Kaocha suite flags"
    (let [opts (cli/parse-main-args ["--runner" "kaocha"
                                     "--suite" "unit"
                                     "--suite" "integration"])]
      (is (= :kaocha (:runner opts)))
      (is (= ["unit" "integration"] (:suites opts)))))
  (testing "accepted Kaocha short suite flags"
    (let [opts (cli/parse-main-args ["-r" "kaocha"
                                     "-s" "unit"
                                     "-s" "integration"])]
      (is (= :kaocha (:runner opts)))
      (is (= ["unit" "integration"] (:suites opts)))))
  (testing "accepted Kaocha suites and config EDN flags"
    (let [opts (cli/parse-main-args ["--runner" "kaocha"
                                     "--suites" "[:unit]"
                                     "--config" "{:kaocha/tests []}"])]
      (is (= [:unit] (:suites opts)))
      (is (= {:kaocha/tests []} (:config opts)))))
  (testing "help does not normalize or run"
    (is (= {:help? true :usage cli/usage}
           (cli/parse-main-args ["--help"]))))
  (testing "parser errors"
    (is (argument-error? #(cli/parse-main-args ["--unknown"])))
    (is (argument-error? #(cli/parse-main-args ["--dir"])))
    (is (argument-error? #(cli/parse-main-args ["--result-format" "["])))
    (is (argument-error? #(cli/parse-main-args ["--ns-pattern" "a"
                                                "--namespace-pattern" "b"])))
    (is (argument-error? #(cli/parse-main-args ["--namespace-pattern" "a"
                                                "--namespace-regex" "b"])))
    (is (argument-error? #(cli/parse-main-args ["--suite" "unit"
                                                "--suites" "[:integration]"])))))

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
  ([dir opts boundary]
   (let [out (string-writer)
         err (string-writer)
         outcome (cli/run-cli opts (merge {:cwd (.getPath dir) :out out :err err}
                                          boundary))]
     (assoc outcome :stdout (str out) :stderr (str err)))))

(defn- result-files
  [dir]
  (->> (.listFiles (io/file dir ".scry-results"))
       (map #(.getName %))
       sort
       vec))

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
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
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
          (let [outcome (run-cli-in dir (cli/normalize-exec-opts
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
        (let [outcome (cli/run-cli
                       (cli/normalize-exec-opts {})
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
                                           :assertions []}]))})]
          (is (= 1 (:exit-code outcome)))
          (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
          (is (= "" (str out)))
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
              (let [outcome (cli/run-cli
                             (cli/normalize-exec-opts {})
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
                                                 :assertions []}]))})]
                (is (= 1 (:exit-code outcome)))
                (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
                (is (= "" (str out)))
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
      (let [outcome (run-cli-in dir (cli/normalize-exec-opts
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
      (let [outcome (run-cli-in dir (cli/normalize-exec-opts
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
                              (cli/normalize-exec-opts {})
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
      (is (= "loader.demo/suite-error-1\nsuite-fail-1\nloader.demo/suite-unknown-1\n"
             (:stderr outcome)))
      (is (= ["loader.demo__suite-error-1.edn" "suite-fail-1.edn"] files))
      (is (= nil (:var error-data)))
      (is (= 'loader.demo (:ns error-data)))
      (is (= :error (:status error-data)))
      (is (= :fail (:status fail-data))))))

(deftest run-cli-failing-run-test
  ;; A failing CLI run prints unqualified failing names to stderr, writes
  ;; detailed EDN files, removes stale files, and exits non-zero.
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.failing/also-passes
                                           'scry.fixtures.failing/equality-fails]}))
          files (result-files dir)
          failure-file (io/file dir ".scry-results" "scry.fixtures.failing__equality-fails.edn")
          failure-data (edn/read-string (slurp failure-file))]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
      (is (= ".Assertions: 1 passed, 1 failed, 0 errored\nTests: 1 passed, 1 failed, 0 errored\n"
             (:stdout outcome)))
      (is (= "equality-fails\n" (:stderr outcome)))
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

(deftest run-cli-error-result-files-are-readable-edn-test
  ;; Error result files sanitize raw Throwables so clojure.edn/read-string can
  ;; read the file while preserving exception class/message/stacktrace detail.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.erroring/throws-exception]}))
          error-file (io/file dir ".scry-results" "scry.fixtures.erroring__throws-exception.edn")
          error-data (edn/read-string (slurp error-file))
          assertion (first (:assertions error-data))]
      (is (= 1 (:exit-code outcome)))
      (is (= "throws-exception\n" (:stderr outcome)))
      (is (= ["scry.fixtures.erroring__throws-exception.edn"] (result-files dir)))
      (is (= 'scry.fixtures.erroring/throws-exception (:var error-data)))
      (is (= :error (:status error-data)))
      (is (= :error (:type assertion)))
      (is (= :throwable (get-in assertion [:actual :type])))
      (is (= 'java.lang.ArithmeticException (get-in assertion [:actual :class])))
      (is (str/includes? (get-in assertion [:actual :stacktrace])
                         "java.lang.ArithmeticException"))
      (is (str/includes? (:stacktrace assertion)
                         "java.lang.ArithmeticException")))))

(deftest run-cli-mixed-fail-and-error-status-test
  ;; A test var with both failure and error assertions is treated as errored
  ;; throughout the CLI contract: one stderr progress name, one result file,
  ;; errored var summary, and non-zero exit.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.mixed/fail-then-error]}))
          error-file (io/file dir ".scry-results" "scry.fixtures.mixed__fail-then-error.edn")
          error-data (edn/read-string (slurp error-file))]
      (is (= 1 (:exit-code outcome)))
      (is (= "Assertions: 0 passed, 1 failed, 1 errored\nTests: 0 passed, 0 failed, 1 errored\n"
             (:stdout outcome)))
      (is (= "fail-then-error\n" (:stderr outcome)))
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
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.arbitrary/arbitrary-object-fails]}))
          failure-file (io/file dir ".scry-results" "scry.fixtures.arbitrary__arbitrary-object-fails.edn")
          failure-data (edn/read-string (slurp failure-file))
          assertion (first (:assertions failure-data))]
      (is (= 1 (:exit-code outcome)))
      (is (= "arbitrary-object-fails\n" (:stderr outcome)))
      (is (= ["scry.fixtures.arbitrary__arbitrary-object-fails.edn"] (result-files dir)))
      (is (= 'scry.fixtures.arbitrary/arbitrary-object-fails (:var failure-data)))
      (is (= :fail (:status failure-data)))
      (let [object-leaves (filter #(and (map? %)
                                        (= :object (:type %))
                                        (= 'java.lang.Object (:class %)))
                                  (tree-seq coll? seq assertion))]
        (is (= 2 (count object-leaves)))
        (is (every? #(string? (:pr-str %)) object-leaves))))))

(deftest run-cli-result-format-projection-keeps-detailed-result-files-test
  ;; User-supplied result-format projection is preserved for the returned
  ;; result, while CLI-retained canonical results still drive detailed EDN
  ;; result files with assertions and captured output.
  (with-temp-dir [dir]
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.output/noisy-and-fails]
                                    :result-format {:var {:top-level-keys [:summary :pass?]
                                                          :entry-keys [:var]
                                                          :assertions? false
                                                          :output? false}}}))
          failure-file (io/file dir ".scry-results" "scry.fixtures.output__noisy-and-fails.edn")
          failure-data (edn/read-string (slurp failure-file))]
      (is (= 1 (:exit-code outcome)))
      (is (= "noisy-and-fails\n" (:stderr outcome)))
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
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.colliding-a/same-name
                                           'scry.fixtures.colliding-b/same-name]}))]
      (is (= 1 (:exit-code outcome)))
      (is (= "same-name\nsame-name\n" (:stderr outcome)))
      (is (= ["scry.fixtures.colliding-a__same-name.edn"
              "scry.fixtures.colliding-b__same-name.edn"]
             (result-files dir))))))

(deftest run-cli-short-circuiting-each-fixture-test
  ;; A var skipped by a short-circuiting :each fixture does not become an
  ;; unknown CLI var or produce progress/result files, but zero executed tests
  ;; still make the CLI exit non-zero.
  (with-temp-dir [dir]
    (reset! scry.fixtures.short-circuiting-fixtures/events [])
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
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
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts
                                   {:vars ['scry.fixtures.unknown/no-assertions]}))]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/unknown-result (:scry.cli/outcome-kind outcome)))
      (is (= "Assertions: 0 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored, 1 unknown\n"
             (:stdout outcome)))
      (is (= "no-assertions\n" (:stderr outcome)))
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
                     (cli/normalize-exec-opts {})
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
                     (cli/normalize-exec-opts {})
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
                     (cli/normalize-exec-opts {})
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
                     (cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [opts]
                        ((:progress-callback opts) synthetic-unknown)
                        (runner-result [synthetic-unknown]))})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/unknown-result (:scry.cli/outcome-kind outcome)))
        (is (= "Assertions: 0 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored, 1 unknown\n"
               (:stdout outcome)))
        (is (= "loader.demo/suite-unknown-1\n"
               (:stderr outcome)))
        (is (= [] (result-files dir)))
        (is (= {:pass 0 :fail 0 :error 0 :unknown 1}
               (get-in outcome [:summary :tests])))
        (is (= 1 (get-in outcome [:summary :var-count]))))))
  (testing "synthetic load errors take precedence over concrete test failures"
    (with-temp-dir [dir]
      (let [outcome (run-cli-in
                     dir
                     (cli/normalize-exec-opts {})
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
                     (cli/normalize-exec-opts {})
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
                     (cli/normalize-exec-opts {})
                     {:run-clojure-test
                      (fn [_]
                        {:summary {:test 0 :pass 0 :fail 1 :error 1}
                         :pass? false
                         :canonical-results []})})]
        (is (= 1 (:exit-code outcome)))
        (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
        (is (= "Assertions: 0 passed, 1 failed, 1 errored\nTests: 0 passed, 0 failed, 0 errored\n"
               (:stdout outcome)))
        (is (= "" (:stderr outcome)))
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
                         (cli/normalize-exec-opts {})
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
                     (cli/normalize-exec-opts {})
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
                   (cli/normalize-exec-opts
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
        (let [outcome (run-cli-in dir (cli/normalize-exec-opts
                                       {:namespaces ['scry.fixtures.asserting-fixtures]}))]
          (is (= 1 (:exit-code outcome)))
          (is (= :scry.cli/test-failure (:scry.cli/outcome-kind outcome)))
          (is (= ".Assertions: 3 passed, 2 failed, 0 errored\nTests: 1 passed, 0 failed, 0 errored\n"
                 (:stdout outcome)))
          (is (= "" (:stderr outcome)))
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
    (let [outcome (run-cli-in dir (cli/normalize-exec-opts {:namespaces ['clojure.core]}))]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/zero-tests (:scry.cli/outcome-kind outcome)))
      (is (= "Assertions: 0 passed, 0 failed, 0 errored\nTests: 0 passed, 0 failed, 0 errored\n"
             (:stdout outcome)))
      (is (= [] (result-files dir)))))
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [out (string-writer)
          err (string-writer)
          outcome (cli/run-cli (cli/normalize-exec-opts
                                {:namespaces ['scry.fixtures.missing-runner-exception]})
                               {:cwd (.getPath dir)
                                :out out
                                :err err})]
      (is (= 1 (:exit-code outcome)))
      (is (= :scry.cli/runner-error (:scry.cli/outcome-kind outcome)))
      (is (= [] (result-files dir)))
      (is (str/includes? (str err) "scry CLI error:"))
      (is (instance? java.io.FileNotFoundException (-> outcome :error :exception)))))
  (with-temp-dir [dir]
    (write-stale-result! dir)
    (let [outcome (run-cli-in
                   dir
                   (cli/normalize-exec-opts {:runner :kaocha})
                   {:resolve-kaocha-runner
                    (fn []
                      (throw (java.io.FileNotFoundException. "scry.kaocha")))})]
      (is (= 1 (:exit-code outcome)))
      (is (= "" (:stdout outcome)))
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
                   (cli/normalize-exec-opts {:runner :kaocha})
                   {:resolve-kaocha-runner
                    (fn []
                      (throw (IllegalStateException. "resolver boom")))})]
      (is (= 1 (:exit-code outcome)))
      (is (= "" (:stdout outcome)))
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
                       (cli/normalize-exec-opts {:runner :kaocha})
                       {:resolve-kaocha-runner (constantly resolved-runner)})]
          (is (= 1 (:exit-code outcome)))
          (is (= "" (:stdout outcome)))
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
          outcome (cli/run {:vars ['scry.fixtures.passing/arithmetic-passes]}
                           {:cwd (.getPath dir) :out out :err err})]
      (is (= 0 (:exit-code outcome)))
      (is (= :scry.cli/pass (:scry.cli/outcome-kind outcome)))
      (is (str/includes? (str out) "Assertions: 2 passed"))
      (is (= "" (str err)))))
  (with-temp-dir [dir]
    (let [out (string-writer)
          err (string-writer)
          thrown (try
                   (cli/run {:vars ['scry.fixtures.failing/equality-fails]}
                            {:cwd (.getPath dir) :out out :err err})
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
                     (cli/run {}
                              {:cwd (.getPath dir)
                               :out out
                               :err err
                               :run-clojure-test
                               (fn [opts]
                                 ((:progress-callback opts) synthetic-error)
                                 (runner-result [synthetic-error]))})
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

(deftest main-outcome-entry-point-test
  ;; Main-style invocation parsing, help, and argument errors can be verified
  ;; through main-outcome without calling System/exit.
  (testing "help prints usage and exits successfully"
    (let [out (string-writer)
          err (string-writer)]
      (is (= 0 (cli/main-outcome ["--help"] {:out out :err err})))
      (is (str/includes? (str out) "Usage:"))
      (is (= "" (str err)))))
  (testing "argument errors print terse diagnostics and exit non-zero"
    (let [out (string-writer)
          err (string-writer)]
      (is (= 1 (cli/main-outcome ["--unknown"] {:out out :err err})))
      (is (= "" (str out)))
      (is (str/includes? (str err) "scry CLI argument error: Unknown option"))))
  (testing "test-running main path delegates to run-cli"
    (with-temp-dir [dir]
      (let [out (string-writer)
            err (string-writer)]
        (is (= 0 (cli/main-outcome ["--var" "scry.fixtures.passing/arithmetic-passes"]
                                   {:cwd (.getPath dir) :out out :err err})))
        (is (str/includes? (str out) "Assertions: 2 passed"))
        (is (= [] (result-files dir)))))))
