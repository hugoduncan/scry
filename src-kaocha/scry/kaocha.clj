(ns scry.kaocha
  "In-process kaocha runner producing scry's inspectable result map.

   Kaocha already captures per-test clojure.test events and (via its
   capture-output plugin) per-test output. This adapter runs kaocha
   programmatically and transforms its result tree into scry's result model.

   Note: kaocha merges stdout and stderr into a single captured stream, so for
   kaocha results the combined output is placed in :out and :err is empty."
  (:require
   [kaocha.api :as api]
   [kaocha.config :as config]
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

(defn result->scry
  "Transform a raw kaocha result map into scry's inspectable result shape.

   `extra` may supply additional :summary entries (e.g. :duration-ms), :scope,
   and :result-format. Kaocha defaults to :suite scope because this adapter does
   not currently expose namespace/var selectors matching scry.clojure-test."
  ([kaocha-result] (result->scry kaocha-result nil))
  ([kaocha-result extra]
   (let [leaves (leaf-testables (:kaocha.result/tests kaocha-result))
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

(defn- build-config
  "Build a normalized kaocha config map from scry opts."
  [opts]
  (let [{:keys [source-paths test-paths ns-patterns]} (merge default-config opts)]
    (config/normalize
     {:kaocha/tests [{:kaocha.testable/id :unit
                      :kaocha.testable/type :kaocha.type/clojure.test
                      :kaocha/source-paths source-paths
                      :kaocha/test-paths test-paths
                      :kaocha/ns-patterns ns-patterns}]
      :kaocha/plugins [:kaocha.plugin/capture-output]
      :kaocha/reporter []
      :kaocha/color? false})))

(defn run
  "Run kaocha tests in-process and return scry's inspectable result map.

   Options:
     :config         a fully-formed kaocha config map (overrides the rest)
     :source-paths   source dirs (default [\"src\"])
     :test-paths     test dirs (default [\"test\"])
     :ns-patterns    namespace-name regex strings (default [\"-test$\"])
     :result-format  suite-scope formatting overrides

   Returns the same scoped result model as `scry.core/run`."
  ([] (run {}))
  ([opts]
   (let [cfg (or (:config opts) (build-config opts))
         start (System/nanoTime)
         kaocha-result (api/run cfg)
         duration-ms (/ (- (System/nanoTime) start) 1e6)]
     (result->scry kaocha-result {:duration-ms duration-ms
                                  :scope :suite
                                  :result-format (:result-format opts)}))))
