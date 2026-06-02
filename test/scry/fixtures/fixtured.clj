(ns scry.fixtures.fixtured
  "Fixture namespace exercising clojure.test :once and :each fixtures, used to
   confirm scry delegates fixture handling to clojure.test correctly."
  (:require
   [clojure.test :refer [deftest is use-fixtures]]))

(def events (atom []))

(use-fixtures :once
  (fn [t] (swap! events conj :once-start) (t) (swap! events conj :once-end)))

(use-fixtures :each
  (fn [t] (swap! events conj :each-start) (t) (swap! events conj :each-end)))

(deftest first-test
  (is (= 1 1)))

(deftest second-test
  (is (= 2 2)))
