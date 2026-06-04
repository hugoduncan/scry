(ns scry.clojure-test
  "In-process runner for clojure.test, producing scry's inspectable result map.

   Test execution is delegated to `clojure.test/test-vars` (so :once/:each
   fixtures behave exactly as in a normal run) while `clojure.test/report` and
   *out*/*err* are bound to scry's capture machinery."
  (:require
   [clojure.java.io :as io]
   [clojure.test :as test]
   [clojure.tools.namespace.find :as ns-find]
   [scry.capture :as capture]))

(def ^:private default-ns-pattern
  "Default namespace-name pattern for test discovery."
  #".*-test$")

(defn- var-line
  [v]
  (or (:line (meta v)) 0))

(defn ns-test-vars
  "Return the test vars (vars carrying :test metadata) of a loaded namespace,
   ordered by source line for deterministic execution."
  [ns-sym]
  (->> (ns-interns ns-sym)
       vals
       (filter #(:test (meta %)))
       (sort-by (juxt var-line (comp str :name meta)))))

(defn discover-namespaces
  "Find namespace symbols under `dirs` whose names match `pattern`."
  [dirs pattern]
  (->> dirs
       (mapcat #(ns-find/find-namespaces-in-dir (io/file %)))
       (filter #(re-matches pattern (str %)))
       distinct))

(defn- resolve-vars
  "Resolve the sequence of test vars to run from `opts`.

   :vars        explicit collection of vars (filtered to those with :test)
   :namespaces  explicit collection of namespace symbols (required, then all
                test vars collected)
   otherwise    discover namespaces under :dirs (default [\"test\"]) matching
                :ns-pattern (default #\".*-test$\")"
  [{:keys [vars namespaces dirs ns-pattern]}]
  (cond
    (seq vars)
    (let [test-vars (filter #(:test (meta %)) vars)]
      (if (seq test-vars)
        test-vars
        (when (seq namespaces)
          (run! require namespaces)
          (mapcat ns-test-vars namespaces))))

    (seq namespaces)
    (do (run! require namespaces)
        (mapcat ns-test-vars namespaces))

    :else
    (let [nses (discover-namespaces (or (seq dirs) ["test"])
                                    (or ns-pattern default-ns-pattern))]
      (run! require nses)
      (mapcat ns-test-vars nses))))

(defn result-scope
  "Classify the result scope from original run options and executable vars.

   Explicit executable :vars take precedence. If explicit :vars resolve to no
   executable tests, classification falls back to an explicit namespace selector
   when present; discovered runs are always :suite."
  [{:keys [vars namespaces]} _vars-to-run]
  (let [explicit-test-var-count (when (seq vars)
                                  (count (filter #(:test (meta %)) vars)))]
    (cond
      (seq vars)
      (case explicit-test-var-count
        0 (if (= 1 (count namespaces)) :namespace :suite)
        1 :var
        :suite)

      (= 1 (count namespaces)) :namespace

      :else :suite)))

(defn run
  "Run clojure.test tests in-process and return an inspectable result map.

   Options:
     :vars           explicit vars to run
     :namespaces     namespace symbols to run
     :dirs           source dirs to scan for test namespaces (default [\"test\"])
     :ns-pattern     regex matched against namespace names during discovery
     :result-format  per-scope result formatting overrides

   Results use :results as the canonical collection and may include :failures
   as a filtered compatibility collection, depending on the selected format."
  ([] (run {}))
  ([opts]
   (let [state (capture/new-state)
         vars-to-run (vec (resolve-vars opts))
         scope (result-scope opts vars-to-run)
         start (System/nanoTime)]
     ;; Reset the testing context/var stacks so that running inside an
     ;; enclosing clojure.test run (e.g. scry's own tests) does not leak
     ;; ambient `testing` contexts into captured assertions.
     (binding [test/report (capture/report-fn state)
               test/*testing-contexts* (list)
               test/*testing-vars* (list)
               *out* (capture/routing-writer state :out)
               *err* (capture/routing-writer state :err)]
       (test/test-vars vars-to-run))
     (let [duration-ms (/ (- (System/nanoTime) start) 1e6)]
       (capture/build-result state {:duration-ms duration-ms
                                    :scope scope
                                    :result-format (:result-format opts)})))))
