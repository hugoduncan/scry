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
    (filter #(:test (meta %)) vars)

    (seq namespaces)
    (do (run! require namespaces)
        (mapcat ns-test-vars namespaces))

    :else
    (let [nses (discover-namespaces (or (seq dirs) ["test"])
                                    (or ns-pattern default-ns-pattern))]
      (run! require nses)
      (mapcat ns-test-vars nses))))

(defn run
  "Run clojure.test tests in-process and return an inspectable result map.

   Options:
     :vars        explicit vars to run
     :namespaces  namespace symbols to run
     :dirs        source dirs to scan for test namespaces (default [\"test\"])
     :ns-pattern  regex matched against namespace names during discovery

   Result map:
     :summary   {:test :pass :fail :error :duration-ms :var-count :fail-var-count}
     :pass?     true when no failures or errors
     :failures  [{:var :ns :status :assertions [...] :out :err}]
   where each assertion is {:type :message :expected :actual :file :line
   :contexts} (plus :stacktrace for errors)."
  ([] (run {}))
  ([opts]
   (let [state (capture/new-state)
         vars-to-run (resolve-vars opts)
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
       (capture/build-result state {:duration-ms duration-ms})))))
