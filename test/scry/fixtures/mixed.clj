(ns scry.fixtures.mixed
  "Fixture namespace: vars with mixed pass/fail/error status."
  (:require
   [clojure.test :refer [deftest is]]))

(deftest pass-then-fail
  (is (= 1 1))
  (is (= 1 2)))

(deftest fail-then-error
  (is (= 1 2))
  (is (= 1 (/ 1 0))))
