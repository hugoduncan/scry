(ns scry.tests
  "Run scry's test suite slices via bb.

  Each slice shells out to the same clojure invocations used by CI and the
  maintainer workflow in AGENTS.md. Slices are deliberately kept separate:
  optional Kaocha adapter tests, build checks, and release-helper checks have
  their own aliases and are not part of the core discovery set.

  `bb test` runs every slice in order and fails on the first failing slice.
  `bb test:core`, `bb test:kaocha`, `bb test:build`, and `bb test:release`
  run a single focused slice."
  (:require
   [babashka.process :refer [shell]]))

(defn- run-slice!
  "Runs a command, printing a slice banner first. `shell` throws when the
  process exits non-zero, which fails the surrounding bb task."
  [banner & cmd]
  (println (str "== " banner " =="))
  (apply shell cmd))

(defn core-slice! []
  (run-slice! "core slice"
              "clojure" "-M:test" "-e"
              "(require '[scry.core :as scry])
         (let [result (scry/run {:namespaces ['scry.capture-test
                                              'scry.clojure-test-test
                                              'scry.cli-test]})]
           (println (scry/report-string result))
           (when-not (:pass? result) (System/exit 1)))"))

(defn kaocha-slice! []
  (run-slice! "kaocha adapter slice"
              "clojure" "-M:test:kaocha" "-e"
              "(require '[scry.kaocha-test :as t] '[clojure.test :as ct])
         (let [result (ct/run-tests 'scry.kaocha-test)]
           (when-not (ct/successful? result) (System/exit 1)))")
  (run-slice! "kaocha CLI slice"
              "clojure" "-M:test:kaocha" "-e"
              "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct])
         (let [result (ct/run-tests 'scry.cli-kaocha-test)]
           (when-not (ct/successful? result) (System/exit 1)))"))

(defn build-slice! []
  (run-slice! "build slice"
              "clojure" "-M:test:build" "-e"
              "(require '[scry.build-test :as t] '[clojure.test :as ct])
         (let [result (ct/run-tests 'scry.build-test)]
           (when-not (ct/successful? result) (System/exit 1)))"))

(defn release-slice! []
  (run-slice! "release slice"
              "clojure" "-M:test:release-test" "-e"
              "(require '[scry.release-test :as t] '[clojure.test :as ct])
         (let [result (ct/run-tests 'scry.release-test)]
           (when-not (ct/successful? result) (System/exit 1)))"))

(def slices
  {:core core-slice!
   :kaocha kaocha-slice!
   :build build-slice!
   :release release-slice!})

(defn run-tests!
  "Runs the requested slices. With no args, runs every slice in order and
  stops at the first failure. With a slice keyword arg, runs just that slice.
  Unknown slice keywords are rejected."
  [& args]
  (let [requested (->> args
                       (remove nil?)
                       (mapv keyword))]
    (when-let [unknown (seq (remove slices requested))]
      (throw (ex-info (str "Unknown test slice(s): " (pr-str (vec unknown)))
                      {:unknown (vec unknown)
                       :known (vec (keys slices))})))
    (let [to-run (if (seq requested) requested (keys slices))]
      (doseq [slice to-run]
        ((get slices slice))))))
