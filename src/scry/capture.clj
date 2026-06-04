(ns scry.capture
  "In-process capture of clojure.test execution.

   The capture machinery is built around an explicit state atom plus a
   `clojure.test/report` hook. clojure.test/report is a dynamic var, so we
   bind it to a closure over the state atom (`report-fn`) for the duration of a
   run. stdout/stderr are routed per test var by binding *out*/*err* to
   `routing-writer`s that consult the state atom for the currently executing
   var. Assertion events are retained per executed var so result formatting can
   expose compact suite results or detailed namespace/var results."
  (:require
   [clojure.string :as str]
   [clojure.test :as test])
  (:import
   [java.io StringWriter Writer]))

;;; State

(defn new-state
  "Create a fresh capture state atom.

   :current      the Var currently executing (set on :begin-test-var)
   :order        Vars in encounter order
   :vars         map of Var -> per-var capture entry
   :counts       running clojure.test counters
   :orphan       output buffers for writes outside any test var"
  []
  (atom {:current nil
         :order []
         :vars {}
         :counts {:test 0 :pass 0 :fail 0 :error 0}
         :orphan {:out (StringBuilder.) :err (StringBuilder.)}}))

(defn- var->symbol
  "Return the fully-qualified symbol naming a test var."
  [v]
  (let [m (meta v)]
    (symbol (str (ns-name (:ns m))) (str (:name m)))))

;;; Output routing

(defn- current-buffer
  "Return the StringBuilder that should receive output for `stream-key`
   (:out or :err) given the current execution state."
  ^StringBuilder [state stream-key]
  (let [s @state
        v (:current s)]
    (or (when v (get-in s [:vars v stream-key]))
        (get-in s [:orphan stream-key]))))

(defn routing-writer
  "A java.io.Writer that appends everything written to the per-var buffer for
   `stream-key`, selected dynamically from `state` at write time."
  ^Writer [state stream-key]
  (proxy [Writer] []
    (write
      ([x]
       (let [^StringBuilder sb (current-buffer state stream-key)]
         (cond
           (string? x) (.append sb ^String x)
           (instance? CharSequence x) (.append sb ^CharSequence x)
           (integer? x) (.append sb (char (int x)))
           :else (.append sb (String. ^chars x)))))
      ([cbuf off len]
       (let [^StringBuilder sb (current-buffer state stream-key)]
         (.append sb (String. ^chars cbuf (int off) (int len))))))
    (flush [])
    (close [])))

;;; Assertion extraction

(defn- stacktrace-str
  "Render a Throwable (with causes) to a string for later inspection."
  [^Throwable t]
  (let [sw (StringWriter.)]
    (.printStackTrace t (java.io.PrintWriter. sw))
    (str sw)))

(defn assertion
  "Extract the inspectable subset of a clojure.test assertion report map.

   `contexts` is the vector of active `testing` strings, outermost first. For
   errors a rendered :stacktrace string is added."
  [m contexts]
  (let [base {:type (:type m)
              :message (:message m)
              :expected (:expected m)
              :actual (:actual m)
              :file (:file m)
              :line (:line m)
              :contexts (vec contexts)}]
    (cond-> base
      (instance? Throwable (:actual m))
      (assoc :stacktrace (stacktrace-str (:actual m))))))

;;; Report hook

(defn- begin-var!
  [state v]
  (swap! state
         (fn [s]
           (cond-> s
             true (assoc :current v)
             (not (contains? (:vars s) v))
             (-> (update :order conj v)
                 (assoc-in [:vars v]
                           {:var v
                            :var-symbol (var->symbol v)
                            :ns (ns-name (:ns (meta v)))
                            :name (:name (meta v))
                            :out (StringBuilder.)
                            :err (StringBuilder.)
                            :events []}))
             true (update-in [:counts :test] (fnil inc 0))))))

(defn- end-var!
  [state]
  (swap! state assoc :current nil))

(defn- record-event!
  [state m]
  (let [event (assertion m (vec (reverse test/*testing-contexts*)))]
    (swap! state
           (fn [s]
             (let [v (:current s)]
               (cond-> (update-in s [:counts (:type m)] (fnil inc 0))
                 v (update-in [:vars v :events] conj event)))))))

(defn report-fn
  "Return a clojure.test/report replacement closing over `state`.

   Handles the event types scry cares about; everything else is ignored."
  [state]
  (fn [m]
    (case (:type m)
      :begin-test-var (begin-var! state (:var m))
      :end-test-var (end-var! state)
      :pass (record-event! state m)
      :fail (record-event! state m)
      :error (record-event! state m)
      nil)))

;;; Result formatting

(def default-result-format
  "Default scoped result-format configuration."
  {:suite {:top-level-keys [:summary :pass? :results :failures]
           :entry-keys [:var :ns :status :assertion-summary]
           :assertions? false
           :output? false}
   :namespace {:top-level-keys [:summary :pass? :results :failures]
               :entry-keys [:var :ns :status :assertions]
               :assertions? true
               :output? false}
   :var {:top-level-keys [:summary :pass? :results :failures]
         :entry-keys [:var :ns :status :assertions :out :err]
         :assertions? true
         :output? true}})

(defn- status
  [events]
  (cond
    (some #(= :error (:type %)) events) :error
    (some #(= :fail (:type %)) events) :fail
    (some #(= :pass (:type %)) events) :pass
    :else :unknown))

(defn- assertion-summary
  [events]
  (frequencies (map :type events)))

(defn- failure-entry?
  [entry]
  (contains? #{:fail :error} (:status entry)))

(defn- canonical-entry
  [entry]
  (let [events (:events entry)]
    {:var (:var-symbol entry)
     :ns (:ns entry)
     :status (status events)
     :assertion-summary (merge {:pass 0 :fail 0 :error 0}
                               (assertion-summary events))
     :assertions events
     :out (str (:out entry))
     :err (str (:err entry))}))

(defn current-var-result
  "Return the canonical result entry for the currently executing test var.

   This is intended for end-of-var progress callbacks while output buffers and
   assertion events for the var are still available in the capture state."
  [state]
  (when-let [v (:current @state)]
    (canonical-entry (get-in @state [:vars v]))))

(defn canonical-results
  "Return unprojected result entries for every executed test var in state."
  [state]
  (let [s @state]
    (mapv #(canonical-entry (get-in s [:vars %])) (:order s))))

(defn- merge-result-format
  [overrides]
  (merge-with merge default-result-format (or overrides {})))

(defn- format-entry
  [{:keys [entry-keys assertions? output?]} entry]
  (let [projected (select-keys entry entry-keys)]
    (cond-> projected
      assertions? (assoc :assertions (:assertions entry))
      (false? assertions?) (dissoc :assertions)
      output? (assoc :out (:out entry) :err (:err entry))
      (false? output?) (dissoc :out :err))))

(defn- scope-results
  [scope entries]
  (if (= :suite scope)
    (filterv failure-entry? entries)
    entries))

(defn format-result
  "Format a canonical result map for `scope` using optional result-format overrides."
  [result scope result-format]
  (let [fmt (get (merge-result-format result-format) scope)
        canonical (:canonical-results result)
        selected-results (mapv #(format-entry fmt %) (scope-results scope canonical))
        selected-failures (mapv #(format-entry fmt %) (filter failure-entry? canonical))
        values (assoc result
                      :results selected-results
                      :failures selected-failures)]
    (select-keys values (:top-level-keys fmt))))

(defn build-result
  "Build the scoped result map from captured `state`.

   Options may include :duration-ms, :scope, and :result-format."
  [state {:keys [duration-ms scope result-format]
          :or {scope :suite}}]
  (let [s @state
        counts (:counts s)
        canonical (canonical-results state)
        failures (filterv failure-entry? canonical)
        result {:summary (assoc counts
                                :duration-ms duration-ms
                                :var-count (count (:order s))
                                :fail-var-count (count failures))
                :pass? (and (zero? (:fail counts 0)) (zero? (:error counts 0)))
                :canonical-results canonical}]
    (format-result result scope result-format)))

(defn orphan-output
  "Return output captured outside any test var as {:out s :err s}."
  [state]
  (let [s @state]
    {:out (str (get-in s [:orphan :out]))
     :err (str (get-in s [:orphan :err]))}))

(defn summary-line
  "A terse one-line human summary of a result's :summary."
  [result]
  (let [{:keys [test pass fail error]} (:summary result)]
    (str/join ", "
              [(str test " tests")
               (str pass " pass")
               (str fail " fail")
               (str error " error")])))
