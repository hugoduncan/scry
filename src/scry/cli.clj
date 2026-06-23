(ns scry.cli
  "Command-line and `clojure -X` entry points for scry."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [scry.capture :as capture]
   [scry.cli.results :as results]
   [scry.clojure-test :as clojure-test]))

(defn- usage-for
  "Build CLI usage text for a help request.

   With no runner (`nil`) the general help lists every option annotated by the
   mode it applies to. With a specific runner keyword the help lists only that
   runner's relevant options, without mode annotations, so `--help` is sensitive
   to an explicitly supplied `--runner`."
  [runner]
  (str/join
   "\n"
   (case runner
     :clojure-test
     ["Usage:"
      "  clojure -M:test -m scry.cli --runner clojure-test [options]"
      "  clojure -X:test scry.cli/run :runner :clojure-test"
      ""
      "Options:"
      "  -r, --runner RUNNER             clojure-test (default) or kaocha"
      "  -d, --dir DIR                   Test directory; repeatable"
      "  -n, --namespace, --ns NS        Test namespace; repeatable"
      "  -v, --var VAR                   Fully-qualified test var; repeatable"
      "      --ns-pattern REGEX          Namespace regex"
      "      --result-format EDN         Result-format map"
      "      --help                      Print this help"]

     :kaocha
     ["Usage:"
      "  clojure -M:test:kaocha -m scry.cli --runner kaocha [scry options] [kaocha args]..."
      "  clojure -X:test:kaocha scry.cli/run :runner :kaocha"
      ""
      "scry options:"
      "  -r, --runner RUNNER             clojure-test (default) or kaocha"
      "  -d, --dir DIR                   Test directory; repeatable"
      "      --result-format EDN         Result-format map"
      "      --config EDN                Kaocha config map"
      "      --help                      Print this help"
      ""
      "All other arguments — Kaocha options (e.g. --focus SYM, --no-randomize)"
      "and positional [SUITE]... selectors — are forwarded verbatim to Kaocha's"
      "own CLI parser. See Kaocha's own --help for its full option surface."]

     ["Usage:"
      "  clojure -M:test -m scry.cli [options]"
      "  clojure -X:test scry.cli/run :runner :clojure-test"
      ""
      "Options:"
      "  -r, --runner RUNNER             clojure-test (default) or kaocha"
      "  -d, --dir DIR                   Test directory; repeatable"
      "  -n, --namespace, --ns NS        Test namespace; repeatable, core mode only"
      "  -v, --var VAR                   Fully-qualified test var; repeatable, core mode only"
      "      --ns-pattern REGEX          Namespace regex, core mode only"
      "      --result-format EDN         Result-format map"
      "      --config EDN                Kaocha config map"
      "      --help                      Print this help"
      ""
      "Kaocha mode forwards all other arguments verbatim to Kaocha's own CLI"
      "parser:"
      "  [kaocha args]...                Kaocha options (e.g. --focus SYM,"
      "                                  --no-randomize) and positional [SUITE]..."
      "                                  selectors; Kaocha mode only"])))

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

;; Keys owned by scry that must never be forwarded to Kaocha as pass-through
;; (`:kaocha-extra`). Derived from the existing key sets so it stays in sync.
;; `:kaocha-extra` itself is included: it is the forwarded payload container,
;; not a key to collect, so the `-X` collection step must not re-collect an
;; already-present `:kaocha-extra` map into a nested one.
(def ^:private scry-managed-keys
  (into #{:runner :result-format :progress-callback :kaocha-extra :kaocha-argv :dirs}
        (concat core-only-keys kaocha-only-keys kaocha-fallback-keys)))

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

(defn- help-runner
  "Lenient runner resolution for `--help` output.

   Returns the canonical runner keyword for a recognized `--runner` value, or
   `nil` for an absent or unrecognized runner so general help is shown rather
   than failing a help request with an argument error."
  [runner]
  (some-> runner str/lower-case runner-aliases))

(defn- normalize-runner
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
  [opts option-keys message]
  (let [present-keys (filter #(present? opts %) option-keys)]
    (when (seq present-keys)
      (argument-error message {:options (vec present-keys)}))))

(defn- cli-result-format
  [result-format]
  (let [base (or result-format {})]
    (reduce (fn [fmt scope]
              (update-in fmt [scope :top-level-keys]
                         (fn [top-level-keys]
                           (-> (or top-level-keys
                                   (get-in capture/default-result-format
                                           [scope :top-level-keys]))
                               vec
                               (into [:summary :pass? :canonical-results])
                               distinct
                               vec))))
            base
            result-scopes)))

(defn- normalize-core-options
  [opts normalized]
  (reject-keys opts (conj (into kaocha-only-keys kaocha-fallback-keys) :kaocha-extra)
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
  (let [normalized (cond-> normalized
                     (present? opts :suite) (assoc :suite (:suite opts))
                     (present? opts :suites) (assoc :suites (normalize-suites (:suites opts)))
                     (present? opts :config) (assoc :config (:config opts))
                     (present? opts :dirs) (assoc :test-paths (normalize-dirs (:dirs opts)))
                     (present? opts :source-paths) (assoc :source-paths (:source-paths opts))
                     (present? opts :test-paths) (assoc :test-paths (:test-paths opts))
                     (present? opts :ns-patterns) (assoc :ns-patterns (:ns-patterns opts))
                     (present? opts :kaocha-argv) (assoc :kaocha-argv (:kaocha-argv opts)))
        ;; Collect every top-level key outside the scry-managed set as raw
        ;; pass-through. On `-X` these are scattered top-level keys (e.g.
        ;; `:focus`). On `-m` there is no `:kaocha-extra`: unknown Kaocha tokens
        ;; are forwarded opaquely as `:kaocha-argv` (in `scry-managed-keys`, so
        ;; never re-collected here) and parsed in the adapter. Collected
        ;; top-level extras win on conflict with a pre-existing `:kaocha-extra`.
        collected (into {} (remove (fn [[k _]] (contains? scry-managed-keys k)) opts))
        kaocha-extra (merge (:kaocha-extra opts) collected)]
    (cond-> normalized
      (seq kaocha-extra) (assoc :kaocha-extra kaocha-extra))))

(defn- normalize-exec-opts
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
                 (dissoc :suite-values :help? :ns-pattern-option :runner-option)
                 (cond->
                  (= 1 (count suite-values))
                   (assoc :suite (first suite-values))

                   (> (count suite-values) 1)
                   (assoc :suites (vec suite-values))))]
    opts))

(defn- argv-runner
  "Resolve the effective runner from raw `-m` argv before per-token parsing.

   `--runner`/`-r` may appear anywhere in argv, so a pre-pass is needed to decide
   whether unrecognized tokens are forwarded to Kaocha (`:kaocha`) or rejected as
   unknown options (core mode). Uses the lenient `help-runner` mapping and falls
   back to `:clojure-test` for an absent or unrecognized runner so the eventual
   `normalize-runner` still raises the argument error for a bad runner value."
  [args]
  (loop [remaining (seq args)]
    (if-not remaining
      :clojure-test
      (let [flag (first remaining)
            more (next remaining)]
        (if (contains? #{"--runner" "-r"} flag)
          (or (help-runner (first more)) :clojure-test)
          (recur more))))))

(defn- parse-main-args
  [args]
  (let [kaocha? (= :kaocha (argv-runner args))]
    (loop [remaining (seq args)
           raw {}]
      (if-not remaining
        (if (:help? raw)
          {:help? true :usage (usage-for (help-runner (:runner raw)))}
          (normalize-exec-opts (main-opts->exec-opts raw)))
        (let [flag (first remaining)
              more (next remaining)]
          (case flag
            ("--help" "-h")
            (recur more (assoc raw :help? true))

            ("--runner" "-r")
            (let [value (require-value more flag)]
              ;; Reject a repeated runner flag so the single argv-derived runner
              ;; stays authoritative. `argv-runner` resolves the forward/reject
              ;; mode from the first occurrence while `main-opts->exec-opts` would
              ;; otherwise execute under the last; rejecting the repeat keeps both
              ;; resolutions in agreement (mirrors the `--ns-pattern` guard).
              (when (:runner raw)
                (argument-error "Specify only one runner option"
                                {:options [(:runner-option raw) flag]}))
              (recur (next more) (assoc raw
                                        :runner value
                                        :runner-option flag)))

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

            "--config"
            (let [value (require-value more flag)]
              (recur (next more) (assoc raw :config (read-edn-option value :config))))

          ;; Default branch: a token that is not a scry-owned flag.
          ;;
          ;; In Kaocha mode every such token — unknown `--flags`, their values,
          ;; and positional suite names — is forwarded verbatim, in original
          ;; order, as `:kaocha-argv` for Kaocha's own CLI parser. scry never
          ;; interprets these tokens; only Kaocha knows their arities.
          ;;
          ;; In core mode a token starting with `-` is an unknown-option argument
          ;; error; any other token is collected, in order, as a positional that
          ;; `main-opts->exec-opts` collapses and `normalize-core-options`
          ;; rejects, preserving today's behaviour.
            (if kaocha?
              (recur more (add-repeat raw :kaocha-argv flag))
              (if (str/starts-with? flag "-")
                (argument-error (str "Unknown option: " flag) {:option flag})
                (recur more (add-repeat raw :suite-values flag))))))))))

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
    :pass (when (results/concrete-var-backed-entry? entry)
            (.write out ".")
            (.flush out))
    :fail (do (.write err (str (progress-label synthetic-counters entry) "\n")) (.flush err))
    :error (do (.write err (str (progress-label synthetic-counters entry) "\n")) (.flush err))
    (do (.write err (str (progress-label synthetic-counters entry) "\n")) (.flush err))))

(defn- write-summary!
  [{:keys [out]} summary]
  (.write out (summary-text summary))
  (.flush out))

(defn- write-error-summary!
  "Surface a minimal, explicit stdout summary for an error/exception CLI outcome
   that produced no real run summary (`:summary nil`).

   These outcomes — `:scry.cli/runner-error` (the `run-cli` catch path) and
   `:scry.cli/argument-error` (the argument-error path) — otherwise leave stdout
   silent, ending in-progress output with no closing line. This writes a single
   clearly-labelled line naming `outcome-kind` so the run is never silent on
   stdout. The wording is deliberately not a \"0 passed, 0 failed\" green run.

   This is supplementary human-facing output only; the authoritative machine
   signals (`:scry.cli/outcome-kind`, exit code, `.scry-results/*.edn`) and the
   returned outcome map's `nil` `:summary` are unchanged."
  [{:keys [out]} outcome-kind]
  (.write out (str "No tests run — scry CLI error outcome: " outcome-kind "\n"))
  (.flush out))

(defn- write-seed!
  "Surface a runner-provided random seed (e.g. Kaocha's randomize seed, exposed
   as `:summary :seed`) on stdout after the summary so a failing order can be
   reproduced. Called only for failing outcomes, mirroring Kaocha's own
   failure-only seed reporting."
  [{:keys [out]} seed]
  (.write out (str "Randomized with --seed " seed "\n"))
  (.flush out))

(defn- root-cause-throwable
  [^Throwable t]
  (loop [t t]
    (if-let [cause (.getCause t)]
      (recur cause)
      t)))

(defn- throwable-cause-text
  [^Throwable t]
  (let [root (root-cause-throwable t)]
    (str (.getName (class root))
         (when-let [message (.getMessage root)]
           (str ": " message)))))

(defn- assertion-cause-text
  "Human root-cause text for a load-error assertion's `:actual`.

   `:actual` is a live Throwable when produced directly by the Kaocha adapter,
   or an edn-ified `Throwable->map` shape (with `:via`/`:cause`) when rehydrated.
   Returns nil when no usable cause is present."
  [{:keys [actual]}]
  (cond
    (instance? Throwable actual)
    (throwable-cause-text actual)

    (map? actual)
    (let [root (last (:via actual))
          klass (:type root)
          message (or (:message root) (:cause actual))]
      (when (or klass message)
        (str (when klass (str klass))
             (when (and klass message) ": ")
             (when message (str message)))))

    :else nil))

(defn- load-error-detail
  "One-line load-error detail: the failing assertion message plus its root-cause
   class/message, derived from the synthetic entry's first assertion."
  [entry]
  (let [assertion (first (:assertions entry))
        parts (remove str/blank? [(:message assertion)
                                  (assertion-cause-text assertion)])]
    (when (seq parts)
      (str/join " — " parts))))

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
  [entries summary]
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

(def ^:private failure-outcome-kinds
  #{:scry.cli/load-error :scry.cli/test-failure :scry.cli/unknown-result})

(defn- results-dir-pointer
  [dir result-files]
  (let [n (count result-files)]
    (str "See " (.getPath ^java.io.File dir) " for failure details"
         (when (pos? n)
           (str " (" n " file" (when (> n 1) "s") ")"))
         ".\n")))

(defn- write-failure-diagnostic!
  "Surface a failing outcome on stderr without changing the stdout summary.

   Writes a short pointer at the results directory for any failure outcome kind.
   For a load error it first writes the failing entry's message and root-cause
   class/message inline so the failure is visible without opening the EDN file.
   Authoritative machine signals (outcome-kind, exit code, result files) are
   unchanged."
  [{:keys [err] :as _boundary} dir entries result-files outcome-kind]
  (when (contains? failure-outcome-kinds outcome-kind)
    (when (= :scry.cli/load-error outcome-kind)
      (when-let [detail (some-> (first (filter load-error-entry? entries))
                                load-error-detail)]
        (.write err (str "Load error: " detail "\n"))))
    (.write err (results-dir-pointer dir result-files))
    (.flush err)))

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
  [boundary]
  (let [resolver (:resolve-kaocha-runner boundary)
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
  [opts boundary progress-callback]
  (let [kaocha-runner (resolve-kaocha-runner boundary)]
    (kaocha-runner (assoc opts :progress-callback progress-callback))))

(defn- run-normalized
  [opts boundary progress-callback]
  (case (:runner opts)
    :clojure-test
    ((:run-clojure-test boundary)
     (assoc opts :progress-callback progress-callback))

    :kaocha
    (run-kaocha opts boundary progress-callback)))

(defn- run-cli
  [normalized-options boundary]
  (try
    (let [dir (results/prepare-results-dir! boundary)
          synthetic-counters (atom {})
          progress-callback #(progress! synthetic-counters boundary %)
          result (run-normalized normalized-options boundary progress-callback)
          entries (canonical-result-entries result)
          summary (cli-summary result entries)
          result-files (results/write-result-files! dir entries)
          outcome-kind (classify-outcome entries summary)
          code (exit-code outcome-kind)]
      (write-summary! boundary summary)
      (when-let [seed (and (contains? failure-outcome-kinds outcome-kind)
                           (get-in result [:summary :seed]))]
        (write-seed! boundary seed))
      (write-failure-diagnostic! boundary dir entries result-files outcome-kind)
      {:exit-code code
       :scry.cli/outcome-kind outcome-kind
       :result result
       :summary summary
       :result-files result-files
       :error nil})
    (catch Throwable e
      (let [outcome-kind (error-outcome-kind e)]
        (write-error-summary! boundary outcome-kind)
        (.write (:err boundary) (str "scry CLI error: " (.getMessage e) "\n"))
        (.flush (:err boundary))
        {:exit-code (exit-code outcome-kind)
         :scry.cli/outcome-kind outcome-kind
         :result nil
         :summary nil
         :result-files []
         :error {:message (.getMessage e)
                 :data (ex-data e)
                 :exception e}}))))

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

(defn- run-with-boundary
  [opts boundary]
  (try
    (let [normalized (normalize-exec-opts opts)
          outcome (run-cli normalized boundary)]
      (if (zero? (:exit-code outcome))
        outcome
        (throw (non-zero-exception "scry CLI run failed" outcome))))
    (catch clojure.lang.ExceptionInfo e
      (if (= :scry.cli/argument-error (:type (ex-data e)))
        (do
          (write-error-summary! boundary :scry.cli/argument-error)
          (throw (non-zero-exception "scry CLI argument error"
                                     (argument-error-outcome e))))
        (throw e)))))

(defn run
  "`clojure -X` entry point for the scry CLI.

   Normalizes EDN options, runs the shared CLI implementation, and returns the
   successful outcome. Throws ex-info with structured outcome data when the CLI
   run is non-zero so `clojure -X` exits non-zero without calling System/exit."
  [opts]
  (run-with-boundary opts (default-boundary)))

(defn- main-outcome
  [args boundary]
  (try
    (let [parsed (parse-main-args args)]
      (if (:help? parsed)
        (do
          (.write (:out boundary) (:usage parsed))
          (.write (:out boundary) "\n")
          (.flush (:out boundary))
          0)
        (:exit-code (run-cli parsed boundary))))
    (catch clojure.lang.ExceptionInfo e
      (if (= :scry.cli/argument-error (:type (ex-data e)))
        (do
          (write-error-summary! boundary :scry.cli/argument-error)
          (.write (:err boundary) (str "scry CLI argument error: " (.getMessage e) "\n"))
          (.flush (:err boundary))
          1)
        (throw e)))))

(defn ^:no-doc -main
  [& args]
  (System/exit (main-outcome args (default-boundary))))
