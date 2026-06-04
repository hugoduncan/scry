(ns scry.cli
  "Command-line entry points and option normalization for scry."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [scry.capture :as capture]
   [scry.cli.results :as results]
   [scry.clojure-test :as clojure-test]))

(def usage
  (str "Usage:\n"
       "  clojure -M:test -m scry.cli [options]\n"
       "  clojure -X:test scry.cli/run :runner :clojure-test\n\n"
       "Options:\n"
       "  -r, --runner RUNNER             clojure-test (default) or kaocha\n"
       "  -d, --dir DIR                   Test directory; repeatable\n"
       "  -n, --namespace, --ns NS        Test namespace; repeatable, core mode only\n"
       "  -v, --var VAR                   Fully-qualified test var; repeatable, core mode only\n"
       "      --ns-pattern REGEX          Namespace regex, core mode only\n"
       "      --result-format EDN         Result-format map\n"
       "  -s, --suite SUITE               Kaocha suite; repeatable\n"
       "      --suites EDN                Kaocha suites collection\n"
       "      --config EDN                Kaocha config map\n"
       "      --help                      Print this help"))

(def ^:private result-scopes [:suite :namespace :var])

(def ^:private runner-aliases
  {"clojure-test" :clojure-test
   "clojure.test" :clojure-test
   "core" :clojure-test
   "test" :clojure-test
   "kaocha" :kaocha})

(def ^:private ns-pattern-keys [:ns-pattern :namespace-pattern :namespace-regex])
(def ^:private core-only-keys (into #{:namespaces :vars} ns-pattern-keys))
(def ^:private kaocha-only-keys #{:suite :suites :config})
(def ^:private kaocha-fallback-keys #{:source-paths :test-paths :ns-patterns})

(defn- argument-error
  [message data]
  (throw (ex-info message (assoc data :type :scry.cli/argument-error))))

(defn- present?
  [opts k]
  (contains? opts k))

(defn- sequential-but-not-string?
  [x]
  (and (sequential? x) (not (string? x))))

(defn- one-or-many
  [x]
  (cond
    (nil? x) []
    (sequential-but-not-string? x) (vec x)
    :else [x]))

(defn normalize-runner
  "Normalize a CLI runner identifier to :clojure-test or :kaocha."
  [runner]
  (let [value (or runner :clojure-test)
        token (cond
                (keyword? value) (name value)
                (symbol? value) (name value)
                (string? value) value
                :else nil)]
    (or (some-> token str/lower-case runner-aliases)
        (argument-error (str "Unknown runner: " (pr-str value))
                        {:option :runner :value value}))))

(defn- normalize-dirs
  [dirs]
  (let [values (one-or-many dirs)]
    (when (some #(not (string? %)) values)
      (argument-error ":dirs must be a string or collection of strings"
                      {:option :dirs :value dirs}))
    (vec values)))

(defn- normalize-namespaces
  [namespaces]
  (let [values (one-or-many namespaces)]
    (when (some #(not (or (symbol? %) (string? %))) values)
      (argument-error ":namespaces must contain symbols or strings"
                      {:option :namespaces :value namespaces}))
    (mapv symbol values)))

(defn- parse-regex
  [value option]
  (cond
    (nil? value) nil
    (instance? java.util.regex.Pattern value) value
    (string? value)
    (try
      (re-pattern value)
      (catch java.util.regex.PatternSyntaxException e
        (argument-error (str "Invalid namespace regex: " (.getMessage e))
                        {:option option :value value})))
    :else
    (argument-error "Namespace pattern must be a regex pattern or string"
                    {:option option :value value})))

(defn- normalize-ns-pattern
  [opts]
  (let [present-keys (filter #(present? opts %) ns-pattern-keys)]
    (when (> (count present-keys) 1)
      (argument-error "Specify only one namespace pattern option"
                      {:options (vec present-keys)}))
    (when-let [option (first present-keys)]
      (parse-regex (get opts option) option))))

(defn- qualified-var-symbol
  [value]
  (cond
    (var? value) nil
    (symbol? value) value
    (string? value) (symbol value)
    :else
    (argument-error ":vars must contain Vars or fully-qualified symbols/strings"
                    {:option :vars :value value})))

(defn- resolve-test-var
  [value]
  (if (var? value)
    (if (:test (meta value))
      value
      (argument-error "Var is not a test var"
                      {:option :vars :value (symbol (str (ns-name (:ns (meta value))))
                                                    (str (:name (meta value))))}))
    (let [sym (qualified-var-symbol value)
          ns-sym (some-> sym namespace symbol)
          var-name (some-> sym name symbol)]
      (when-not (and ns-sym var-name)
        (argument-error "Test var must be fully qualified"
                        {:option :vars :value value}))
      (try
        (require ns-sym)
        (catch Throwable e
          (argument-error (str "Could not require namespace " ns-sym)
                          {:option :vars :value value :cause e})))
      (let [resolved (ns-resolve ns-sym var-name)]
        (cond
          (nil? resolved)
          (argument-error (str "Could not resolve test var " sym)
                          {:option :vars :value value})

          (not (var? resolved))
          (argument-error (str sym " does not resolve to a Var")
                          {:option :vars :value value})

          (:test (meta resolved))
          resolved

          :else
          (argument-error (str sym " is not a test var")
                          {:option :vars :value value}))))))

(defn- normalize-vars
  [vars]
  (mapv resolve-test-var (one-or-many vars)))

(defn- validate-map-option
  [opts k]
  (when (and (present? opts k) (not (map? (get opts k))))
    (argument-error (str k " must be a map") {:option k :value (get opts k)})))

(defn- normalize-suites
  [suites]
  (let [values (one-or-many suites)]
    (when-not (seq values)
      (argument-error ":suites must be a non-empty collection"
                      {:option :suites :value suites}))
    (when-not (sequential-but-not-string? suites)
      (argument-error ":suites must be a non-empty collection"
                      {:option :suites :value suites}))
    (vec values)))

(defn- reject-keys
  [opts keys message]
  (let [present-keys (filter #(present? opts %) keys)]
    (when (seq present-keys)
      (argument-error message {:options (vec present-keys)}))))

(defn- cli-result-format
  [result-format]
  (let [base (or result-format {})]
    (reduce (fn [fmt scope]
              (update-in fmt [scope :top-level-keys]
                         (fn [keys]
                           (-> (or keys (get-in capture/default-result-format
                                                [scope :top-level-keys]))
                               vec
                               (into [:summary :pass? :canonical-results])
                               distinct
                               vec))))
            base
            result-scopes)))

(defn- normalize-core-options
  [opts normalized]
  (reject-keys opts (into kaocha-only-keys kaocha-fallback-keys)
               "Kaocha options require :runner :kaocha")
  (cond-> normalized
    (present? opts :dirs) (assoc :dirs (normalize-dirs (:dirs opts)))
    (some #(present? opts %) ns-pattern-keys) (assoc :ns-pattern (normalize-ns-pattern opts))
    (present? opts :namespaces) (assoc :namespaces (normalize-namespaces (:namespaces opts)))
    (present? opts :vars) (assoc :vars (normalize-vars (:vars opts)))))

(defn- normalize-kaocha-options
  [opts normalized]
  (reject-keys opts core-only-keys
               "Core namespace, var, and ns-pattern selectors are not supported by Kaocha mode")
  (when (and (present? opts :suite) (present? opts :suites))
    (argument-error "Specify either :suite or :suites, not both"
                    {:options [:suite :suites]}))
  (when (and (present? opts :dirs) (present? opts :test-paths))
    (argument-error "Specify either :dirs or :test-paths for Kaocha mode, not both"
                    {:options [:dirs :test-paths]}))
  (when (and (present? opts :dirs) (present? opts :config))
    (argument-error ":dirs cannot be combined with explicit Kaocha :config"
                    {:options [:dirs :config]}))
  (cond-> normalized
    (present? opts :suite) (assoc :suite (:suite opts))
    (present? opts :suites) (assoc :suites (normalize-suites (:suites opts)))
    (present? opts :config) (assoc :config (:config opts))
    (present? opts :dirs) (assoc :test-paths (normalize-dirs (:dirs opts)))
    (present? opts :source-paths) (assoc :source-paths (:source-paths opts))
    (present? opts :test-paths) (assoc :test-paths (:test-paths opts))
    (present? opts :ns-patterns) (assoc :ns-patterns (:ns-patterns opts))))

(defn normalize-exec-opts
  "Normalize a `clojure -X` option map for CLI execution.

   Throws ex-info with :type :scry.cli/argument-error on invalid options."
  [opts]
  (when-not (map? opts)
    (argument-error "CLI options must be a map" {:value opts}))
  (validate-map-option opts :result-format)
  (validate-map-option opts :config)
  (let [runner (normalize-runner (:runner opts))
        normalized (cond-> {:runner runner}
                     (present? opts :result-format)
                     (assoc :result-format (cli-result-format (:result-format opts))))]
    (cond-> (case runner
              :clojure-test (normalize-core-options opts normalized)
              :kaocha (normalize-kaocha-options opts normalized))
      (not (present? opts :result-format))
      (assoc :result-format (cli-result-format nil)))))

(defn- read-edn-option
  [text option]
  (try
    (edn/read-string text)
    (catch Throwable e
      (argument-error (str "Invalid EDN for " (name option))
                      {:option option :value text :cause e}))))

(defn- require-value
  [args option]
  (let [value (first args)]
    (when (or (nil? value) (str/starts-with? value "-"))
      (argument-error (str option " requires a value") {:option option}))
    value))

(defn- add-repeat
  [opts k value]
  (update opts k (fnil conj []) value))

(defn- main-opts->exec-opts
  [raw]
  (let [suite-values (:suite-values raw)
        opts (-> raw
                 (dissoc :suite-values :help? :ns-pattern-option)
                 (cond->
                  (= 1 (count suite-values))
                   (assoc :suite (first suite-values))

                   (> (count suite-values) 1)
                   (assoc :suites (vec suite-values))))]
    opts))

(defn parse-main-args
  "Parse `-m scry.cli` string args into normalized CLI options.

   Returns {:help? true :usage usage} for --help. Throws ex-info for argument
   errors."
  [args]
  (loop [remaining (seq args)
         raw {}]
    (if-not remaining
      (if (:help? raw)
        {:help? true :usage usage}
        (normalize-exec-opts (main-opts->exec-opts raw)))
      (let [flag (first remaining)
            more (next remaining)]
        (case flag
          ("--help" "-h")
          (recur more (assoc raw :help? true))

          ("--runner" "-r")
          (let [value (require-value more flag)]
            (recur (next more) (assoc raw :runner value)))

          ("--dir" "-d")
          (let [value (require-value more flag)]
            (recur (next more) (add-repeat raw :dirs value)))

          ("--namespace" "--ns" "-n")
          (let [value (require-value more flag)]
            (recur (next more) (add-repeat raw :namespaces value)))

          ("--var" "-v")
          (let [value (require-value more flag)]
            (recur (next more) (add-repeat raw :vars value)))

          ("--ns-pattern" "--namespace-pattern" "--namespace-regex")
          (let [value (require-value more flag)]
            (when-let [previous (:ns-pattern-option raw)]
              (argument-error "Specify only one namespace pattern option"
                              {:options [previous flag]}))
            (recur (next more) (assoc raw
                                      :ns-pattern value
                                      :ns-pattern-option flag)))

          "--result-format"
          (let [value (require-value more flag)]
            (recur (next more)
                   (assoc raw :result-format (read-edn-option value :result-format))))

          ("--suite" "-s")
          (let [value (require-value more flag)]
            (when (:suites raw)
              (argument-error "Specify either --suite or --suites, not both"
                              {:options [:suite :suites]}))
            (recur (next more) (add-repeat raw :suite-values value)))

          "--suites"
          (let [value (require-value more flag)]
            (when (seq (:suite-values raw))
              (argument-error "Specify either --suite or --suites, not both"
                              {:options [:suite :suites]}))
            (recur (next more) (assoc raw :suites (read-edn-option value :suites))))

          "--config"
          (let [value (require-value more flag)]
            (recur (next more) (assoc raw :config (read-edn-option value :config))))

          (argument-error (str "Unknown option: " flag) {:option flag}))))))

;;; CLI execution

(defn- default-resolve-kaocha-runner
  []
  (requiring-resolve 'scry.kaocha/run))

(defn- default-boundary
  []
  {:out *out*
   :err *err*
   :cwd (System/getProperty "user.dir")
   :run-clojure-test clojure-test/run
   :resolve-kaocha-runner default-resolve-kaocha-runner})

(defn- boundary
  [io-boundary]
  (merge (default-boundary) (or io-boundary {})))

(defn- assertion-counts
  [entries]
  (reduce (fn [counts entry]
            (merge-with + counts (:assertion-summary entry)))
          {:pass 0 :fail 0 :error 0}
          entries))

(defn- var-counts
  [entries]
  (reduce (fn [counts entry]
            (update counts (:status entry) (fnil inc 0)))
          {:pass 0 :fail 0 :error 0 :unknown 0}
          entries))

(defn- runner-assertion-counts
  [result entries]
  (let [summary (:summary result)]
    (if (every? #(number? (get summary %)) [:pass :fail :error])
      {:pass (:pass summary)
       :fail (:fail summary)
       :error (:error summary)}
      (assertion-counts entries))))

(defn- cli-summary
  [result entries]
  {:assertions (runner-assertion-counts result entries)
   :tests (var-counts entries)
   :var-count (count entries)})

(defn- summary-text
  [{:keys [assertions tests]}]
  (str "Assertions: " (:pass assertions 0) " passed, "
       (:fail assertions 0) " failed, "
       (:error assertions 0) " errored\n"
       "Tests: " (:pass tests 0) " passed, "
       (:fail tests 0) " failed, "
       (:error tests 0) " errored"
       (when (pos? (:unknown tests 0))
         (str ", " (:unknown tests) " unknown"))
       "\n"))

(defn- progress-label
  [synthetic-counters entry]
  (if (results/concrete-var-backed-entry? entry)
    (name (:var entry))
    (let [status (:status entry)
          ordinal (swap! synthetic-counters update status (fnil inc 0))]
      (results/synthetic-display-label
       entry
       (results/synthetic-token status (get ordinal status))))))

(defn- progress!
  [synthetic-counters {:keys [out err]} entry]
  (case (:status entry)
    :pass (do (.write out ".") (.flush out))
    :fail (do (.write err (str (progress-label synthetic-counters entry) "\n")) (.flush err))
    :error (do (.write err (str (progress-label synthetic-counters entry) "\n")) (.flush err))
    (do (.write err (str (progress-label synthetic-counters entry) "\n")) (.flush err))))

(defn- write-summary!
  [{:keys [out]} summary]
  (.write out (summary-text summary))
  (.flush out))

(defn- positive-count?
  [m k]
  (pos? (long (or (get m k) 0))))

(defn- load-error-entry?
  [entry]
  (and (results/failure-entry? entry)
       (not (results/concrete-var-backed-entry? entry))))

(defn- concrete-failure-entry?
  [entry]
  (and (results/failure-entry? entry)
       (results/concrete-var-backed-entry? entry)))

(defn- aggregate-failure?
  [{:keys [assertions]}]
  (or (positive-count? assertions :fail)
      (positive-count? assertions :error)))

(defn- classify-outcome
  [result entries summary]
  (cond
    (some load-error-entry? entries)
    :scry.cli/load-error

    (or (some concrete-failure-entry? entries)
        (aggregate-failure? summary))
    :scry.cli/test-failure

    (some #(= :unknown (:status %)) entries)
    :scry.cli/unknown-result

    (not-any? results/concrete-var-backed-entry? entries)
    :scry.cli/zero-tests

    :else
    :scry.cli/pass))

(defn- exit-code
  [outcome-kind]
  (if (= :scry.cli/pass outcome-kind)
    0
    1))

(defn- error-outcome-kind
  [^Throwable e]
  (case (:type (ex-data e))
    :scry.cli/argument-error :scry.cli/argument-error
    :scry.cli/runner-error :scry.cli/runner-error
    :scry.cli/runner-error))

(def ^:private canonical-entry-statuses #{:pass :fail :error :unknown})

(defn- valid-canonical-entry?
  [entry]
  (and (map? entry)
       (contains? canonical-entry-statuses (:status entry))))

(defn- canonical-entry-error-data
  [index entry]
  {:type :scry.cli/runner-error
   :index index
   :entry entry})

(defn- canonical-result-entries
  [result]
  (let [entries (:canonical-results result)]
    (when-not (vector? entries)
      (throw (ex-info "Runner result did not include :canonical-results"
                      {:type :scry.cli/runner-error})))
    (doseq [[index entry] (map-indexed vector entries)]
      (when-not (valid-canonical-entry? entry)
        (throw (ex-info "Runner result included malformed canonical entry"
                        (canonical-entry-error-data index entry)))))
    entries))

(defn- runner-error
  ([message]
   (runner-error message nil))
  ([message cause]
   (throw (ex-info message
                   {:type :scry.cli/runner-error
                    :runner :kaocha}
                   cause))))

(defn- resolve-kaocha-runner
  [io-boundary]
  (let [resolver (:resolve-kaocha-runner io-boundary)
        runner (try
                 (resolver)
                 (catch java.io.FileNotFoundException e
                   (runner-error
                    "Kaocha CLI mode requires the optional scry.kaocha adapter on the classpath"
                    e))
                 (catch Throwable e
                   (runner-error "Could not load Kaocha CLI runner" e)))]
    (when-not (ifn? runner)
      (runner-error "Resolved Kaocha CLI runner is not invokable"))
    runner))

(defn- run-kaocha
  [opts io-boundary progress-callback]
  (let [run-kaocha (resolve-kaocha-runner io-boundary)]
    (run-kaocha (assoc opts :progress-callback progress-callback))))

(defn- run-normalized
  [opts io-boundary progress-callback]
  (case (:runner opts)
    :clojure-test
    ((:run-clojure-test io-boundary)
     (assoc opts :progress-callback progress-callback))

    :kaocha
    (run-kaocha opts io-boundary progress-callback)))

(defn run-cli
  "Run scry from normalized CLI options and return a structured outcome.

   Performs CLI output and .scry-results filesystem effects, but never calls
   System/exit. The optional io-boundary map may provide :out, :err, :cwd,
   :run-clojure-test, and :resolve-kaocha-runner for narrow state-based tests."
  ([normalized-options] (run-cli normalized-options nil))
  ([normalized-options io-boundary]
   (let [io-boundary (boundary io-boundary)]
     (try
       (let [dir (results/prepare-results-dir! io-boundary)
             synthetic-counters (atom {})
             progress-callback #(progress! synthetic-counters io-boundary %)
             result (run-normalized normalized-options io-boundary progress-callback)
             entries (canonical-result-entries result)
             summary (cli-summary result entries)
             result-files (results/write-result-files! dir entries)
             outcome-kind (classify-outcome result entries summary)
             code (exit-code outcome-kind)]
         (write-summary! io-boundary summary)
         {:exit-code code
          :scry.cli/outcome-kind outcome-kind
          :result result
          :summary summary
          :result-files result-files
          :error nil})
       (catch Throwable e
         (let [outcome-kind (error-outcome-kind e)]
           (.write (:err io-boundary) (str "scry CLI error: " (.getMessage e) "\n"))
           (.flush (:err io-boundary))
           {:exit-code (exit-code outcome-kind)
            :scry.cli/outcome-kind outcome-kind
            :result nil
            :summary nil
            :result-files []
            :error {:message (.getMessage e)
                    :data (ex-data e)
                    :exception e}}))))))

(defn- non-zero-exception
  [message outcome]
  (ex-info message
           {:type :scry.cli/non-zero
            :exit-code (:exit-code outcome)
            :scry.cli/outcome-kind (:scry.cli/outcome-kind outcome)
            :summary (:summary outcome)
            :error (:error outcome)
            :outcome outcome}))

(defn- argument-error-outcome
  [^Throwable e]
  {:exit-code 1
   :scry.cli/outcome-kind :scry.cli/argument-error
   :result nil
   :summary nil
   :result-files []
   :error {:message (.getMessage e)
           :data (ex-data e)
           :exception e}})

(defn run
  "`clojure -X` entry point for the scry CLI.

   Normalizes EDN options, runs the shared CLI implementation, and returns the
   successful outcome. Throws ex-info with structured outcome data when the CLI
   run is non-zero so `clojure -X` exits non-zero without calling System/exit."
  ([opts] (run opts nil))
  ([opts io-boundary]
   (try
     (let [normalized (normalize-exec-opts opts)
           outcome (run-cli normalized io-boundary)]
       (if (zero? (:exit-code outcome))
         outcome
         (throw (non-zero-exception "scry CLI run failed" outcome))))
     (catch clojure.lang.ExceptionInfo e
       (if (= :scry.cli/argument-error (:type (ex-data e)))
         (throw (non-zero-exception "scry CLI argument error"
                                    (argument-error-outcome e)))
         (throw e))))))

(defn main-outcome
  "Parse `-m scry.cli` args and run the shared CLI implementation.

   Returns an exit code and never calls System/exit. This exists so tests can
   cover main-style parsing and help behavior without terminating the process."
  ([args] (main-outcome args nil))
  ([args io-boundary]
   (let [io-boundary (boundary io-boundary)]
     (try
       (let [parsed (parse-main-args args)]
         (if (:help? parsed)
           (do
             (.write (:out io-boundary) (:usage parsed))
             (.write (:out io-boundary) "\n")
             (.flush (:out io-boundary))
             0)
           (:exit-code (run-cli parsed io-boundary))))
       (catch clojure.lang.ExceptionInfo e
         (if (= :scry.cli/argument-error (:type (ex-data e)))
           (do
             (.write (:err io-boundary) (str "scry CLI argument error: " (.getMessage e) "\n"))
             (.flush (:err io-boundary))
             1)
           (throw e)))))))

(defn -main
  "`clojure -M -m scry.cli` entry point."
  [& args]
  (System/exit (main-outcome args)))
