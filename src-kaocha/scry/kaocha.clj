(ns scry.kaocha
  "In-process kaocha runner producing scry's inspectable result map.

   Kaocha already captures per-test clojure.test events and (via its
   capture-output plugin) per-test output. This adapter runs kaocha
   programmatically and transforms its result tree into scry's result model.

   Note: kaocha merges stdout and stderr into a single captured stream, so for
   kaocha results the combined output is placed in :out and :err is empty."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test]
   [clojure.tools.cli :as cli]
   [kaocha.api :as api]
   [kaocha.config :as config]
   [kaocha.plugin :as plugin]
   [kaocha.type :as type]
   [scry.capture :as capture]))

(defn- leaf-testables
  "Return the leaf testables (those with no nested tests) of a kaocha result
   tree node sequence."
  [testables]
  (mapcat (fn [t]
            (if-let [subs (seq (:kaocha.result/tests t))]
              (leaf-testables subs)
              [t]))
          testables))

(defn- status
  [fail error pass]
  (cond
    (pos? error) :error
    (pos? fail) :fail
    (pos? pass) :pass
    :else :unknown))

(defn- testable->entry
  "Convert a leaf testable into a canonical scry result entry."
  [t]
  (let [var-name (:kaocha.var/name t)
        events (:kaocha.testable/events t)
        assertions (mapv (fn [e]
                           (capture/assertion
                            e
                            (vec (reverse (:testing-contexts e)))))
                         (filter #(#{:pass :fail :error} (:type %)) events))
        pass (:kaocha.result/pass t 0)
        fail (:kaocha.result/fail t 0)
        error (:kaocha.result/error t 0)]
    {:var var-name
     :ns (some-> var-name namespace symbol)
     :status (status fail error pass)
     :assertion-summary {:pass pass :fail fail :error error}
     :assertions assertions
     :out (or (:kaocha.plugin.capture-output/output t) "")
     :err ""}))

(defn- result->scry
  ([kaocha-result] (result->scry kaocha-result nil))
  ([kaocha-result extra]
   (let [leaves (filterv #(not (:kaocha.testable/skip %))
                         (leaf-testables (:kaocha.result/tests kaocha-result)))
         entries (mapv testable->entry leaves)
         sum (fn [k] (reduce + 0 (map #(get-in % [:assertion-summary k] 0)
                                      entries)))
         fail (sum :fail)
         error (sum :error)
         failures (filterv #(contains? #{:fail :error} (:status %)) entries)
         scope (or (:scope extra) :suite)
         result-format (:result-format extra)
         result {:summary (merge {:test (count leaves)
                                  :pass (sum :pass)
                                  :fail fail
                                  :error error
                                  :var-count (count leaves)
                                  :fail-var-count (count failures)}
                                 (dissoc extra :scope :result-format))
                 :pass? (zero? (+ fail error))
                 :canonical-results entries}]
     (capture/format-result result scope result-format))))

(def ^:private default-config
  {:source-paths ["src"]
   :test-paths ["test"]
   :ns-patterns ["-test$"]})

(defn- tests-edn-file
  []
  (io/file (System/getProperty "user.dir") "tests.edn"))

(defn- tests-edn-exists?
  []
  (.exists (tests-edn-file)))

(defn- absolutize-paths
  ([paths]
   (absolutize-paths (System/getProperty "user.dir") paths))
  ([base-dir paths]
   (mapv #(let [file (io/file %)]
            (if (.isAbsolute file)
              (.getPath file)
              (.getPath (io/file base-dir %))))
         paths)))

(defn- absolutize-suite-paths
  [base-dir suite]
  (-> suite
      (update :kaocha/source-paths #(some->> % (absolutize-paths base-dir)))
      (update :kaocha/test-paths #(some->> % (absolutize-paths base-dir)))))

(defn- absolutize-config-paths
  [base-dir cfg]
  (update cfg :kaocha/tests #(mapv (partial absolutize-suite-paths base-dir) %)))

(defn- load-config-file
  "Load a Kaocha config file and absolutize its suite paths relative to the
   config file's own directory."
  [file]
  (let [file (.getAbsoluteFile (io/file file))]
    (absolutize-config-paths (.getParentFile file) (config/load-config file))))

(defn- load-tests-edn-config
  []
  (load-config-file (tests-edn-file)))

(defn- build-fallback-config
  "Build a normalized synthetic kaocha config map from scry opts."
  [opts]
  (let [{:keys [source-paths test-paths ns-patterns]} (merge default-config opts)]
    (config/normalize
     {:kaocha/tests [{:kaocha.testable/id :unit
                      :kaocha.testable/type :kaocha.type/clojure.test
                      :kaocha/source-paths (absolutize-paths source-paths)
                      :kaocha/test-paths (absolutize-paths test-paths)
                      :kaocha/ns-patterns ns-patterns}]})))

(defn- forwarded-config-file
  "Detect an explicitly forwarded Kaocha `--config-file`/`-c` value in a raw
   `-m` argv vector. `clojure.tools.cli` always injects the `tests.edn`
   default for `:config-file`, so an explicitly forwarded value must be
   detected from the raw argv rather than the parsed option. Returns the
   explicit path string, or nil when none was forwarded."
  [argv]
  (loop [tokens (seq argv)]
    (when-let [token (first tokens)]
      (cond
        (or (= token "--config-file") (= token "-c"))
        (second tokens)

        (str/starts-with? token "--config-file=")
        (subs token (count "--config-file="))

        (and (str/starts-with? token "-c") (> (count token) 2))
        (subs token 2)

        :else (recur (next tokens))))))

(defn- resolve-config
  [opts cfg-file]
  (cond
    (contains? opts :config) (:config opts)
    cfg-file (load-config-file cfg-file)
    (tests-edn-exists?) (load-tests-edn-config)
    :else (build-fallback-config opts)))

(defn- plugin-id
  [plugin]
  (if (map? plugin)
    (:kaocha.plugin/id plugin)
    plugin))

(defn- ensure-plugin
  [plugins plugin-keyword]
  (let [plugins (vec plugins)]
    (cond-> plugins
      (not (some #{plugin-keyword} (map plugin-id plugins)))
      (conj plugin-keyword))))

(defn- ensure-runtime-plugins
  "Ensure the plugins scry relies on are present. The capture-output plugin
   provides per-test output; the filter plugin translates forwarded
   `:kaocha/cli-options` (e.g. `:focus`) into `:kaocha.filter/*` directives.
   Synthetic fallback and bare explicit `:config` maps may omit Kaocha's default
   plugin chain, so ensure both here."
  [plugins]
  (-> plugins
      (ensure-plugin :kaocha.plugin/capture-output)
      (ensure-plugin :kaocha.plugin/filter)))

(defn- apply-runtime-defaults
  [cfg]
  (-> cfg
      (update :kaocha/plugins ensure-runtime-plugins)
      (assoc :kaocha/reporter []
             :kaocha/color? false)))

(defn- ->focus-keyword
  "Coerce a single raw `:focus` value to a keyword, mirroring the Kaocha filter
   plugin's `--focus` parse semantics (a leading `:` on a string is stripped)."
  [v]
  (cond
    (keyword? v) v
    (symbol? v) (keyword v)
    (string? v) (keyword (if (str/starts-with? v ":") (subs v 1) v))
    :else (keyword (str v))))

(defn- coerce-focus
  "Coerce a raw `:focus` value (scalar or collection of string/symbol/keyword)
   into a vector of keywords, the shape the filter plugin's cli-option parse
   produces."
  [focus]
  (mapv ->focus-keyword
        (if (and (sequential? focus) (not (string? focus)))
          focus
          [focus])))

(defn- coerce-kaocha-extra
  "Coerce known raw `:kaocha-extra` values to the types the Kaocha cli-options
   layer expects. Unknown keys are forwarded as-is (the documented `-X`
   mistyped-key trade-off)."
  [extra]
  (cond-> extra
    (contains? extra :focus) (update :focus coerce-focus)))

(defn- apply-kaocha-extra
  "Merge raw forwarded `:kaocha-extra` into the resolved config's
   `:kaocha/cli-options`, coercing known values first. Existing config
   cli-options are authoritative on conflict (OQ2 merge-with-config-wins)."
  [cfg extra]
  (if (seq extra)
    (update cfg :kaocha/cli-options
            (fn [cli-options]
              (merge (coerce-kaocha-extra extra) cli-options)))
    cfg))

(def ^:private default-cli-spec-plugins
  "Kaocha's default plugin chain. Included when building the forwarded-argv
   option spec so the standard Kaocha flag surface (e.g. `--focus`,
   `--[no-]randomize`) is always parseable as a drop-in, even for synthetic
   fallback configs that omit Kaocha's default plugins. A flag only takes effect
   if its plugin is active during the run, but it should never fail to parse."
  [:kaocha.plugin/randomize
   :kaocha.plugin/filter
   :kaocha.plugin/capture-output])

(defn- kaocha-cli-option-spec
  "Build the full `clojure.tools.cli` option spec for forwarded `-m` argv:
   Kaocha's own base runner spec extended by the config's plugins (plus Kaocha's
   default plugins) `:kaocha.hooks/cli-options`, mirroring `kaocha.runner/-main*`.
   Using Kaocha's own spec is what makes scry a drop-in for Kaocha's CLI: arity
   and parsing of every Kaocha option are decided by Kaocha, not re-implemented
   here."
  [cfg]
  (let [base @(requiring-resolve 'kaocha.runner/cli-options)
        plugin-chain (plugin/load-all (concat (:kaocha/plugins cfg)
                                              default-cli-spec-plugins))]
    (plugin/run-hook* plugin-chain :kaocha.hooks/cli-options base)))

(defn- parse-kaocha-argv
  "Parse a forwarded raw `-m` Kaocha argv vector with Kaocha's own CLI machinery.

   Returns `{:cli-options <parsed options minus the :config-file default>
             :suites <positional selectors coerced to keywords>}`. Throws
   `ex-info` when Kaocha's parser reports option errors (the accepted `-m`
   unknown-flag reclassification to a runner/load error)."
  [cfg argv]
  (let [{:keys [options arguments errors]} (cli/parse-opts argv (kaocha-cli-option-spec cfg))]
    (when (seq errors)
      (throw (ex-info (str "Invalid Kaocha CLI arguments: " (str/join "; " errors))
                      {:kaocha-argv (vec argv) :errors errors})))
    {:cli-options (dissoc options :config-file)
     :suites (mapv (requiring-resolve 'kaocha.runner/parse-kw) arguments)}))

(defn- event-var-symbol
  [event]
  (when-let [v (:var event)]
    (symbol (str (ns-name (:ns (meta v))))
            (str (:name (meta v))))))

(defn- progress-reporter
  [callback]
  (let [current-var (atom nil)
        counts (atom {})]
    (fn [event]
      (case (:type event)
        :begin-test-var
        (let [var-symbol (event-var-symbol event)]
          (reset! current-var var-symbol)
          (swap! counts assoc var-symbol {:pass 0 :fail 0 :error 0}))

        :pass
        (when-let [var-symbol @current-var]
          (swap! counts update-in [var-symbol :pass] (fnil inc 0)))

        :fail
        (when-let [var-symbol @current-var]
          (swap! counts update-in [var-symbol :fail] (fnil inc 0)))

        :error
        (if-let [var-symbol @current-var]
          (swap! counts update-in [var-symbol :error] (fnil inc 0))
          ;; A suite-level error with no enclosing test var (e.g. a namespace
          ;; load/compile failure) emits :error between :kaocha/begin-suite and
          ;; :kaocha/end-suite with no test-var events. Fire the callback
          ;; immediately so the synthetic suite-level progress label is printed
          ;; during the run instead of being silently collapsed to a count.
          (callback {:var nil
                     :ns nil
                     :status :error
                     :assertion-summary {:pass 0 :fail 0 :error 1}}))

        :end-test-var
        (let [var-symbol (event-var-symbol event)
              {:keys [pass fail error] :as summary} (get @counts var-symbol
                                                         {:pass 0 :fail 0 :error 0})]
          (callback {:var var-symbol
                     :ns (some-> var-symbol namespace symbol)
                     :status (status fail error pass)
                     :assertion-summary summary})
          (reset! current-var nil))

        nil))))

(defn- apply-progress-reporter
  [cfg progress-callback]
  (cond-> cfg
    progress-callback
    (update :kaocha/reporter conj (progress-reporter progress-callback))))

(defn- discarding-writer
  "A writer that discards everything written to it, backed by a null output
   stream so nothing accumulates in memory.

   Used to bind `*out*`/`*err*` around `api/run` so Kaocha framework-level direct
   prints (e.g. the randomize plugin's `post-run` \"Randomized with --seed N\"
   line, which bypasses the reporter) do not leak onto scry's clean output
   stream. scry's progress callback and CLI summary write to the boundary stream
   objects, not these dynamic vars, and Kaocha's capture-output plugin rebinds
   `*out*` per test, so neither is affected."
  ^java.io.Writer []
  (java.io.PrintWriter.
   (java.io.OutputStreamWriter. (java.io.OutputStream/nullOutputStream))))

(defn- valid-suites-collection?
  [suites]
  (and (coll? suites)
       (not (string? suites))
       (not (map? suites))
       (seq suites)))

(defn- suite-selectors
  [{:keys [suite suites]}]
  (when (and (some? suite) (some? suites))
    (throw (ex-info "Supply either :suite or :suites, not both."
                    {:suite suite
                     :suites suites})))
  (cond
    (some? suite) [suite]
    (nil? suites) nil
    (valid-suites-collection? suites) (vec suites)
    :else (throw (ex-info ":suites must be a non-empty collection of suite selectors; use :suite for a single selector."
                          {:suites suites}))))

(defn- suite-ids
  [cfg]
  (mapv :kaocha.testable/id (:kaocha/tests cfg)))

(defn- comparable-text
  [x]
  (cond
    (string? x) x
    (or (keyword? x) (symbol? x)) (name x)
    :else nil))

(defn- resolve-suite-selector
  [available-ids selector]
  (if (some #(= selector %) available-ids)
    selector
    (let [selector-text (comparable-text selector)
          matches (if selector-text
                    (filterv #(= selector-text (comparable-text %)) available-ids)
                    [])]
      (case (count matches)
        0 (throw (ex-info "Unknown Kaocha suite selector."
                          {:selector selector
                           :available-suite-ids available-ids}))
        1 (first matches)
        (throw (ex-info "Ambiguous Kaocha suite selector."
                        {:selector selector
                         :matching-suite-ids matches
                         :available-suite-ids available-ids}))))))

(defn- select-suites
  [cfg selectors]
  (if (seq selectors)
    (let [available-ids (suite-ids cfg)
          selected-ids (mapv #(resolve-suite-selector available-ids %) selectors)
          selected? (set selected-ids)]
      (-> cfg
          (config/apply-cli-args selected-ids)
          (update :kaocha/tests
                  (fn [tests]
                    (mapv (fn [test]
                            (if (selected? (:kaocha.testable/id test))
                              (dissoc test :kaocha.testable/skip)
                              test))
                          tests)))))
    cfg))

(defn run
  "Run kaocha tests in-process and return scry's inspectable result map.

   Options:
     :config             a fully-formed kaocha config map (overrides loading tests.edn)
     :suite              a single suite selector
     :suites             a collection of suite selectors
     :source-paths       fallback source dirs when no :config or tests.edn exists
     :test-paths         fallback test dirs when no :config or tests.edn exists
     :ns-patterns        fallback namespace-name regex strings
     :result-format      suite-scope formatting overrides
     :progress-callback  optional function called after each completed test var
     :kaocha-argv        a vector of raw `-m` CLI strings forwarded verbatim by
                         the scry CLI in Kaocha mode (every token that is not a
                         scry-owned flag: unknown `--flags`, their values, and
                         positional suite names). They are parsed with Kaocha's
                         own CLI machinery (its `tools.cli` spec plus active
                         plugins' option hooks); parsed cli-options are merged
                         like `:kaocha-extra` (resolved `:config` authoritative
                         on conflict) and positional selectors are routed through
                         the same `:suite`/`:suites` resolution. Malformed Kaocha
                         options surface as a runner/load error rather than an
                         argument error. A forwarded `--config-file`/`-c` value
                         loads that Kaocha config (resolved `:config` still wins);
                         only the parser-injected `tests.edn` default is dropped.
                         This option is `-m`-only; the `-X` map path uses
                         `:kaocha-extra`.
     :kaocha-extra       a map of raw Kaocha cli-options forwarded by the scry
                         CLI's bounded pass-through (e.g. `:focus`). It is merged
                         into the resolved config's :kaocha/cli-options with the
                         resolved :config authoritative on conflict. Known values
                         are coerced (`:focus` raw string/symbol/keyword scalar or
                         collection becomes a vector of keywords); unknown keys are
                         forwarded as-is, so a mistyped key surfaces as a runner or
                         load error rather than an argument error.

   When :config is omitted, the current project's tests.edn is loaded if it
   exists; otherwise a synthetic :unit suite is built from :source-paths,
   :test-paths, and :ns-patterns.

   Suite selectors match configured suite ids by exact value first, then by
   unique text (`\"string\"` ids/selectors as-is, keywords and symbols by
   `name`). Use :suite for a single selector; :suites must be a non-empty
   collection. Unknown or ambiguous selectors, and supplying both :suite and
   :suites, throw `ex-info`.

   The adapter defaults to suite scope because its public options do not mirror
   the namespace/var selectors of `scry.core/run`. Kaocha's capture-output
   plugin merges stdout and stderr, so combined output is placed in :out and
   :err is empty.

   When Kaocha randomizes test order (its default), the randomize seed is
   surfaced as `:seed` in the result `:summary` so a failing order can be
   reproduced; the framework's own stray \"Randomized with --seed N\" stdout
   print is suppressed.

   Returns the same scoped result model as `scry.core/run`."
  ([] (run {}))
  ([opts]
   (let [argv (:kaocha-argv opts)
         base-cfg (resolve-config opts (forwarded-config-file argv))
         parsed-argv (when (seq argv) (parse-kaocha-argv base-cfg argv))
         selectors (concat (suite-selectors opts) (:suites parsed-argv))
         cfg (-> base-cfg
                 (select-suites selectors)
                 apply-runtime-defaults
                 (apply-kaocha-extra (:kaocha-extra opts))
                 (apply-kaocha-extra (:cli-options parsed-argv))
                 (apply-progress-reporter (:progress-callback opts)))
         start (System/nanoTime)
         kaocha-result (capture/without-context
                        (binding [clojure.test/*report-counters* (ref type/initial-counters)
                                  *out* (discarding-writer)
                                  *err* (discarding-writer)]
                          (api/run cfg)))
         duration-ms (/ (- (System/nanoTime) start) 1e6)
         ;; Surface Kaocha's randomize seed (present only when randomization was
         ;; active) as structured run metadata, replacing the framework's stray
         ;; "Randomized with --seed N" stdout print that the *out* binding above
         ;; suppresses.
         seed (:kaocha.plugin.randomize/seed kaocha-result)]
     (result->scry kaocha-result (cond-> {:duration-ms duration-ms
                                          :scope :suite
                                          :result-format (:result-format opts)}
                                   (some? seed) (assoc :seed seed))))))
