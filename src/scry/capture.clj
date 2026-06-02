(ns scry.capture
  "In-process capture of clojure.test execution.

   The capture machinery is built around an explicit state atom plus a
   `clojure.test/report` hook. clojure.test/report is a dynamic var, so we
   bind it to a closure over the state atom (`report-fn`) for the duration of a
   run. stdout/stderr are routed per test var by binding *out*/*err* to
   `routing-writer`s that consult the state atom for the currently executing
   var.

   Only failing/erroring assertions are retained as events; captured output is
   kept per var and surfaced in the result for failed vars only."
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
  "Extract the inspectable subset of a clojure.test :fail/:error report map.

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

(defn- count!
  [state k]
  (swap! state update-in [:counts k] (fnil inc 0)))

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
      :pass (count! state :pass)
      :fail (record-event! state m)
      :error (record-event! state m)
      nil)))

;;; Result construction

(defn build-result
  "Build the inspectable result map from captured `state`.

   Failures contain only the failing/erroring assertions plus captured
   stdout/stderr for that var."
  [state {:keys [duration-ms]}]
  (let [s @state
        counts (:counts s)
        failures (vec
                  (for [v (:order s)
                        :let [entry (get-in s [:vars v])]
                        :when (seq (:events entry))]
                    {:var (:var-symbol entry)
                     :ns (:ns entry)
                     :status (if (some #(= :error (:type %)) (:events entry))
                               :error
                               :fail)
                     :assertions (:events entry)
                     :out (str (:out entry))
                     :err (str (:err entry))}))]
    {:summary (assoc counts
                     :duration-ms duration-ms
                     :var-count (count (:order s))
                     :fail-var-count (count failures))
     :pass? (and (zero? (:fail counts 0)) (zero? (:error counts 0)))
     :failures failures}))

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
