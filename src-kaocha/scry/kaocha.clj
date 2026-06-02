(ns scry.kaocha
  "In-process kaocha runner producing scry's inspectable result map.

   Kaocha already captures per-test clojure.test events and (via its
   capture-output plugin) per-test output. This adapter runs kaocha
   programmatically and transforms its result tree into the same result shape
   produced by `scry.clojure-test/run`, so callers get a uniform structure.

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

(defn- testable->failure
  "Convert a failed/errored leaf testable into a scry failure entry, or nil if
   it neither failed nor errored."
  [t]
  (let [fail (:kaocha.result/fail t 0)
        error (:kaocha.result/error t 0)]
    (when (pos? (+ fail error))
      (let [var-name (:kaocha.var/name t)
            events (:kaocha.testable/events t)
            assertions (->> events
                            (filter #(#{:fail :error} (:type %)))
                            (mapv (fn [e]
                                    (capture/assertion
                                     e
                                     (vec (reverse (:testing-contexts e)))))))]
        {:var var-name
         :ns (some-> var-name namespace symbol)
         :status (if (pos? error) :error :fail)
         :assertions assertions
         :out (or (:kaocha.plugin.capture-output/output t) "")
         :err ""}))))

(defn result->scry
  "Transform a raw kaocha result map into scry's inspectable result shape.

   `extra` may supply additional :summary entries (e.g. :duration-ms)."
  ([kaocha-result] (result->scry kaocha-result nil))
  ([kaocha-result extra]
   (let [leaves (leaf-testables (:kaocha.result/tests kaocha-result))
         sum (fn [k] (reduce + 0 (map #(get % k 0) leaves)))
         fail (sum :kaocha.result/fail)
         error (sum :kaocha.result/error)
         failures (vec (keep testable->failure leaves))]
     {:summary (merge {:test (count leaves)
                       :pass (sum :kaocha.result/pass)
                       :fail fail
                       :error error
                       :var-count (count leaves)
                       :fail-var-count (count failures)}
                      extra)
      :pass? (zero? (+ fail error))
      :failures failures})))

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
     :config        a fully-formed kaocha config map (overrides the rest)
     :source-paths  source dirs (default [\"src\"])
     :test-paths    test dirs (default [\"test\"])
     :ns-patterns   namespace-name regex strings (default [\"-test$\"])

   Returns the same result shape as `scry.clojure-test/run`."
  ([] (run {}))
  ([opts]
   (let [cfg (or (:config opts) (build-config opts))
         start (System/nanoTime)
         kaocha-result (api/run cfg)
         duration-ms (/ (- (System/nanoTime) start) 1e6)]
     (result->scry kaocha-result {:duration-ms duration-ms}))))
