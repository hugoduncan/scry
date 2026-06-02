(ns scry.fixtures.passing
  "Fixture namespace: all tests pass. Not auto-discovered (no -test suffix)."
  (:require
   [clojure.test :refer [deftest is testing]]))

(deftest arithmetic-passes
  (testing "addition"
    (is (= 2 (+ 1 1))))
  (testing "multiplication"
    (is (= 6 (* 2 3)))))
