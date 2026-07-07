(ns scry.cli.results
  "Result-file naming, filesystem lifecycle, and EDN sanitization for the CLI."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn results-dir
  "Return the .scry-results directory for an IO boundary map containing :cwd."
  [{:keys [cwd]}]
  (io/file cwd ".scry-results"))

(defn- directory-without-following-symlinks?
  [path]
  (java.nio.file.Files/isDirectory
   path
   (into-array java.nio.file.LinkOption
               [java.nio.file.LinkOption/NOFOLLOW_LINKS])))

(defn- delete-recursive!
  [file]
  (let [path (.toPath file)]
    (when (java.nio.file.Files/exists
           path
           (into-array java.nio.file.LinkOption
                       [java.nio.file.LinkOption/NOFOLLOW_LINKS]))
      (when (directory-without-following-symlinks? path)
        (with-open [children (java.nio.file.Files/newDirectoryStream path)]
          (doseq [child children]
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
  (let [dir (results-dir io-boundary)]
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
  [entries]
  (let [reserved-var-files (into #{}
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
        assignments))))

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
    (let [seen (:throwable-seen opts)]
      (if (.containsKey seen t)
        (cycle-placeholder t)
        (do
          (.put seen t true)
          (try
            (let [ex-data-value (when (instance? clojure.lang.IExceptionInfo t)
                                  (throwable-access t ex-data opts))
                  ex-opts (assoc opts :max-depth (:max-ex-data-depth opts))
                  message (throwable-access t #(.getMessage %) opts)
                  stack-trace (throwable-access t #(.getStackTrace %) opts)
                  cause (throwable-access t #(.getCause %) opts)
                  suppressed-values (throwable-access t #(.getSuppressed %) opts)
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
  (let [seen (:seen opts)]
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

(defn write-result-files!
  "Write readable EDN result files for failing/erroring canonical entries.

  Returns a vector of written file paths."
  [dir entries]
  (mapv (fn [{:keys [entry filename]}]
          (let [file (io/file dir filename)]
            (spit file (pr-str (edn-readable-data entry)))
            (.getPath file)))
        (result-file-assignments entries)))
