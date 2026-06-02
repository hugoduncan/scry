(ns scry.fixtures.output
  "Fixture namespace: tests that emit stdout/stderr. One fails, one passes."
  (:require
   [clojure.test :refer [deftest is]]))

(deftest noisy-and-fails
  (println "stdout from failing test")
  (binding [*out* *err*]
    (println "stderr from failing test"))
  (is (= :a :b)))

(deftest noisy-and-passes
  (println "stdout from passing test")
  (is (= :a :a)))
