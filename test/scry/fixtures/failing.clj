(ns scry.fixtures.failing
  "Fixture namespace: contains a passing and a failing test."
  (:require
   [clojure.test :refer [deftest is testing]]))

(deftest also-passes
  (is (true? true)))

(deftest equality-fails
  (testing "outer context"
    (testing "inner context"
      (is (= 1 2) "one is not two"))))
