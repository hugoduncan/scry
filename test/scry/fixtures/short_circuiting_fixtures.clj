(ns scry.fixtures.short-circuiting-fixtures
  "Fixture namespace exercising :each fixtures that skip their test function."
  (:require
   [clojure.test :refer [deftest is use-fixtures]]))

(def events (atom []))

(use-fixtures :each
  (fn [_]
    (swap! events conj :fixture-ran)
    (println "short-circuit fixture output")
    (is true "short-circuit fixture assertion")))

(deftest skipped-by-each-fixture-test
  (swap! events conj :test-ran)
  (is false "body should not run"))
