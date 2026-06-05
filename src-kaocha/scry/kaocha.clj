(ns scry.kaocha
  "In-process kaocha runner producing scry's inspectable result map.

   Kaocha already captures per-test clojure.test events and (via its
   capture-output plugin) per-test output. This adapter runs kaocha
   programmatically and transforms its result tree into scry's result model.

   Note: kaocha merges stdout and stderr into a single captured stream, so for
   kaocha results the combined output is placed in :out and :err is empty."
  (:require
   [clojure.java.io :as io]
   [clojure.test]
   [kaocha.api :as api]
   [kaocha.config :as config]
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
  [cfg]
  (let [base-dir (.getParentFile (tests-edn-file))]
    (update cfg :kaocha/tests #(mapv (partial absolutize-suite-paths base-dir) %))))

(defn- load-tests-edn-config
  []
  (absolutize-config-paths (config/load-config (tests-edn-file))))

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

(defn- resolve-config
  [opts]
  (cond
    (contains? opts :config) (:config opts)
    (tests-edn-exists?) (load-tests-edn-config)
    :else (build-fallback-config opts)))

(defn- plugin-id
  [plugin]
  (if (map? plugin)
    (:kaocha.plugin/id plugin)
    plugin))

(defn- ensure-capture-output-plugin
  [plugins]
  (let [plugins (vec plugins)]
    (cond-> plugins
      (not (some #{:kaocha.plugin/capture-output} (map plugin-id plugins)))
      (conj :kaocha.plugin/capture-output))))

(defn- apply-runtime-defaults
  [cfg]
  (-> cfg
      (update :kaocha/plugins ensure-capture-output-plugin)
      (assoc :kaocha/reporter []
             :kaocha/color? false)))

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
        (when-let [var-symbol @current-var]
          (swap! counts update-in [var-symbol :error] (fnil inc 0)))

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

   Returns the same scoped result model as `scry.core/run`."
  ([] (run {}))
  ([opts]
   (let [cfg (-> opts
                 resolve-config
                 (select-suites (suite-selectors opts))
                 apply-runtime-defaults
                 (apply-progress-reporter (:progress-callback opts)))
         start (System/nanoTime)
         kaocha-result (capture/without-context
                        (binding [clojure.test/*report-counters* (ref type/initial-counters)]
                          (api/run cfg)))
         duration-ms (/ (- (System/nanoTime) start) 1e6)]
     (result->scry kaocha-result {:duration-ms duration-ms
                                  :scope :suite
                                  :result-format (:result-format opts)}))))
