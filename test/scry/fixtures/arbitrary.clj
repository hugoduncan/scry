(ns scry.fixtures.arbitrary
  "Fixture namespace: contains assertion data with arbitrary object values."
  (:require
   [clojure.test :refer [deftest is]]))

(deftest arbitrary-object-fails
  (let [expected (Object.)
        actual {:nested [(Object.)]}]
    (is (= expected actual) "objects are not equal")))
