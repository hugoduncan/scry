(ns scry.fixtures.output-fixtures
  "Fixture namespace exercising output ownership for :once and :each fixtures."
  (:require
   [clojure.test :refer [deftest is use-fixtures]]))

(use-fixtures :once
  (fn [t]
    (println "once setup")
    (t)
    (println "once teardown")))

(use-fixtures :each
  (fn [t]
    (println "each setup")
    (t)
    (println "each teardown")))

(deftest first-output-test
  (println "first body")
  (is (= 1 1)))

(deftest second-output-test
  (println "second body")
  (is (= 2 2)))
