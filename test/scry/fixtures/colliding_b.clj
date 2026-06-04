(ns scry.fixtures.colliding-b
  (:require
   [clojure.test :refer [deftest is]]))

(deftest same-name
  (is (= :b :c) "b differs from c"))
