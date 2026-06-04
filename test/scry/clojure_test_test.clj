(ns scry.clojure-test-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.clojure-test :as ct]
   [scry.core :as scry]
   [scry.fixtures.asserting-fixtures]
   [scry.fixtures.erroring]
   [scry.fixtures.failing]
   [scry.fixtures.fixtured]
   [scry.fixtures.mixed]
   [scry.fixtures.nested]
   [scry.fixtures.output]
   [scry.fixtures.output-fixtures]
   [scry.fixtures.passing]
   [scry.fixtures.raw-nested-allowlisted]
   [scry.fixtures.raw-nested-fixtures]
   [scry.fixtures.raw-nested-outer]
   [scry.fixtures.short-circuiting-fixtures]))

(defn not-a-test
  []
  :not-a-test)

(deftest invokes-nested-scry-run-test
  (println "outer before")
  (let [inner-result (ct/run {:vars [#'scry.fixtures.nested/inner-failing-test]})]
    (is (false? (:pass? inner-result)))
    (is (= 'scry.fixtures.nested/inner-failing-test
           (:var (first (:results inner-result)))))
    (is (= "inner stdout\n" (:out (first (:results inner-result)))))
    (is (= "inner stderr\n" (:err (first (:results inner-result))))))
  (println "outer after"))

(defn- failure-for
  "Return the failure entry for a fully-qualified test var symbol."
  [result var-sym]
  (->> (:failures result)
       (filter #(= var-sym (:var %)))
       first))

(deftest run-summary-test
  ;; Running multiple namespaces produces compact suite results with accurate
  ;; counts and an overall pass? flag.
  (testing "run over mixed fixture namespaces"
    (let [result (ct/run {:namespaces ['scry.fixtures.passing
                                       'scry.fixtures.failing
                                       'scry.fixtures.erroring]})]
      (testing "summary counts reflect every assertion"
        (is (= 4 (get-in result [:summary :test])))
        (is (= 1 (get-in result [:summary :fail])))
        (is (= 1 (get-in result [:summary :error]))))
      (testing "pass? is false when there are failures"
        (is (false? (:pass? result))))
      (testing "suite results are compact failing/erroring entries"
        (is (= 2 (get-in result [:summary :fail-var-count])))
        (is (= #{'scry.fixtures.failing/equality-fails
                 'scry.fixtures.erroring/throws-exception}
               (set (map :var (:results result)))))
        (is (every? #(= #{:var :ns :status :assertion-summary}
                        (set (keys %)))
                    (:results result)))))))

(deftest passing-run-is-detailed-for-single-namespace-test
  ;; A single namespace run includes passing vars and passing assertion detail.
  (testing "all-passing namespace"
    (let [result (ct/run {:namespaces ['scry.fixtures.passing]})
          entry (first (:results result))]
      (is (true? (:pass? result)))
      (is (empty? (:failures result)))
      (is (= 0 (get-in result [:summary :fail-var-count])))
      (is (= 'scry.fixtures.passing/arithmetic-passes (:var entry)))
      (is (= :pass (:status entry)))
      (is (= [:pass :pass] (mapv :type (:assertions entry))))
      (is (not (contains? entry :out)))
      (is (not (contains? entry :err))))))

(deftest captures-assertion-detail-test
  ;; Failed assertions retain expected/actual forms, message, location, and
  ;; the active testing contexts (outermost first).
  (testing "failing assertion detail"
    (let [result (ct/run {:namespaces ['scry.fixtures.failing]})
          failure (failure-for result 'scry.fixtures.failing/equality-fails)
          assertion (first (:assertions failure))]
      (is (= :fail (:status failure)))
      (is (= :fail (:type assertion)))
      (is (= "one is not two" (:message assertion)))
      (is (= '(= 1 2) (:expected assertion)))
      (is (= '(not (= 1 2)) (:actual assertion)))
      (is (= ["outer context" "inner context"] (:contexts assertion)))
      (is (= "failing.clj" (:file assertion)))
      (is (pos-int? (:line assertion))))))

(deftest captures-error-detail-test
  ;; Errors capture status :error and a rendered stacktrace string.
  (testing "erroring test detail"
    (let [result (ct/run {:namespaces ['scry.fixtures.erroring]})
          failure (failure-for result 'scry.fixtures.erroring/throws-exception)
          assertion (first (:assertions failure))]
      (is (= :error (:status failure)))
      (is (= :error (:type assertion)))
      (is (instance? ArithmeticException (:actual assertion)))
      (is (string? (:stacktrace assertion)))
      (is (re-find #"Divide by zero" (:stacktrace assertion))))))

(deftest captures-output-for-single-var-only-test
  ;; stdout/stderr is included by default for single-var scope but omitted from
  ;; single-namespace scope entries.
  (testing "single var output capture"
    (let [result (ct/run {:vars [#'scry.fixtures.output/noisy-and-fails]})
          entry (first (:results result))]
      (is (= "stdout from failing test\n" (:out entry)))
      (is (= "stderr from failing test\n" (:err entry)))
      (is (= [entry] (:failures result)))))
  (testing "single namespace omits output keys"
    (let [result (ct/run {:namespaces ['scry.fixtures.output]})]
      (is (every? #(not (contains? % :out)) (:results result)))
      (is (nil? (failure-for result 'scry.fixtures.output/noisy-and-passes))))))

(deftest multi-var-output-is-isolated-test
  ;; Detailed custom formatting shows stdout/stderr belong only to the var that
  ;; produced them in multi-var runs.
  (testing "per-var output buffers"
    (let [result (ct/run {:vars [#'scry.fixtures.output/noisy-and-passes
                                 #'scry.fixtures.output/noisy-and-fails]
                          :result-format
                          {:suite {:top-level-keys [:summary :results :failures :canonical-results]
                                   :entry-keys [:var :status :out :err]
                                   :output? true}}})
          by-var (into {} (map (juxt :var identity) (:canonical-results result)))]
      (is (= "stdout from passing test\n"
             (get-in by-var ['scry.fixtures.output/noisy-and-passes :out])))
      (is (= "" (get-in by-var ['scry.fixtures.output/noisy-and-passes :err])))
      (is (= "stdout from failing test\n"
             (get-in by-var ['scry.fixtures.output/noisy-and-fails :out])))
      (is (= "stderr from failing test\n"
             (get-in by-var ['scry.fixtures.output/noisy-and-fails :err]))))))

(deftest fixture-output-ownership-test
  ;; :each fixture output is part of the owning var, while :once fixture output
  ;; has no public var-level result field and stays out of formatted entries.
  (testing "fixture output ownership"
    (let [result (ct/run {:namespaces ['scry.fixtures.output-fixtures]
                          :result-format
                          {:namespace {:entry-keys [:var :status :out]
                                       :output? true}}})
          by-var (into {} (map (juxt :var identity) (:results result)))]
      (is (= "each setup\nfirst body\neach teardown\n"
             (get-in by-var ['scry.fixtures.output-fixtures/first-output-test :out])))
      (is (= "each setup\nsecond body\neach teardown\n"
             (get-in by-var ['scry.fixtures.output-fixtures/second-output-test :out])))
      (is (not (str/includes?
                (apply str (map :out (:results result)))
                "once setup")))
      (is (not (str/includes?
                (apply str (map :out (:results result)))
                "once teardown"))))))

(deftest short-circuiting-each-fixture-does-not-create-var-entry-test
  ;; A short-circuiting :each fixture preserves clojure.test semantics: the
  ;; test var never begins, so no public unknown entry or progress item appears.
  (testing "skipped by :each fixture"
    (reset! scry.fixtures.short-circuiting-fixtures/events [])
    (let [progress (atom [])
          result (ct/run {:namespaces ['scry.fixtures.short-circuiting-fixtures]
                          :progress-callback #(swap! progress conj %)})]
      (is (true? (:pass? result)))
      (is (= [:fixture-ran]
             @scry.fixtures.short-circuiting-fixtures/events))
      (is (= 0 (get-in result [:summary :test])))
      (is (= 1 (get-in result [:summary :pass])))
      (is (= 0 (get-in result [:summary :var-count])))
      (is (= [] (:results result)))
      (is (= [] (:failures result)))
      (is (= [] @progress)))))

(deftest fixture-assertion-ownership-test
  ;; :each fixture assertions before and after test-var belong to the owning
  ;; var, while :once assertions affect run counts without creating var entries.
  (let [original-once-setup-pass? @scry.fixtures.asserting-fixtures/once-setup-pass?
        original-once-teardown-pass? @scry.fixtures.asserting-fixtures/once-teardown-pass?
        original-each-setup-pass? @scry.fixtures.asserting-fixtures/each-setup-pass?
        original-each-teardown-pass? @scry.fixtures.asserting-fixtures/each-teardown-pass?]
    (try
      (testing ":each fixture failures fail the owning var"
        (reset! scry.fixtures.asserting-fixtures/once-setup-pass? true)
        (reset! scry.fixtures.asserting-fixtures/once-teardown-pass? true)
        (reset! scry.fixtures.asserting-fixtures/each-setup-pass? false)
        (reset! scry.fixtures.asserting-fixtures/each-teardown-pass? false)
        (let [result (ct/run {:namespaces ['scry.fixtures.asserting-fixtures]})
              entry (first (:results result))]
          (is (false? (:pass? result)))
          (is (= 1 (get-in result [:summary :test])))
          (is (= 3 (get-in result [:summary :pass])))
          (is (= 2 (get-in result [:summary :fail])))
          (is (= :fail (:status entry)))
          (is (= 'scry.fixtures.asserting-fixtures/fixture-assertion-test
                 (:var entry)))
          (is (= ["each setup assertion is attributed to the var"
                  "each teardown assertion is attributed to the var"]
                 (mapv :message (filter #(= :fail (:type %)) (:assertions entry)))))))
      (testing ":once fixture assertions are counted but remain outside var entries"
        (reset! scry.fixtures.asserting-fixtures/once-setup-pass? false)
        (reset! scry.fixtures.asserting-fixtures/once-teardown-pass? false)
        (reset! scry.fixtures.asserting-fixtures/each-setup-pass? true)
        (reset! scry.fixtures.asserting-fixtures/each-teardown-pass? true)
        (let [result (ct/run {:namespaces ['scry.fixtures.asserting-fixtures]})
              entry (first (:results result))]
          (is (false? (:pass? result)))
          (is (= 1 (get-in result [:summary :test])))
          (is (= 3 (get-in result [:summary :pass])))
          (is (= 2 (get-in result [:summary :fail])))
          (is (= 0 (get-in result [:summary :fail-var-count])))
          (is (empty? (:failures result)))
          (is (= 'scry.fixtures.asserting-fixtures/fixture-assertion-test
                 (:var entry)))
          (is (= :pass (:status entry)))
          (is (empty? (filter #(= :fail (:type %)) (:assertions entry))))))
      (finally
        (reset! scry.fixtures.asserting-fixtures/once-setup-pass?
                original-once-setup-pass?)
        (reset! scry.fixtures.asserting-fixtures/once-teardown-pass?
                original-once-teardown-pass?)
        (reset! scry.fixtures.asserting-fixtures/each-setup-pass?
                original-each-setup-pass?)
        (reset! scry.fixtures.asserting-fixtures/each-teardown-pass?
                original-each-teardown-pass?)))))

(deftest nested-scry-run-is-isolated-test
  ;; A nested scry run owns its inner failure/output; the outer run captures only
  ;; the outer var's assertions and explicit output.
  (testing "inner scry run does not leak into outer capture"
    (reset! scry.fixtures.nested/nested-inner-events [])
    (let [result (ct/run {:vars [#'invokes-nested-scry-run-test]})
          entry (first (:results result))]
      (is (true? (:pass? result)))
      (is (= [:inner-ran] @scry.fixtures.nested/nested-inner-events))
      (is (= 'scry.clojure-test-test/invokes-nested-scry-run-test (:var entry)))
      (is (= 1 (get-in result [:summary :test])))
      (is (= 4 (get-in result [:summary :pass])))
      (is (= 0 (get-in result [:summary :fail])))
      (is (= "outer before\nouter after\n" (:out entry)))
      (is (= "" (:err entry)))
      (is (= #{'scry.clojure-test-test/invokes-nested-scry-run-test}
             (set (map :var (:results result))))))))

(deftest raw-nested-non-owned-clojure-test-is-ignored-test
  ;; Raw nested clojure.test execution for non-selected vars is treated as
  ;; non-owned implementation detail and does not alter the outer scry result.
  (testing "non-owned raw clojure.test var events are ignored"
    (reset! scry.fixtures.nested/raw-nested-events [])
    (let [result (ct/run {:vars [#'scry.fixtures.raw-nested-outer/invokes-raw-nested-clojure-test-test]})
          entry (first (:results result))]
      (is (true? (:pass? result)))
      (is (= [:raw-ran] @scry.fixtures.nested/raw-nested-events))
      (is (= 1 (get-in result [:summary :test])))
      (is (= 1 (get-in result [:summary :pass])))
      (is (= 0 (get-in result [:summary :fail])))
      (is (= "outer raw before\nouter raw after\n" (:out entry)))
      (is (= #{'scry.fixtures.raw-nested-outer/invokes-raw-nested-clojure-test-test}
             (set (map :var (:results result)))))))
  (testing "non-owned raw clojure.test namespace fixture events are ignored"
    (reset! scry.fixtures.raw-nested-fixtures/events [])
    (let [result (ct/run {:vars [#'scry.fixtures.raw-nested-outer/invokes-raw-nested-fixtured-clojure-test-test]})
          entry (first (:results result))]
      (is (true? (:pass? result)))
      (is (= [:once-setup :test-body :once-teardown]
             @scry.fixtures.raw-nested-fixtures/events))
      (is (= 1 (get-in result [:summary :test])))
      (is (= 1 (get-in result [:summary :pass])))
      (is (= 0 (get-in result [:summary :fail])))
      (is (= "outer raw fixture before\nouter raw fixture after\n" (:out entry)))
      (is (= "" (:err entry)))
      (is (= #{'scry.fixtures.raw-nested-outer/invokes-raw-nested-fixtured-clojure-test-test}
             (set (map :var (:results result)))))))
  (testing "non-owned raw clojure.test frames suppress nested allow-listed vars"
    (reset! scry.fixtures.raw-nested-allowlisted/events [])
    (let [result (ct/run {:vars [#'scry.fixtures.raw-nested-outer/invokes-raw-nested-allow-listed-helper-test
                                 #'scry.fixtures.raw-nested-allowlisted/allow-listed-helper-test]
                          :result-format
                          {:suite {:top-level-keys [:summary :pass? :canonical-results]
                                   :entry-keys [:var :status :out]
                                   :output? true}}})
          by-var (into {} (map (juxt :var identity) (:canonical-results result)))]
      (is (true? (:pass? result)))
      (is (= [:wrapper-ran :helper-ran :helper-ran]
             @scry.fixtures.raw-nested-allowlisted/events))
      (is (= 2 (get-in result [:summary :test])))
      (is (= 2 (get-in result [:summary :pass])))
      (is (= 0 (get-in result [:summary :fail])))
      (is (= #{'scry.fixtures.raw-nested-outer/invokes-raw-nested-allow-listed-helper-test
               'scry.fixtures.raw-nested-allowlisted/allow-listed-helper-test}
             (set (keys by-var))))
      (is (= "outer raw allow-listed before\nouter raw allow-listed after\n"
             (get-in by-var ['scry.fixtures.raw-nested-outer/invokes-raw-nested-allow-listed-helper-test :out])))
      (is (= "allow-listed helper output\n"
             (get-in by-var ['scry.fixtures.raw-nested-allowlisted/allow-listed-helper-test :out])))))
  (testing "non-owned raw clojure.test run-tests keeps its own summary"
    (let [result (ct/run {:vars [#'scry.fixtures.raw-nested-outer/invokes-raw-nested-run-tests-test]})
          entry (first (:results result))]
      (is (true? (:pass? result)))
      (is (= 1 (get-in result [:summary :test])))
      (is (= 1 (get-in result [:summary :pass])))
      (is (= 0 (get-in result [:summary :fail])))
      (is (= "outer raw run-tests before\nouter raw run-tests after\n"
             (:out entry)))
      (is (= #{'scry.fixtures.raw-nested-outer/invokes-raw-nested-run-tests-test}
             (set (map :var (:results result)))))))
  (testing "non-owned raw clojure.test test-vars keeps its own counters"
    (let [result (ct/run {:vars [#'scry.fixtures.raw-nested-outer/invokes-raw-nested-test-vars-counters-test]})
          entry (first (:results result))]
      (is (true? (:pass? result)))
      (is (= 1 (get-in result [:summary :test])))
      (is (= 1 (get-in result [:summary :pass])))
      (is (= 0 (get-in result [:summary :fail])))
      (is (= "outer raw test-vars counters before\nouter raw test-vars counters after\n"
             (:out entry)))
      (is (= #{'scry.fixtures.raw-nested-outer/invokes-raw-nested-test-vars-counters-test}
             (set (map :var (:results result)))))))
  (testing "non-owned raw clojure.test test-var keeps its own counters"
    (let [result (ct/run {:vars [#'scry.fixtures.raw-nested-outer/invokes-raw-nested-test-var-counters-test]})
          entry (first (:results result))]
      (is (true? (:pass? result)))
      (is (= 1 (get-in result [:summary :test])))
      (is (= 1 (get-in result [:summary :pass])))
      (is (= 0 (get-in result [:summary :fail])))
      (is (= "outer raw test-var counters before\nouter raw test-var counters after\n"
             (:out entry)))
      (is (= "" (:err entry)))
      (is (= #{'scry.fixtures.raw-nested-outer/invokes-raw-nested-test-var-counters-test}
             (set (map :var (:results result))))))))

(deftest run-explicit-vars-test
  ;; A run can target explicit vars, ignoring non-test vars.
  (testing "explicit multiple :vars use suite scope"
    (let [result (ct/run {:vars [#'scry.fixtures.failing/equality-fails
                                 #'scry.fixtures.passing/arithmetic-passes]})]
      (is (= 2 (get-in result [:summary :test])))
      (is (= 1 (get-in result [:summary :fail-var-count])))
      (is (= 1 (count (:results result))))))
  (testing "explicit single executable var uses var scope despite namespaces"
    (let [result (ct/run {:vars [#'not-a-test
                                 #'scry.fixtures.passing/arithmetic-passes]
                          :namespaces ['scry.fixtures.failing]})]
      (is (= 1 (count (:results result))))
      (is (contains? (first (:results result)) :out))))
  (testing "non-test vars fall back to namespace classification"
    (let [result (ct/run {:vars [#'not-a-test]
                          :namespaces ['scry.fixtures.passing]})]
      (is (= :pass (:status (first (:results result)))))
      (is (contains? (first (:results result)) :assertions))
      (is (not (contains? (first (:results result)) :out)))
      (is (not (contains? (first (:results result)) :err))))))

(deftest discovered-single-namespace-is-suite-scope-test
  ;; Discovery intent remains suite scope even when discovery finds one ns.
  (testing "discovery with one matching fixture namespace"
    (let [result (ct/run {:dirs ["test/scry/fixtures"]
                          :ns-pattern #"scry\.fixtures\.passing"})]
      (is (empty? (:results result)))
      (is (= 1 (get-in result [:summary :var-count]))))))

(deftest progress-callback-test
  ;; The optional progress callback reports each completed var in execution
  ;; order with final canonical status and error-over-fail precedence.
  (testing "progress callback entries"
    (let [entries (atom [])
          result (ct/run {:vars [#'scry.fixtures.passing/arithmetic-passes
                                 #'scry.fixtures.mixed/pass-then-fail
                                 #'scry.fixtures.mixed/fail-then-error]
                          :progress-callback #(swap! entries conj %)})]
      (is (= [:pass :fail :error] (mapv :status @entries)))
      (is (= ['scry.fixtures.passing/arithmetic-passes
              'scry.fixtures.mixed/pass-then-fail
              'scry.fixtures.mixed/fail-then-error]
             (mapv :var @entries)))
      (is (= {:pass 1 :fail 1 :error 0}
             (:assertion-summary (second @entries))))
      (is (= {:pass 0 :fail 1 :error 1}
             (:assertion-summary (nth @entries 2))))
      (is (= 3 (get-in result [:summary :test])))))
  (testing "callback writes are not appended to public var output"
    (let [callback-out (java.io.StringWriter.)
          callback-err (java.io.StringWriter.)
          result (binding [*out* callback-out
                           *err* callback-err]
                   (ct/run {:vars [#'scry.fixtures.output-fixtures/first-output-test]
                            :progress-callback
                            (fn [_]
                              (println "callback stdout")
                              (binding [*out* *err*]
                                (println "callback stderr")))}))
          entry (first (:results result))]
      (is (= "each setup\nfirst body\neach teardown\n" (:out entry)))
      (is (= "" (:err entry)))
      (is (= "callback stdout\n" (str callback-out)))
      (is (= "callback stderr\n" (str callback-err))))))

(deftest custom-formatting-test
  ;; Result formatting can be configured independently per scope using
  ;; state-based assertions on returned data.
  (testing "custom suite top-level and entry keys"
    (let [result (ct/run {:namespaces ['scry.fixtures.failing
                                       'scry.fixtures.erroring]
                          :result-format
                          {:suite {:top-level-keys [:summary :results]
                                   :entry-keys [:var :status :assertions]
                                   :assertions? true}}})]
      (is (= #{:summary :results} (set (keys result))))
      (is (every? #(contains? % :assertions) (:results result)))))
  (testing "custom namespace enables output"
    (let [result (ct/run {:namespaces ['scry.fixtures.output]
                          :result-format
                          {:namespace {:entry-keys [:var :status]
                                       :output? true}}})]
      (is (every? #(contains? % :out) (:results result)))))
  (testing "custom var disables assertions and output"
    (let [result (ct/run {:vars [#'scry.fixtures.output/noisy-and-fails]
                          :result-format
                          {:var {:entry-keys [:var :status :assertions :out]
                                 :assertions? false
                                 :output? false}}})]
      (is (= #{:var :status} (set (keys (first (:results result)))))))))

(deftest core-helper-compatibility-test
  ;; Public helpers prefer :failures, fall back to filtered :results, and
  ;; tolerate formats that omit both result collections.
  (testing "helpers on scoped defaults"
    (let [result (scry/run {:vars [#'scry.fixtures.output/noisy-and-fails]})]
      (is (= result (scry/last-result)))
      (is (= 1 (count (scry/failures result))))
      (is (= 'scry.fixtures.output/noisy-and-fails
             (:var (scry/failed-test result 'scry.fixtures.output/noisy-and-fails))))
      (is (= {:out "stdout from failing test\n"
              :err "stderr from failing test\n"}
             (scry/output result 'scry.fixtures.output/noisy-and-fails)))
      (is (re-find #"fail: scry.fixtures.output/noisy-and-fails"
                   (scry/report-string result)))))
  (testing "helpers fall back to results and tolerate omitted collections"
    (let [result {:summary {:test 1 :pass 0 :fail 1 :error 0}
                  :pass? false
                  :results [{:var 'x/y :status :fail}]}]
      (is (= [{:var 'x/y :status :fail}] (scry/failures result)))
      (is (nil? (scry/output {:summary {:test 0 :pass 0 :fail 0 :error 0}}
                             'x/y))))))

(deftest fixtures-are-honoured-test
  ;; clojure.test :once/:each fixtures run in the correct order through scry's
  ;; local fixture-preserving loop around clojure.test/test-var.
  (testing ":once and :each fixtures"
    (require 'scry.fixtures.fixtured)
    (let [events @(deref (resolve 'scry.fixtures.fixtured/events))]
      (reset! (deref (resolve 'scry.fixtures.fixtured/events)) [])
      (ct/run {:namespaces ['scry.fixtures.fixtured]})
      (is (= [:once-start :each-start :each-end :each-start :each-end :once-end]
             @(deref (resolve 'scry.fixtures.fixtured/events)))
          (str "previous events: " events)))))
