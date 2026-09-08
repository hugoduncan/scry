(ns scry.cli.results
  "Result-file naming, filesystem lifecycle, and EDN sanitization for the CLI."
  (:require
   [clojure.java.io :as io]))

(defn results-dir
  "Return the .scry-results directory for an IO boundary map containing :cwd."
  ^java.io.File [{:keys [cwd]}]
  (io/file cwd ".scry-results"))

(defn- directory-without-following-symlinks?
  [path]
  (java.nio.file.Files/isDirectory
   path
   (into-array java.nio.file.LinkOption
               [java.nio.file.LinkOption/NOFOLLOW_LINKS])))

(defn- delete-recursive!
  [^java.io.File file]
  (let [^java.nio.file.Path path (.toPath file)]
    (when (java.nio.file.Files/exists
           path
           (into-array java.nio.file.LinkOption
                       [java.nio.file.LinkOption/NOFOLLOW_LINKS]))
      (when (directory-without-following-symlinks? path)
        (with-open [children (java.nio.file.Files/newDirectoryStream path)]
          (doseq [^java.nio.file.Path child children]
            (delete-recursive! (.toFile child)))))
      (try
        (java.nio.file.Files/delete path)
        (catch java.io.IOException e
          (throw (ex-info (str "Could not delete " (.getPath file))
                          {:type :scry.cli/runner-error
                           :path (.getPath file)}
                          e)))))))

(defn prepare-results-dir!
  "Clear and recreate the CLI results directory for a run."
  [io-boundary]
  (let [^java.io.File dir (results-dir io-boundary)]
    (delete-recursive! dir)
    (when-not (.mkdirs dir)
      (throw (ex-info (str "Could not create " (.getPath dir))
                      {:type :scry.cli/runner-error
                       :path (.getPath dir)})))
    dir))

(defn- encode-char
  [^Character c]
  (if (or (<= (int \a) (int c) (int \z))
          (<= (int \A) (int c) (int \Z))
          (<= (int \0) (int c) (int \9))
          (contains? #{\. \_ \-} c))
    (str c)
    (format "_u%04x_" (int c))))

(defn encode-file-segment
  "Encode a namespace or var-name segment for a deterministic result filename."
  [segment]
  (apply str (map encode-char (str segment))))

(defn concrete-var-symbol?
  "True when x is a concrete namespace-qualified var symbol."
  [x]
  (and (symbol? x)
       (seq (namespace x))
       (seq (name x))))

(defn concrete-var-backed-entry?
  "True when a canonical entry is attributable to a concrete test var."
  [entry]
  (concrete-var-symbol? (:var entry)))

(defn result-file-name
  "Return the namespace-prefixed result EDN filename for a var-backed entry."
  [entry]
  (let [var-symbol (:var entry)]
    (str (encode-file-segment (namespace var-symbol))
         "__"
         (encode-file-segment (name var-symbol))
         ".edn")))

(defn failure-entry?
  "True when a canonical result entry should be written to a result file."
  [entry]
  (contains? #{:fail :error} (:status entry)))

(defn synthetic-token
  "Return a synthetic suite-level display token for status and ordinal."
  [status ordinal]
  (str (case status
         :error "suite-error"
         :fail "suite-fail"
         :unknown "suite-unknown"
         "suite-result")
       "-"
       ordinal))

(defn synthetic-display-label
  "Return a human progress label for a synthetic canonical entry."
  [entry token]
  (let [ns-name (some-> (:ns entry) str)]
    (if (seq ns-name)
      (str ns-name "/" token)
      token)))

(defn- synthetic-file-name
  [entry token]
  (let [base (str (encode-file-segment token) ".edn")
        ns-name (some-> (:ns entry) str)]
    (if (seq ns-name)
      (str (encode-file-segment ns-name) "__" base)
      base)))

(defn- next-synthetic-token
  [counters status]
  (let [ordinal (inc (get counters status 0))]
    [(assoc counters status ordinal)
     (synthetic-token status ordinal)]))

(defn- collision-suffixed
  [filename suffix]
  (str (subs filename 0 (- (count filename) 4))
       "--"
       suffix
       ".edn"))

(defn- unique-file-name
  [used filename]
  (if-not (contains? used filename)
    filename
    (loop [suffix 2]
      (let [candidate (collision-suffixed filename suffix)]
        (if (contains? used candidate)
          (recur (inc suffix))
          candidate)))))

(defn result-file-assignments
  "Return failing/erroring entries with deterministic result-file names.

  Var-backed filenames keep the existing namespace-prefixed shape. Synthetic
  entries use per-status suite-level names with deterministic collision suffixes
  for file paths when needed."
  ([entries]
   (result-file-assignments entries #{}))
  ([entries reserved-filenames]
   (let [reserved-var-files (into (set reserved-filenames)
                                  (comp (filter concrete-var-backed-entry?)
                                        (map result-file-name))
                                  entries)]
     (loop [remaining entries
            counters {}
            used reserved-var-files
            assignments []]
       (if-let [entry (first remaining)]
         (cond
           (not (failure-entry? entry))
           (recur (next remaining) counters used assignments)

           (concrete-var-backed-entry? entry)
           (recur (next remaining)
                  counters
                  (conj used (result-file-name entry))
                  (conj assignments {:entry entry
                                     :filename (result-file-name entry)}))

           :else
           (let [[counters token] (next-synthetic-token counters (:status entry))
                 filename (->> token
                               (synthetic-file-name entry)
                               (unique-file-name used))]
             (recur (next remaining)
                    counters
                    (conj used filename)
                    (conj assignments {:entry entry
                                       :filename filename
                                       :token token}))))
         assignments)))))

(def default-sanitizer-limits
  {:max-depth 20
   :max-seq-length 100
   :max-string-length 20000
   :max-throwable-depth 8
   :max-stack-frames 80
   :max-suppressed 8
   :max-ex-data-depth 8})

(defn- class-name
  [value]
  (.getName (class value)))

(defn- truncated
  [reason]
  {:scry/truncated reason})

(defn- cycle-placeholder
  [value]
  {:scry/cycle true :class (class-name value)})

(defn- bounded-string
  [s {:keys [max-string-length]}]
  (if (and max-string-length (> (count s) max-string-length))
    (str (subs s 0 max-string-length) "…" (pr-str (truncated :max-string-length)))
    s))

(defn- non-edn-placeholder
  [value opts]
  {:scry/non-edn-class (class-name value)
   :str (bounded-string
         (try
           (str value)
           (catch Throwable e
             (str "#<str failed: " (class-name e) ">")))
         opts)})

(defn- edn-scalar?
  [value]
  (or (nil? value)
      (keyword? value)
      (symbol? value)
      (number? value)
      (true? value)
      (false? value)
      (char? value)
      (uuid? value)
      (inst? value)))

(declare edn-readable-data*)

(defn- stack-frame-data
  [^StackTraceElement frame]
  {:class (.getClassName frame)
   :method (.getMethodName frame)
   :file (.getFileName frame)
   :line (.getLineNumber frame)})

(defn- throwable-access
  [^Throwable t f opts]
  (try
    (f t)
    (catch Throwable e
      (non-edn-placeholder e opts))))

(defn- throwable-access-failed?
  [value]
  (and (map? value) (contains? value :scry/non-edn-class)))

(defn- throwable-data*
  [^Throwable t opts depth]
  (if (>= depth (:max-throwable-depth opts))
    (truncated :throwable-cause-depth)
    (let [^java.util.IdentityHashMap seen (:throwable-seen opts)]
      (if (.containsKey seen t)
        (cycle-placeholder t)
        (do
          (.put seen t true)
          (try
            (let [ex-data-value (when (instance? clojure.lang.IExceptionInfo t)
                                  (throwable-access t ex-data opts))
                  ex-opts (assoc opts :max-depth (:max-ex-data-depth opts))
                  message (throwable-access t #(.getMessage ^Throwable %) opts)
                  stack-trace (throwable-access t #(.getStackTrace ^Throwable %) opts)
                  cause (throwable-access t #(.getCause ^Throwable %) opts)
                  suppressed-values (throwable-access t #(.getSuppressed ^Throwable %) opts)
                  trace (when-not (throwable-access-failed? stack-trace)
                          (take (:max-stack-frames opts) stack-trace))
                  suppressed (when-not (throwable-access-failed? suppressed-values)
                               (take (:max-suppressed opts) suppressed-values))]
              (cond-> {:type (symbol (class-name t))
                       :message (if (throwable-access-failed? message)
                                  message
                                  (some-> message (bounded-string opts)))
                       :at (if (throwable-access-failed? stack-trace)
                             stack-trace
                             (some-> (first stack-trace) stack-frame-data))
                       :trace (if (throwable-access-failed? stack-trace)
                                stack-trace
                                (mapv stack-frame-data trace))}
                ex-data-value (assoc :data (if (throwable-access-failed? ex-data-value)
                                             ex-data-value
                                             (edn-readable-data* ex-data-value ex-opts 0)))
                (and cause (throwable-access-failed? cause)) (assoc :cause cause)
                (and cause (not (throwable-access-failed? cause))) (assoc :cause (throwable-data* cause opts (inc depth)))
                (and suppressed-values (throwable-access-failed? suppressed-values)) (assoc :suppressed suppressed-values)
                (seq suppressed) (assoc :suppressed (mapv #(throwable-data* % opts (inc depth))
                                                          suppressed))))
            (finally
              (.remove seen t))))))))

(defn- with-identity
  [value opts f]
  (let [^java.util.IdentityHashMap seen (:seen opts)]
    (if (.containsKey seen value)
      (cycle-placeholder value)
      (do
        (.put seen value true)
        (try
          (try
            (f)
            (catch Throwable _
              (non-edn-placeholder value opts)))
          (finally
            (.remove seen value)))))))

(defn- map-entry-data
  [opts depth [k v]]
  [(edn-readable-data* k opts (inc depth))
   (edn-readable-data* v opts (inc depth))])

(defn- limited-with-truncation
  [coll opts]
  (let [limit (:max-seq-length opts)
        it (clojure.lang.RT/iter coll)]
    (loop [n 0
           values []]
      (if (and (< n limit) (.hasNext it))
        (recur (inc n) (conj values (.next it)))
        {:values values
         :truncated? (.hasNext it)}))))

(defn- append-truncation-sentinel
  [xs truncated?]
  (if truncated?
    (concat xs [(truncated :max-seq-length)])
    xs))

(defn- limited-map-entries
  [value opts depth]
  (let [{:keys [values truncated?]} (limited-with-truncation value opts)
        entries (map (partial map-entry-data opts depth) values)]
    (if truncated?
      (concat entries [[(truncated :max-seq-length)
                        (truncated :max-seq-length)]])
      entries)))

(defn edn-readable-data*
  [value opts depth]
  (let [opts (merge default-sanitizer-limits opts)]
    (cond
      (> depth (:max-depth opts))
      (truncated :max-depth)

      (string? value)
      (bounded-string value opts)

      (edn-scalar? value)
      value

      (instance? Throwable value)
      (throwable-data* value (update opts :throwable-seen #(or % (java.util.IdentityHashMap.))) 0)

      (map? value)
      (with-identity value opts
        #(into {} (limited-map-entries value opts depth)))

      (instance? java.util.Map value)
      (with-identity value opts
        #(into {} (limited-map-entries value opts depth)))

      (vector? value)
      (with-identity value opts
        #(let [{:keys [values truncated?]} (limited-with-truncation value opts)]
           (vec (append-truncation-sentinel
                 (map (fn [x] (edn-readable-data* x opts (inc depth))) values)
                 truncated?))))

      (set? value)
      (with-identity value opts
        #(let [{:keys [values truncated?]} (limited-with-truncation value opts)]
           (into #{} (append-truncation-sentinel
                      (map (fn [x] (edn-readable-data* x opts (inc depth))) values)
                      truncated?))))

      (seq? value)
      (with-identity value opts
        #(let [{:keys [values truncated?]} (limited-with-truncation value opts)]
           (doall (append-truncation-sentinel
                   (map (fn [x] (edn-readable-data* x opts (inc depth))) values)
                   truncated?))))

      (.isArray (class value))
      (with-identity value opts
        #(let [{:keys [values truncated?]} (limited-with-truncation value opts)]
           (vec (append-truncation-sentinel
                 (map (fn [x] (edn-readable-data* x opts (inc depth))) values)
                 truncated?))))

      (instance? Iterable value)
      (with-identity value opts
        #(let [{:keys [values truncated?]} (limited-with-truncation value opts)]
           (vec (append-truncation-sentinel
                 (map (fn [x] (edn-readable-data* x opts (inc depth))) values)
                 truncated?))))

      :else
      (non-edn-placeholder value opts))))

(defn edn-readable-data
  "Recursively coerce data into bounded values readable by clojure.edn/read-string.

  Accepts optional sanitizer limits such as `:max-depth`, `:max-seq-length`,
  `:max-string-length`, and `:seen` (`java.util.IdentityHashMap`). Pathological
  data is replaced with tagged placeholder maps."
  ([value]
   (edn-readable-data value {}))
  ([value opts]
   (edn-readable-data* value
                       (merge default-sanitizer-limits
                              {:seen (java.util.IdentityHashMap.)}
                              opts)
                       0)))

(defn- atomic-write-entry!
  "Publish entry at filename without exposing a partially-written final file."
  [^java.io.File dir filename entry]
  (let [target (.toPath (io/file dir filename))
        temp (java.nio.file.Files/createTempFile
              (.toPath dir)
              ".scry-result-"
              ".tmp"
              (make-array java.nio.file.attribute.FileAttribute 0))]
    (try
      (spit (.toFile temp) (pr-str (edn-readable-data entry)))
      (java.nio.file.Files/move
       temp
       target
       (into-array java.nio.file.CopyOption
                   [java.nio.file.StandardCopyOption/ATOMIC_MOVE
                    java.nio.file.StandardCopyOption/REPLACE_EXISTING]))
      (.toString target)
      (catch Throwable e
        ;; A failed attempt must not leave an apparently complete final path.
        ;; Cleanup is deliberately secondary to the publication failure.
        (try
          (java.nio.file.Files/deleteIfExists temp)
          (catch Throwable _))
        (throw e)))))

(defn create-result-sink
  "Create state for synchronous completed-entry artifact publication.

  `:publish-entry!`, when supplied, receives dir, filename, and canonical entry.
  It is a narrow filesystem boundary for tests; publication failures are recorded
  in the sink and never escape a runner callback."
  ([dir]
   (create-result-sink dir {}))
  ([dir {:keys [publish-entry!]
         :or {publish-entry! atomic-write-entry!}}]
   {:dir dir
    :publish-entry! publish-entry!
    :state (atom {:identities {}
                  :completion-order []})}))

(defn sink-state
  "Return the current observable state of a result sink."
  [sink]
  @(:state sink))

(defn- identity-state
  [state var-symbol]
  (get-in state [:identities var-symbol]
          {:occurrence 0
           :required-generation 0}))

(defn- publish-snapshot!
  [{:keys [dir publish-entry! state]} var-symbol snapshot]
  (try
    (let [path (publish-entry! dir (:filename snapshot) (:entry snapshot))]
      (swap! state update-in [:identities var-symbol]
             #(assoc %
                     :successful-generation (:generation snapshot)
                     :successful-path path
                     :latest-error nil))
      path)
    (catch Throwable e
      (swap! state update-in [:identities var-symbol]
             #(assoc % :latest-error e))
      nil)))

(defn handle-completed-entry!
  "Synchronously observe one completed canonical entry.

  Every concrete entry advances its occurrence ordinal. Only failing/erroring
  concrete entries are published, and contained publication failures are kept in
  sink state for later reconciliation. Returns nil so this is observational."
  [{:keys [state] :as sink} entry]
  (when (concrete-var-backed-entry? entry)
    (let [var-symbol (:var entry)
          failure? (failure-entry? entry)
          identity (identity-state @state var-symbol)
          occurrence (inc (:occurrence identity))]
      (swap! state update-in [:identities var-symbol]
             #(assoc (or % identity) :occurrence occurrence))
      (when failure?
        (let [generation (inc (:required-generation identity))
              snapshot {:entry entry
                        :occurrence occurrence
                        :generation generation
                        :filename (result-file-name entry)}]
          (swap! state
                 (fn [current]
                   (-> current
                       (update :completion-order #(if (some #{var-symbol} %) % (conj % var-symbol)))
                       (update-in [:identities var-symbol]
                                  #(assoc (or % identity)
                                          :required-generation generation
                                          :latest-required snapshot
                                          :latest-error nil)))))
          (publish-snapshot! sink var-symbol snapshot)))))
  nil)

(defn- canonical-occurrences
  [entries]
  (reduce (fn [occurrences [index entry]]
            (if (concrete-var-backed-entry? entry)
              (update occurrences (:var entry) (fnil conj []) {:index index :entry entry})
              occurrences))
          {}
          (map-indexed vector entries)))

(defn- callback-reserved-filenames
  [state]
  (into #{}
        (keep (fn [[_ identity]]
                (when (or (:successful-path identity)
                          (and (:latest-required identity)
                               (> (:required-generation identity)
                                  (or (:successful-generation identity) 0))))
                  (get-in identity [:latest-required :filename]))))
        (:identities state)))

(defn- successful-paths-in-order
  [state assignments successful-synthetic-filenames]
  (let [canonical-paths
        (keep (fn [{:keys [entry filename]}]
                (cond
                  (concrete-var-backed-entry? entry)
                  (get-in state [:identities (:var entry) :successful-path])

                  (contains? successful-synthetic-filenames filename)
                  (.getPath (io/file (:dir state) filename))))
              assignments)
        callback-only (keep #(get-in state [:identities % :successful-path])
                            (:completion-order state))]
    (vec (distinct (concat canonical-paths callback-only)))))

(defn- ordered-unresolved
  "Return unresolved artifacts in canonical-first deterministic order.

  Canonical concrete identities are ordered by their first canonical occurrence,
  then callback-only identities retain completion order, and synthetic
  assignments follow their established assignment order."
  [state entries synthetic-errors]
  (let [canonical-vars (->> entries
                            (filter concrete-var-backed-entry?)
                            (map :var)
                            distinct)
        callback-only-vars (remove (set canonical-vars) (:completion-order state))
        ordered-vars (concat canonical-vars callback-only-vars)
        unresolved-concrete
        (keep (fn [var-symbol]
                (let [identity (get-in state [:identities var-symbol])]
                  (when (and (:latest-required identity)
                             (> (:required-generation identity)
                                (or (:successful-generation identity) 0)))
                    {:var var-symbol
                     :entry (get-in identity [:latest-required :entry])
                     :error (:latest-error identity)})))
              ordered-vars)]
    (vec (concat unresolved-concrete
                 (map #(select-keys % [:entry :error]) synthetic-errors)))))

(defn reconcile-result-sink!
  "Reconcile a sink with normally returned canonical entries.

  Retries the latest unpublished callback failure using its ordinally matching
  canonical occurrence when that occurrence still fails. It also publishes the
  latest failing occurrence for callback-ignoring runners and synthetic entries.
  Returns only successfully published final paths and unresolved identities; all
  individual publication failures remain contained."
  [sink entries]
  (let [{:keys [state] :as sink} sink
        occurrences (canonical-occurrences entries)]
    ;; A callback's all-entry ordinal identifies precisely which duplicate
    ;; canonical execution may replace its completion-time snapshot.
    (doseq [[var-symbol identity] (:identities @state)
            :let [snapshot (:latest-required identity)]
            :when (and snapshot
                       (> (:generation snapshot)
                          (or (:successful-generation identity) 0)))]
      (let [matching (get-in occurrences [var-symbol (dec (:occurrence snapshot)) :entry])
            retry (if (failure-entry? matching)
                    (assoc snapshot :entry matching)
                    snapshot)]
        (publish-snapshot! sink var-symbol retry)))
    ;; An unmatched canonical failure is from a runner which omitted the
    ;; callback. Publishing the latest one preserves deterministic final state.
    (doseq [[var-symbol var-occurrences] occurrences
            :let [callback-count (get-in @state [:identities var-symbol :occurrence] 0)
                  unmatched (drop callback-count var-occurrences)
                  latest-failure (last (filter #(failure-entry? (:entry %)) unmatched))]
            :when latest-failure]
      (let [identity (identity-state @state var-symbol)
            generation (inc (:required-generation identity))
            snapshot {:entry (:entry latest-failure)
                      :occurrence (inc callback-count)
                      :generation generation
                      :filename (result-file-name (:entry latest-failure))}]
        (swap! state update-in [:identities var-symbol]
               #(assoc (or % identity)
                       :required-generation generation
                       :latest-required snapshot
                       :latest-error nil))
        (publish-snapshot! sink var-symbol snapshot)))
    (let [reserved (callback-reserved-filenames @state)
          assignments (result-file-assignments entries reserved)
          synthetic (remove #(concrete-var-backed-entry? (:entry %)) assignments)
          synthetic-results
          (mapv (fn [{:keys [entry filename] :as assignment}]
                  (try
                    (assoc assignment :path ((:publish-entry! sink) (:dir sink) filename entry))
                    (catch Throwable e
                      (assoc assignment :error e))))
                synthetic)
          synthetic-errors (filter :error synthetic-results)
          successful-synthetic-filenames (into #{} (keep #(when (:path %) (:filename %))) synthetic-results)
          paths (successful-paths-in-order (assoc @state :dir (:dir sink))
                                           assignments
                                           successful-synthetic-filenames)
          unresolved (ordered-unresolved @state entries synthetic-errors)]
      {:result-files (vec (distinct paths))
       :unresolved unresolved})))

(defn sink-exception-snapshot
  "Return completed callback artifacts and unresolved failures without retrying."
  [sink]
  (let [state (assoc (sink-state sink) :dir (:dir sink))]
    {:result-files (vec (distinct
                         (keep #(get-in state [:identities % :successful-path])
                               (:completion-order state))))
     :unresolved (ordered-unresolved state [] [])}))

(defn write-result-files!
  "Write readable EDN result files for failing/erroring canonical entries.

  Returns a vector of written file paths."
  [dir entries]
  (mapv (fn [{:keys [entry filename]}]
          (atomic-write-entry! dir filename entry))
        (result-file-assignments entries)))
