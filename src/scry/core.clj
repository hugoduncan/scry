(ns scry.core
  "Public entry point for scry, an in-process test runner for AI agents.

   `run` executes clojure.test tests in-process and returns an inspectable
   result map; the most recent result is also retained in `last-run` so it can
   be inspected interactively after the run. The optional Kaocha adapter lives
   in `scry.kaocha` and is available when the adapter artifact or equivalent
   optional Kaocha classpath is present."
  (:require
   [clojure.string :as str]
   [scry.capture :as capture]
   [scry.clojure-test :as clojure-test]))

(def last-run
  "Atom holding the most recent run result, for post-run inspection."
  (atom nil))

(defn run
  "Run clojure.test tests in-process and return the inspectable result map.

   Supports directory, namespace, namespace-pattern, var, and result-format
   options documented in the README. The result is also stored in `last-run`."
  ([] (run {}))
  ([opts]
   (reset! last-run (clojure-test/run opts))))

(defn last-result
  "Return the most recent run result, or nil if nothing has run."
  []
  @last-run)

(defn- failure-status?
  [entry]
  (contains? #{:fail :error} (:status entry)))

(defn failures
  "Return failure/error entries of `result` (defaults to the last run).

   Prefers the compatibility :failures collection when present and otherwise
   filters canonical :results. Returns an empty vector when the selected result
   format omits both collections."
  ([] (failures (last-result)))
  ([result]
   (vec (or (:failures result)
            (filter failure-status? (:results result))
            []))))

(defn failed-test
  "Return the failure/error entry for fully-qualified test var symbol `var-sym`."
  ([var-sym] (failed-test (last-result) var-sym))
  ([result var-sym]
   (->> (failures result)
        (filter #(= var-sym (:var %)))
        first)))

(defn output
  "Return {:out s :err s} captured for failed test var `var-sym`, when present."
  ([var-sym] (output (last-result) var-sym))
  ([result var-sym]
   (when-let [f (failed-test result var-sym)]
     (not-empty (select-keys f [:out :err])))))

(defn- assertion-string
  [{:keys [type message expected actual file line contexts stacktrace]}]
  (str/join
   "\n"
   (cond-> [(str "  [" (name type) "] "
                 (when (seq contexts) (str (str/join " / " contexts) " — "))
                 (or message "")
                 " (" file ":" line ")")
            (str "    expected: " (pr-str expected))
            (str "    actual:   " (pr-str actual))]
     stacktrace (conj (str "    stacktrace:\n"
                           (->> (str/split-lines stacktrace)
                                (map #(str "      " %))
                                (str/join "\n")))))))

(defn- failure-string
  [{:keys [var status assertions out err]}]
  (str/join
   "\n"
   (cond-> [(str (name status) ": " var)]
     (seq assertions) (into (map assertion-string assertions))
     (seq out) (conj (str "  --- stdout ---\n" out))
     (seq err) (conj (str "  --- stderr ---\n" err)))))

(defn report-string
  "Render a human/agent-readable report of `result` (defaults to last run)."
  ([] (report-string (last-result)))
  ([result]
   (when result
     (str/join
      "\n\n"
      (into [(capture/summary-line result)]
            (map failure-string (failures result)))))))
