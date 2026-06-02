(ns scry.capture-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [scry.capture :as cap]))

(deftest report-fn-builds-result-test
  ;; Driving report-fn directly (as clojure.test would) accumulates counts,
  ;; per-var assertions, and routed output into the result.
  (testing "manual report event sequence"
    (let [state (cap/new-state)
          report (cap/report-fn state)
          v #'scry.capture-test/report-fn-builds-result-test]
      (report {:type :begin-test-var :var v})
      (binding [*out* (cap/routing-writer state :out)]
        (print "captured-out"))
      (report {:type :pass})
      (report {:type :fail :message "boom" :expected '(= 1 2)
               :actual '(not (= 1 2)) :file "x.clj" :line 7})
      (report {:type :end-test-var :var v})
      (let [result (cap/build-result state {:duration-ms 3.0})]
        (testing "counts"
          (is (= 1 (get-in result [:summary :test])))
          (is (= 1 (get-in result [:summary :pass])))
          (is (= 1 (get-in result [:summary :fail])))
          (is (= 3.0 (get-in result [:summary :duration-ms]))))
        (testing "pass? false"
          (is (false? (:pass? result))))
        (testing "single failure with captured output and assertion"
          (let [failure (first (:failures result))]
            (is (= 'scry.capture-test/report-fn-builds-result-test
                   (:var failure)))
            (is (= "captured-out" (:out failure)))
            (is (= '(= 1 2) (:expected (first (:assertions failure)))))))))))

(deftest routing-writer-routes-by-current-var-test
  ;; Output written while a var is current lands in that var's buffer; output
  ;; written with no current var lands in the orphan buffer.
  (testing "routing follows :current"
    (let [state (cap/new-state)
          report (cap/report-fn state)
          out (cap/routing-writer state :out)
          v #'scry.capture-test/routing-writer-routes-by-current-var-test]
      (binding [*out* out] (print "orphan-text"))
      (report {:type :begin-test-var :var v})
      (binding [*out* out] (print "var-text"))
      (report {:type :fail :message nil :expected nil :actual nil
               :file "x.clj" :line 1})
      (report {:type :end-test-var :var v})
      (binding [*out* out] (print "more-orphan"))
      (let [result (cap/build-result state {:duration-ms 1.0})]
        (is (= "var-text" (:out (first (:failures result))))
            "var buffer holds only the var's output (failure entry only exists if events)")
        (is (= "orphan-textmore-orphan" (:out (cap/orphan-output state))))))))

(deftest summary-line-test
  ;; The terse summary line reflects the summary counts.
  (testing "summary-line formatting"
    (is (= "3 tests, 1 pass, 1 fail, 1 error"
           (cap/summary-line {:summary {:test 3 :pass 1 :fail 1 :error 1}})))))
