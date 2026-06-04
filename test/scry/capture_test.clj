(ns scry.capture-test
  (:require
   [clojure.string :as str]
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
      (report {:type :pass :message "ok" :expected '(= 1 1)
               :actual '(= 1 1) :file "x.clj" :line 6})
      (report {:type :fail :message "boom" :expected '(= 1 2)
               :actual '(not (= 1 2)) :file "x.clj" :line 7})
      (report {:type :end-test-var :var v})
      (let [result (cap/build-result state {:duration-ms 3.0 :scope :var})]
        (testing "counts"
          (is (= 1 (get-in result [:summary :test])))
          (is (= 1 (get-in result [:summary :pass])))
          (is (= 1 (get-in result [:summary :fail])))
          (is (= 3.0 (get-in result [:summary :duration-ms]))))
        (testing "pass? false"
          (is (false? (:pass? result))))
        (testing "single failure with captured output and assertions"
          (let [failure (first (:failures result))]
            (is (= 'scry.capture-test/report-fn-builds-result-test
                   (:var failure)))
            (is (= "captured-out" (:out failure)))
            (is (= [:pass :fail] (mapv :type (:assertions failure))))
            (is (= '(= 1 2) (:expected (second (:assertions failure)))))))))))

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
      (let [result (cap/build-result state {:duration-ms 1.0 :scope :var})]
        (is (= "var-text" (:out (first (:failures result))))
            "var buffer holds only the var's output")
        (is (= "orphan-textmore-orphan" (:out (cap/orphan-output state))))))))

(deftest routing-writer-string-slice-test
  ;; Java's Writer.write(String, off, len) overload routes exactly the selected
  ;; substring rather than treating the string as a char array.
  (testing "string slice writes"
    (let [state (cap/new-state)
          report (cap/report-fn state)
          out (cap/routing-writer state :out)
          v #'scry.capture-test/routing-writer-string-slice-test]
      (report {:type :begin-test-var :var v})
      (.write out "prefix-value-suffix" 7 5)
      (.write out (char-array "abcdef") 2 3)
      (report {:type :pass})
      (report {:type :end-test-var :var v})
      (let [entry (first (:results (cap/build-result state {:duration-ms 1.0
                                                            :scope :var})))]
        (is (= "valuecde" (:out entry)))))))

(deftest disabled-context-ignores-events-and-does-not-append-output-test
  ;; Explicitly disabling capture prevents report events and routed output from
  ;; mutating an enclosing or supplied capture state.
  (testing "without-context nil boundary"
    (let [outer-state (cap/new-state)
          outer-report (cap/report-fn outer-state)
          fallback (java.io.StringWriter.)
          out (binding [*out* fallback]
                (cap/routing-writer outer-state :out))
          v #'scry.capture-test/disabled-context-ignores-events-and-does-not-append-output-test]
      (outer-report {:type :begin-test-var :var v})
      (binding [*out* out]
        (print "outer-before")
        (cap/without-context
         (outer-report {:type :fail :message "ignored"
                        :expected false :actual true})
         (print "escaped-output"))
        (print "outer-after"))
      (outer-report {:type :pass})
      (outer-report {:type :end-test-var :var v})
      (let [entry (first (:results (cap/build-result outer-state {:duration-ms 1.0
                                                                  :scope :var})))]
        (is (= :pass (:status entry)))
        (is (= [:pass] (mapv :type (:assertions entry))))
        (is (= "outer-beforeouter-after" (:out entry)))
        (is (not (str/includes? (:out entry) "escaped-output")))
        (is (= "escaped-output" (str fallback)))))))

(deftest default-suite-format-is-compact-test
  ;; Suite formatting keeps only failing/erroring vars and omits assertions and
  ;; output from entries by default.
  (testing "suite defaults"
    (let [state (cap/new-state)
          report (cap/report-fn state)
          v #'scry.capture-test/default-suite-format-is-compact-test]
      (report {:type :begin-test-var :var v})
      (report {:type :pass})
      (report {:type :fail :file "x.clj" :line 1})
      (report {:type :end-test-var :var v})
      (let [entry (first (:results (cap/build-result state {:duration-ms 1.0
                                                            :scope :suite})))]
        (is (= #{:var :ns :status :assertion-summary} (set (keys entry))))
        (is (= {:pass 1 :fail 1 :error 0} (:assertion-summary entry)))))))

(deftest summary-line-test
  ;; The terse summary line reflects the summary counts.
  (testing "summary-line formatting"
    (is (= "3 tests, 1 pass, 1 fail, 1 error"
           (cap/summary-line {:summary {:test 3 :pass 1 :fail 1 :error 1}})))))
