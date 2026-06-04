(ns scry.clojure-test-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [scry.clojure-test :as ct]
   [scry.core :as scry]
   [scry.fixtures.erroring]
   [scry.fixtures.failing]
   [scry.fixtures.fixtured]
   [scry.fixtures.mixed]
   [scry.fixtures.output]
   [scry.fixtures.passing]))

(defn not-a-test
  []
  :not-a-test)

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
      (is (= 3 (get-in result [:summary :test]))))))

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
  ;; clojure.test :once/:each fixtures run in the correct order, delegated to
  ;; clojure.test/test-vars.
  (testing ":once and :each fixtures"
    (require 'scry.fixtures.fixtured)
    (let [events @(deref (resolve 'scry.fixtures.fixtured/events))]
      (reset! (deref (resolve 'scry.fixtures.fixtured/events)) [])
      (ct/run {:namespaces ['scry.fixtures.fixtured]})
      (is (= [:once-start :each-start :each-end :each-start :each-end :once-end]
             @(deref (resolve 'scry.fixtures.fixtured/events)))
          (str "previous events: " events)))))
