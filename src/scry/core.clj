(ns scry.core
  "Public entry point for scry, an in-process test runner for AI agents.

   `run` executes clojure.test tests in-process and returns an inspectable
   result map; the most recent result is retained for interactive inspection
   through `last-result`. The optional Kaocha adapter lives in `scry.kaocha`
   and is available when the adapter artifact or equivalent optional Kaocha
   classpath is present."
  (:require
   [clojure.string :as str]
   [scry.capture :as capture]
   [scry.clojure-test :as clojure-test]))

(def ^:private last-run
  "Atom holding the most recent run result, for post-run inspection."
  (atom nil))

(defn run
  "Run clojure.test tests in-process and return the inspectable result map.

   Options:
     :dirs          test directories to scan (default [\"test\"])
     :namespaces    explicit collection of namespace symbols to run
     :ns-pattern    regex matching namespace names to run
     :vars          explicit collection of test var refs to run
     :result-format per-scope formatting overrides (see below)

   The result is also available through `last-result`.

   Result shape

   By default a result has this top-level shape:

       {:summary {:test 0 :pass 0 :fail 0 :error 0 :duration-ms 0.0
                  :var-count 0 :fail-var-count 0}
        :pass? true
        :results []
        :failures []}

   `:results` is the canonical formatted collection. `:failures` is a
   compatibility collection holding the failing/erroring subset when the
   selected format includes it.

   Scoped detail

   Default entry detail depends on how the run was invoked:

   - Suite or multi scope (`(run)`, multiple namespaces, or multiple vars):
     compact entries for failing/erroring vars only, each with
     `:assertion-summary`; no per-assertion details or output.
   - Single namespace scope (`{:namespaces ['my.ns-test]}`): an entry for every
     executed var, including passing vars, with all assertion details; no
     stdout/stderr keys.
   - Single var scope (`{:vars [#'my.ns-test/a-test]}`): one entry with all
     assertion details and captured `:out`/`:err`.

   A detailed entry looks like:

       {:var 'my.ns-test/a-test
        :ns 'my.ns-test
        :status :pass ;; :pass, :fail, :error, or rarely :unknown
        :assertions [{:type :pass :message nil
                      :expected '(= 2 (+ 1 1)) :actual '(= 2 (+ 1 1))
                      :file \"ns_test.clj\" :line 42
                      :contexts [\"outer\" \"inner\"]}]
        :out \"captured stdout\"
        :err \"captured stderr\"}

   Error assertions also include `:stacktrace`. A compact suite-scope entry
   looks like:

       {:var 'my.ns-test/a-test :ns 'my.ns-test :status :fail
        :assertion-summary {:pass 0 :fail 1 :error 0}}

   Custom result formatting

   `:result-format` overrides returned keys and inclusions per scope. Scopes are
   `:suite`, `:namespace`, and `:var`; each supports:

     :top-level-keys  top-level keys to return
     :entry-keys      keys to project for each result entry
     :assertions?     authoritative assertion gate; true adds `:assertions`,
                      false removes it
     :output?         authoritative output gate; true adds `:out`/`:err`,
                      false removes them

   For example:

       (run {:namespaces ['my.ns-test]
             :result-format {:namespace {:top-level-keys [:summary :pass? :results]
                                         :entry-keys [:var :status]
                                         :assertions? true
                                         :output? false}}})

   If custom `:top-level-keys` omits both `:results` and `:failures`, helpers
   such as `failures` return empty/nil values because there is no collection to
   inspect."
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
