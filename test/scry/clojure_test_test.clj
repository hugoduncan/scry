(ns scry.clojure-test-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [scry.clojure-test :as ct]
   [scry.fixtures.erroring]
   [scry.fixtures.failing]
   [scry.fixtures.fixtured]
   [scry.fixtures.output]
   [scry.fixtures.passing]))

(defn- failure-for
  "Return the failure entry for a fully-qualified test var symbol."
  [result var-sym]
  (->> (:failures result)
       (filter #(= var-sym (:var %)))
       first))

(deftest run-summary-test
  ;; Running a mix of passing/failing/erroring namespaces produces accurate
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
      (testing "only failing/erroring vars appear in :failures"
        (is (= 2 (get-in result [:summary :fail-var-count])))
        (is (= #{'scry.fixtures.failing/equality-fails
                 'scry.fixtures.erroring/throws-exception}
               (set (map :var (:failures result)))))))))

(deftest passing-run-is-clean-test
  ;; A run with no failures reports pass? true and an empty :failures vector.
  (testing "all-passing namespace"
    (let [result (ct/run {:namespaces ['scry.fixtures.passing]})]
      (is (true? (:pass? result)))
      (is (empty? (:failures result)))
      (is (= 0 (get-in result [:summary :fail-var-count]))))))

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

(deftest captures-output-for-failed-tests-only-test
  ;; stdout/stderr is captured per failing var; passing vars contribute no
  ;; failure entry at all, and output never leaks across vars.
  (testing "output capture"
    (let [result (ct/run {:namespaces ['scry.fixtures.output]})
          failure (failure-for result 'scry.fixtures.output/noisy-and-fails)]
      (is (= "stdout from failing test\n" (:out failure)))
      (is (= "stderr from failing test\n" (:err failure)))
      (testing "passing noisy test produces no failure entry"
        (is (nil? (failure-for result 'scry.fixtures.output/noisy-and-passes)))))))

(deftest run-explicit-vars-test
  ;; A run can target explicit vars, ignoring non-test vars.
  (testing "explicit :vars"
    (let [result (ct/run {:vars [#'scry.fixtures.failing/equality-fails
                                 #'scry.fixtures.passing/arithmetic-passes]})]
      (is (= 2 (get-in result [:summary :test])))
      (is (= 1 (get-in result [:summary :fail-var-count]))))))

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
