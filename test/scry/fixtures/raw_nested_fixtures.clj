(ns scry.fixtures.raw-nested-fixtures
  "Fixture namespace for raw nested clojure.test fixture isolation tests."
  (:require
   [clojure.test :refer [deftest is use-fixtures]]))

(def events (atom []))

(use-fixtures :once
  (fn [t]
    (swap! events conj :once-setup)
    (println "raw once setup")
    (is (= :once-setup :failure) "raw once setup assertion is non-owned")
    (t)
    (swap! events conj :once-teardown)
    (println "raw once teardown")
    (is (= :once-teardown :failure) "raw once teardown assertion is non-owned")))

(deftest raw-fixtured-non-owned-test
  (swap! events conj :test-body)
  (println "raw fixtured body")
  (is (= :test-body :failure) "raw body assertion is non-owned"))
