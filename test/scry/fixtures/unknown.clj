(ns scry.fixtures.unknown
  (:require
   [clojure.test :refer [deftest]]))

(deftest no-assertions
  ;; Fixture for CLI unknown-status coverage: clojure.test executes the var but
  ;; emits no assertion events.
  nil)
