(ns scry.fixtures.raw-nested-allowlisted
  "Allow-listed helper fixture vars for raw nested ignored-frame checks."
  (:require
   [clojure.test :as test :refer [deftest is]]))

(def events (atom []))

(deftest allow-listed-helper-test
  (swap! events conj :helper-ran)
  (println "allow-listed helper output")
  (is (= :helper :helper)))

(deftest non-owned-wrapper-test
  (swap! events conj :wrapper-ran)
  (println "raw non-owned wrapper before")
  (test/test-var #'allow-listed-helper-test)
  (println "raw non-owned wrapper after")
  (is (= :raw-wrapper :failure) "raw wrapper assertion is non-owned"))
