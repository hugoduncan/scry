(ns scry.fixtures.asserting-fixtures
  "Fixture namespace exercising assertion ownership for :once and :each fixtures."
  (:require
   [clojure.test :refer [deftest is use-fixtures]]))

(def once-setup-pass? (atom true))
(def once-teardown-pass? (atom true))
(def each-setup-pass? (atom true))
(def each-teardown-pass? (atom true))

(use-fixtures :once
  (fn [t]
    (is @once-setup-pass? "once setup assertion")
    (t)
    (is @once-teardown-pass? "once teardown assertion")))

(use-fixtures :each
  (fn [t]
    (is @each-setup-pass? "each setup assertion is attributed to the var")
    (t)
    (is @each-teardown-pass? "each teardown assertion is attributed to the var")))

(deftest fixture-assertion-test
  (is (= :body :body)))
