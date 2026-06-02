(ns scry.fixtures.erroring
  "Fixture namespace: a test that throws an uncaught exception."
  (:require
   [clojure.test :refer [deftest is]]))

(deftest throws-exception
  (is (= 1 (/ 1 0))))
