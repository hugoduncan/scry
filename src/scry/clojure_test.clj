(ns scry.clojure-test
  "In-process runner for clojure.test, producing scry's inspectable result map.

   Test execution preserves clojure.test's namespace grouping and :once/:each
   fixture behavior while binding scry capture around the run. Each invocation
   installs a fresh dynamically scoped capture context so nested scry runs own
   their own events and output."
  (:require
   [clojure.java.io :as io]
   [clojure.test :as test]
   [clojure.tools.namespace.find :as ns-find]
   [scry.capture :as capture]))

(def ^:private default-ns-pattern
  "Default namespace-name pattern for test discovery."
  #".*-test$")

(def ^:private base-report
  "The clojure.test report function present before scry installs its hook."
  test/report)

(def ^:dynamic *owned-test-var-invocation*
  "Var currently being invoked by scry's own fixture loop via clojure.test/test-var.

   Other test-var calls observed during a scry run are user/raw nested
   execution and should keep their own clojure.test counters while remaining
   isolated from the outer scry capture."
  nil)

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

(defn- non-owned-raw-test-vars?
  "Return true when a raw nested clojure.test/test-vars call should not report
   through the current scry context.

   scry's own runner uses a local fixture loop and calls test-var directly, so
   test-vars calls observed during a run are user/raw nested execution. If any
   requested var is outside this run's executable allow-list, run that raw call
   with scry capture disabled so namespace-level fixtures before/after
   :begin-test-var cannot inherit the enclosing output owner."
  [intended-vars vars]
  (let [intended (set intended-vars)]
    (boolean (some (complement intended) vars))))

(defn- run-with-non-owned-raw-clojure-test-boundary
  [f enclosing-report-counters]
  (let [already-disabled? (capture/context-disabled?)
        run-raw (fn []
                  (if (identical? test/*report-counters*
                                  enclosing-report-counters)
                    (binding [test/*report-counters* (ref test/*initial-report-counters*)]
                      (f))
                    (f)))]
    (capture/without-context
     (with-redefs [test/report base-report]
       (if already-disabled?
         (run-raw)
         (binding [test/*test-out* (java.io.StringWriter.)
                   *out* (java.io.StringWriter.)
                   *err* (java.io.StringWriter.)]
           (run-raw)))))))

(defn- run-non-owned-raw-test-var
  [test-var v enclosing-report-counters]
  (run-with-non-owned-raw-clojure-test-boundary
   #(test-var v)
   enclosing-report-counters))

(defn- run-non-owned-raw-test-vars
  [test-vars vars enclosing-report-counters]
  (run-with-non-owned-raw-clojure-test-boundary
   #(test-vars vars)
   enclosing-report-counters))

(defn- run-non-owned-raw-run-tests
  [run-tests test-vars namespaces]
  (let [already-disabled? (capture/context-disabled?)
        run-raw-tests (fn []
                        (with-redefs [test/report base-report
                                      test/test-vars test-vars]
                          (apply run-tests namespaces)))]
    (capture/without-context
     (if already-disabled?
       (run-raw-tests)
       (binding [test/*test-out* (java.io.StringWriter.)
                 *out* (java.io.StringWriter.)
                 *err* (java.io.StringWriter.)]
         (run-raw-tests))))))

(defn- test-vars-with-output-owners
  "Run vars with clojure.test fixture semantics and per-var output ownership.

   This mirrors clojure.test/test-vars for the project Clojure version, but
   wraps each :each fixture + test-var invocation with a scry output owner. This
   keeps :each setup/teardown output with its var and leaves :once output as
   run/orphan output."
  [context vars progress-callback]
  (doseq [[ns vars] (group-by (comp :ns meta) vars)]
    (let [once-fixture-fn (test/join-fixtures (::test/once-fixtures (meta ns)))
          each-fixture-fn (test/join-fixtures (::test/each-fixtures (meta ns)))]
      (once-fixture-fn
       (fn []
         (doseq [v vars]
           (when (:test (meta v))
             (capture/with-output-owner
               context
               v
               (fn []
                 (each-fixture-fn
                  (fn []
                    (binding [*owned-test-var-invocation* v]
                      (test/test-var v))))))
             (when-let [entry (and progress-callback
                                   (capture/var-result context v))]
               (capture/without-context
                (progress-callback entry))))))))))

(defn run
  "Run clojure.test tests in-process and return an inspectable result map.

   Options:
     :vars               explicit vars to run
     :namespaces         namespace symbols to run
     :dirs               source dirs to scan for test namespaces (default [\"test\"])
     :ns-pattern         regex matched against namespace names during discovery
     :result-format      per-scope result formatting overrides
     :progress-callback  optional function called with each canonical var entry
                         after that var's final status is known

   Results use :results as the canonical collection and may include :failures
   as a filtered compatibility collection, depending on the selected format."
  ([] (run {}))
  ([opts]
   (let [vars-to-run (vec (resolve-vars opts))
         context (capture/new-context {:intended-vars vars-to-run})
         scope (result-scope opts vars-to-run)
         report (capture/report-fn)
         progress-callback (:progress-callback opts)
         start (System/nanoTime)]
     ;; Reset the testing context/var stacks so that running inside an
     ;; enclosing clojure.test run (e.g. scry's own tests) does not leak
     ;; ambient `testing` contexts into captured assertions.
     (capture/with-context context
       ;; Use a root redefinition rather than a dynamic binding for
       ;; clojure.test/report. Kaocha installs its reporter with with-redefs;
       ;; a dynamic report binding here would shadow Kaocha's reporter in nested
       ;; adapter runs and prevent the adapter from collecting assertion events.
       (let [enclosing-report-counters test/*report-counters*]
         (with-redefs [test/report report
                       test/run-tests (let [run-tests test/run-tests
                                            test-vars test/test-vars]
                                        (fn [& namespaces]
                                          (run-non-owned-raw-run-tests
                                           run-tests
                                           test-vars
                                           namespaces)))
                       test/test-var (let [test-var test/test-var]
                                       (fn [v]
                                         (if (= *owned-test-var-invocation* v)
                                           (test-var v)
                                           (run-non-owned-raw-test-var
                                            test-var
                                            v
                                            enclosing-report-counters))))
                       test/test-vars (let [test-vars test/test-vars]
                                        (fn [vars]
                                          (if (non-owned-raw-test-vars?
                                               vars-to-run
                                               vars)
                                            (run-non-owned-raw-test-vars
                                             test-vars
                                             vars
                                             enclosing-report-counters)
                                            (test-vars vars))))]
           (binding [test/*testing-contexts* (list)
                     test/*testing-vars* (list)
                     *out* (capture/routing-writer :out)
                     *err* (capture/routing-writer :err)]
             (test-vars-with-output-owners context vars-to-run progress-callback)))))
     (let [duration-ms (/ (- (System/nanoTime) start) 1e6)]
       (capture/build-result context {:duration-ms duration-ms
                                      :scope scope
                                      :result-format (:result-format opts)})))))
