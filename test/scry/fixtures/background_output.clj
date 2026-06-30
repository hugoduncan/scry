(ns scry.fixtures.background-output
  "Fixture namespace reproducing the downstream CLI `:scry.cli/runner-error`
  trigger without native dependencies.

  Each test emits output from a separate, non-test-owner thread (and joins it)
  before completing; one test also fails an assertion. This mirrors the
  reported native/JNI case where background threads and direct System.err
  writes occur during a test's execution window. The CLI must still complete
  with a normal pass/fail summary rather than aborting the whole run."
  (:require
   [clojure.test :refer [deftest is testing]]))

(defn- write-from-unowned-thread!
  "Spawn a NON-test thread that writes to both System.out and System.err using
  raw System streams (bypassing *out*/*err*), then join it."
  []
  (let [t (Thread.
           ^Runnable
           (fn []
             (.println System/out
                       "[unowned-thread] stdout write during test execution")
             (.println System/err
                       "[unowned-thread] Exception in thread \"unowned\" simulated.NativeDiagnostic: harmless"))
           "scry-repro-unowned-writer")]
    (.start t)
    (.join t 5000)
    nil))

(deftest passing-with-unowned-background-output
  (testing "a passing test that emits output from a non-owned thread"
    (write-from-unowned-thread!)
    (is (= 2 (+ 1 1)))))

(deftest failing-with-unowned-background-output
  (testing "a FAILING test that emits output from a non-owned thread"
    (write-from-unowned-thread!)
    (is (= 1 2) "deliberate failure for runner-resilience repro")))
