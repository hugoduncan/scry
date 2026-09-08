(ns scry.fixtures.incremental-cli
  "Fixture namespace for native core CLI incremental artifact publication."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is use-fixtures]]))

(def results-dir (atom nil))
(def observed-entry (atom nil))

(defn- each-fixture
  [test-fn]
  (println "each setup")
  (try
    (test-fn)
    (finally
      (println "each teardown"))))

(use-fixtures :each each-fixture)

(deftest first-fails-before-next-var
  (println "first body")
  (is (= :expected :actual) "first failure"))

(deftest second-observes-first-artifact
  (let [file (io/file @results-dir
                      "scry.fixtures.incremental-cli__first-fails-before-next-var.edn")]
    (reset! observed-entry (edn/read-string (slurp file))))
  (is (= :fail (:status @observed-entry))))
