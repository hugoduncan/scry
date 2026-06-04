(ns scry.fixtures.colliding-a
  (:require
   [clojure.test :refer [deftest is]]))

(deftest same-name
  (is (= :a :b) "a differs from b"))
