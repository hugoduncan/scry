(ns scry.fixtures.nested
  "Fixture namespace for nested runner capture isolation tests."
  (:require
   [clojure.test :refer [deftest is]]))

(def nested-inner-events (atom []))
(def raw-nested-events (atom []))

(deftest inner-failing-test
  (swap! nested-inner-events conj :inner-ran)
  (println "inner stdout")
  (binding [*out* *err*]
    (println "inner stderr"))
  (is (= :inner :failure)))

(deftest inner-passing-test
  (println "inner pass stdout")
  (is (= :inner :inner)))

(deftest raw-nested-non-owned-test
  (swap! raw-nested-events conj :raw-ran)
  (println "raw nested stdout")
  (is (= :raw :failure)))
