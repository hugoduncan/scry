(ns scry.fixtures.raw-nested-outer
  "Outer helper vars for raw nested clojure.test isolation checks.

  These vars intentionally invoke raw nested clojure.test executions that emit
  failing assertions. Keep them outside scry.clojure-test-test so ordinary
  clojure.test runs of the main test namespace stay green; scry acceptance tests
  execute these vars explicitly through scry.clojure-test/run."
  (:require
   [clojure.test :as test :refer [deftest is]]
   [scry.fixtures.failing]
   [scry.fixtures.nested]
   [scry.fixtures.output]
   [scry.fixtures.raw-nested-allowlisted]
   [scry.fixtures.raw-nested-fixtures]))

(deftest invokes-raw-nested-clojure-test-test
  (println "outer raw before")
  (test/test-vars [#'scry.fixtures.nested/raw-nested-non-owned-test])
  (is (= [:raw-ran] @scry.fixtures.nested/raw-nested-events))
  (println "outer raw after"))

(deftest invokes-raw-nested-fixtured-clojure-test-test
  (println "outer raw fixture before")
  (test/test-vars [#'scry.fixtures.raw-nested-fixtures/raw-fixtured-non-owned-test])
  (is (= [:once-setup :test-body :once-teardown]
         @scry.fixtures.raw-nested-fixtures/events))
  (println "outer raw fixture after"))

(deftest invokes-raw-nested-allow-listed-helper-test
  (println "outer raw allow-listed before")
  (test/test-var #'scry.fixtures.raw-nested-allowlisted/non-owned-wrapper-test)
  (is (= [:wrapper-ran :helper-ran]
         @scry.fixtures.raw-nested-allowlisted/events))
  (println "outer raw allow-listed after"))

(deftest invokes-raw-nested-run-tests-test
  (println "outer raw run-tests before")
  (let [summary (test/run-tests 'scry.fixtures.failing)]
    (is (= {:test 2 :pass 1 :fail 1 :error 0}
           (select-keys summary [:test :pass :fail :error]))))
  (println "outer raw run-tests after"))

(deftest invokes-raw-nested-test-vars-counters-test
  (println "outer raw test-vars counters before")
  (binding [test/*report-counters* (ref test/*initial-report-counters*)]
    (test/test-vars [#'scry.fixtures.failing/also-passes
                     #'scry.fixtures.failing/equality-fails])
    (is (= {:test 2 :pass 1 :fail 1 :error 0}
           (select-keys @test/*report-counters* [:test :pass :fail :error]))))
  (println "outer raw test-vars counters after"))

(deftest invokes-raw-nested-test-var-counters-test
  (println "outer raw test-var counters before")
  (binding [test/*report-counters* (ref test/*initial-report-counters*)]
    (test/test-var #'scry.fixtures.output/noisy-and-fails)
    (is (= {:test 1 :pass 0 :fail 1 :error 0}
           (select-keys @test/*report-counters* [:test :pass :fail :error]))))
  (println "outer raw test-var counters after"))
