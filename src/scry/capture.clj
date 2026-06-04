(ns scry.capture
  "In-process capture of clojure.test execution.

   Capture is represented by an explicit context that can be dynamically
   replaced or disabled. clojure.test/report hooks and routing writers consult
   the current dynamic context at event/write time, which lets nested scry runs
   install their own capture state and lets foreign runners run with scry
   capture disabled. The public result model remains built from per-var entries
   containing assertion events and stdout/stderr buffers."
  (:require
   [clojure.string :as str]
   [clojure.test :as test])
  (:import
   [java.io StringWriter Writer]))

;;; Context and state

(def ^:private no-context-binding
  (Object.))

(def ^:dynamic *capture-context*
  "The dynamically active capture context.

   The private root sentinel means no dynamic capture boundary has been
   installed. nil is an explicit disabled context and must not fall back to an
   enclosing/supplied state."
  no-context-binding)

(defn new-state
  "Create a fresh capture state atom.

   :current       the owned Var currently reporting assertions
   :output-owner  the Var currently receiving routed output
   :frames        stack of owned/ignored clojure.test var frames
   :order         Vars in encounter order
   :vars          map of Var -> per-var capture entry
   :provisional   non-public buffers/events for an output owner before
                  clojure.test begins the var
   :counts        running clojure.test counters for owned vars
   :orphan        output buffers for writes outside any public test var"
  []
  (atom {:current nil
         :output-owner nil
         :last-completed nil
         :frames []
         :order []
         :vars {}
         :provisional {}
         :counts {:test 0 :pass 0 :fail 0 :error 0}
         :orphan {:out (StringBuilder.) :err (StringBuilder.)}}))

(defn new-context
  "Create a capture context.

   Options:
     :state          raw capture state atom; omitted creates a fresh state
     :intended-vars  collection of Vars owned by this context. nil means
                     accept all vars, preserving direct helper-test semantics.
     :metadata       optional caller metadata retained for diagnostics."
  ([] (new-context {}))
  ([{:keys [state intended-vars metadata]}]
   {:state (or state (new-state))
    :intended-vars (some-> intended-vars set)
    :metadata metadata}))

(defmacro with-context
  "Evaluate body with `context` as the active scry capture context."
  [context & body]
  `(binding [*capture-context* ~context]
     ~@body))

(defmacro without-context
  "Evaluate body with scry capture explicitly disabled."
  [& body]
  `(binding [*capture-context* nil]
     ~@body))

(defn context-disabled?
  "Return true when the current dynamic boundary explicitly disables capture."
  []
  (nil? *capture-context*))

(defn- context?
  [x]
  (and (map? x) (contains? x :state)))

(defn- ->context
  [state-or-context]
  (cond
    (nil? state-or-context) nil
    (context? state-or-context) state-or-context
    :else (new-context {:state state-or-context})))

(defn- active-context
  []
  (when-not (identical? *capture-context* no-context-binding)
    *capture-context*))

(defn- compatibility-context
  [fallback-state-or-context]
  (if (nil? *capture-context*)
    nil
    (->context fallback-state-or-context)))

(defn- context-state
  [context]
  (:state context))

(defn- normalize-state
  [state-or-context]
  (context-state (->context state-or-context)))

(defn- owns-var?
  [context v]
  (let [intended-vars (:intended-vars context)]
    (or (nil? intended-vars)
        (contains? intended-vars v))))

(defn- var->symbol
  "Return the fully-qualified symbol naming a test var."
  [v]
  (let [m (meta v)]
    (symbol (str (ns-name (:ns m))) (str (:name m)))))

(defn- new-entry
  [v]
  {:var v
   :var-symbol (var->symbol v)
   :ns (ns-name (:ns (meta v)))
   :name (:name (meta v))
   :out (StringBuilder.)
   :err (StringBuilder.)
   :events []})

(defn- ensure-entry
  [s v]
  (if (contains? (:vars s) v)
    s
    (-> s
        (update :order conj v)
        (assoc-in [:vars v] (new-entry v)))))

(defn- new-provisional-entry
  []
  {:out (StringBuilder.)
   :err (StringBuilder.)
   :events []})

(defn- ensure-provisional-entry
  [s v]
  (if (contains? (:provisional s) v)
    s
    (assoc-in s [:provisional v] (new-provisional-entry))))

(defn- promote-provisional-entry
  [s v]
  (let [s' (ensure-entry s v)]
    (if-let [provisional (get-in s' [:provisional v])]
      (let [^StringBuilder out (get-in s' [:vars v :out])
            ^StringBuilder err (get-in s' [:vars v :err])]
        (.append out (str (:out provisional)))
        (.append err (str (:err provisional)))
        (-> s'
            (update-in [:vars v :events] into (:events provisional))
            (update :provisional dissoc v)))
      s')))

(defn- flush-provisional-entry
  [s v]
  (if-let [provisional (get-in s [:provisional v])]
    (let [^StringBuilder out (get-in s [:orphan :out])
          ^StringBuilder err (get-in s [:orphan :err])]
      (.append out (str (:out provisional)))
      (.append err (str (:err provisional)))
      (update s :provisional dissoc v))
    s))

(defn- top-owned-var
  [frames]
  (some (fn [{:keys [kind var]}]
          (when (= :owned kind) var))
        (rseq (vec frames))))

(defn- current-owned-var-in
  [s]
  (when (= :owned (:kind (peek (:frames s))))
    (:var (peek (:frames s)))))

(defn- ignored-frame-active?
  [s]
  (boolean (some #(= :ignored (:kind %)) (:frames s))))

;;; Output routing

(defn- append-to-state!
  [state stream-key text]
  (swap! state
         (fn [s]
           (let [owner (when-not (ignored-frame-active? s)
                         (:output-owner s))
                 entry? (and owner (contains? (:vars s) owner))
                 s' (cond
                      entry? s
                      owner (ensure-provisional-entry s owner)
                      :else s)
                 ^StringBuilder buffer (cond
                                         entry? (get-in s' [:vars owner stream-key])
                                         owner (get-in s' [:provisional owner stream-key])
                                         :else (get-in s' [:orphan stream-key]))]
             (.append buffer ^String text)
             s')))
  nil)

(defn- write-fallback!
  [^Writer fallback x]
  (when fallback
    (cond
      (string? x) (.write fallback ^String x)
      (instance? CharSequence x) (.write fallback (str x))
      (integer? x) (.write fallback (int x))
      :else (.write fallback (str x)))))

(defn- route-text!
  [context fallback-writer stream-key text]
  (if context
    (append-to-state! (context-state context) stream-key text)
    (write-fallback! fallback-writer text)))

(defn- dynamic-routing-context
  []
  (active-context))

(defn- compatibility-routing-context
  [state-or-context]
  (compatibility-context state-or-context))

(defn- routing-writer*
  ^Writer [context-fn fallback-writer stream-key]
  (proxy [Writer] []
    (write
      ([x]
       (route-text! (context-fn) fallback-writer stream-key
                    (cond
                      (string? x) x
                      (instance? CharSequence x) (str x)
                      (integer? x) (str (char (int x)))
                      :else (String. ^chars x))))
      ([x off len]
       (route-text! (context-fn) fallback-writer stream-key
                    (cond
                      (string? x) (subs x (int off) (+ (int off) (int len)))
                      (instance? CharSequence x) (subs (str x)
                                                       (int off)
                                                       (+ (int off) (int len)))
                      :else (String. ^chars x (int off) (int len))))))
    (flush []
      (when fallback-writer
        (.flush fallback-writer)))
    (close [])))

(defn routing-writer
  "Return a java.io.Writer that routes writes for `stream-key` (:out or :err).

   Arity 1 is the runner-facing form and consults the dynamic capture context
   at write time, falling back to the writer active at construction when capture
   is disabled. Arity 2 preserves direct state/context-based tests when no
   dynamic context has been installed; an explicit disabled context still routes
   to the fallback writer instead of the supplied state."
  (^Writer [stream-key]
   (let [fallback-writer (case stream-key
                           :out *out*
                           :err *err*)]
     (routing-writer* dynamic-routing-context fallback-writer stream-key)))
  (^Writer [state-or-context stream-key]
   (let [fallback-writer (case stream-key
                           :out *out*
                           :err *err*)]
     (routing-writer* #(compatibility-routing-context state-or-context)
                      fallback-writer
                      stream-key))))

(defn with-output-owner
  "Run `f` while routed output for the active/supplied context belongs to `v`.

   This does not create clojure.test report frames, public result entries, or
   test counts. Fixture setup output/assertions are held provisionally until
   clojure.test actually begins the var; if an :each fixture short-circuits and
   never calls the test function, provisional output is kept non-public."
  [state-or-context v f]
  (let [state (normalize-state state-or-context)
        previous-owner (:output-owner @state)]
    (swap! state assoc :output-owner v)
    (try
      (f)
      (finally
        (swap! state #(-> %
                          (assoc :output-owner previous-owner)
                          (flush-provisional-entry v)))))))

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
  [context v]
  (let [state (context-state context)]
    (swap! state
           (fn [s]
             (if (and (not (ignored-frame-active? s))
                      (owns-var? context v))
               (let [previous-owner (:output-owner s)]
                 (-> s
                     (promote-provisional-entry v)
                     (update :frames conj {:kind :owned
                                           :var v
                                           :previous-output-owner previous-owner})
                     (assoc :current v
                            :output-owner v)
                     (update-in [:counts :test] (fnil inc 0))))
               (-> s
                   (update :frames conj {:kind :ignored
                                         :var v
                                         :previous-output-owner (:output-owner s)})
                   (assoc :current (top-owned-var (:frames s))
                          :output-owner nil)))))))

(defn- end-var!
  [context]
  (let [state (context-state context)]
    (swap! state
           (fn [s]
             (let [{:keys [kind var previous-output-owner]} (peek (:frames s))
                   remaining-frames (pop (:frames s))]
               (cond-> (assoc s
                              :frames remaining-frames
                              :current (top-owned-var remaining-frames)
                              :output-owner previous-output-owner)
                 (= :owned kind) (assoc :last-completed var)))))))

(defn- assertion-owner-var
  [context s]
  (or (current-owned-var-in s)
      (let [owner (:output-owner s)]
        (when (and owner (owns-var? context owner))
          owner))))

(defn- record-event!
  [context m]
  (let [state (context-state context)
        event (assertion m (vec (reverse test/*testing-contexts*)))]
    (swap! state
           (fn [s]
             (if (ignored-frame-active? s)
               s
               (let [v (assertion-owner-var context s)
                     s' (update-in s [:counts (:type m)] (fnil inc 0))]
                 (cond
                   (nil? v) s'
                   (contains? (:vars s') v) (update-in s' [:vars v :events] conj event)
                   :else (-> s'
                             (ensure-provisional-entry v)
                             (update-in [:provisional v :events] conj event)))))))))

(defn- handle-report!
  [context m]
  (when context
    (case (:type m)
      :begin-test-var (begin-var! context (:var m))
      :end-test-var (end-var! context)
      :pass (record-event! context m)
      :fail (record-event! context m)
      :error (record-event! context m)
      nil)))

(defn report-fn
  "Return a clojure.test/report replacement.

   With no arguments, report events dispatch through the dynamically active
   context and are ignored when no context is installed or capture is disabled.
   With a state/context argument, direct tests keep legacy state-closing
   behavior, except an explicit disabled context still ignores events instead of
   falling back to the supplied state."
  ([]
   (fn [m]
     (handle-report! (active-context) m)))
  ([state-or-context]
   (fn [m]
     (handle-report! (compatibility-context state-or-context) m))))

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

(defn var-result
  "Return the canonical result entry for Var `v` from state/context."
  [state-or-context v]
  (let [state (normalize-state state-or-context)]
    (when-let [entry (get-in @state [:vars v])]
      (canonical-entry entry))))

(defn current-var-result
  "Return the canonical result entry for the current or just-completed var.

   This remains useful for end-of-var progress callbacks while also supporting
   the newer callback timing after :each fixture teardown."
  [state-or-context]
  (let [state (normalize-state state-or-context)
        s @state]
    (when-let [v (or (:current s) (:last-completed s))]
      (canonical-entry (get-in s [:vars v])))))

(defn canonical-results
  "Return unprojected result entries for every executed test var in state/context."
  [state-or-context]
  (let [state (normalize-state state-or-context)
        s @state]
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
  "Build the scoped result map from captured state/context.

   Options may include :duration-ms, :scope, and :result-format."
  [state-or-context {:keys [duration-ms scope result-format]
                     :or {scope :suite}}]
  (let [state (normalize-state state-or-context)
        s @state
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
  "Return output captured outside any public test var as {:out s :err s}."
  [state-or-context]
  (let [state (normalize-state state-or-context)
        s @state]
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
