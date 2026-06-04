(ns scry.cli.results
  "Result-file naming, filesystem lifecycle, and EDN sanitization for the CLI."
  (:require
   [clojure.java.io :as io]))

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
                          {:path (.getPath file)}
                          e)))))))

(defn prepare-results-dir!
  "Clear and recreate the CLI results directory for a run."
  [io-boundary]
  (let [dir (results-dir io-boundary)]
    (delete-recursive! dir)
    (when-not (.mkdirs dir)
      (throw (ex-info (str "Could not create " (.getPath dir))
                      {:path (.getPath dir)})))
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

(defn result-file-name
  "Return the namespace-prefixed result EDN filename for a canonical entry."
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

(declare edn-readable-data)

(defn- throwable-stacktrace
  [^Throwable t]
  (let [sw (java.io.StringWriter.)]
    (.printStackTrace t (java.io.PrintWriter. sw))
    (str sw)))

(defn- throwable-data
  [^Throwable t]
  (cond-> {:type :throwable
           :class (symbol (.getName (class t)))
           :message (.getMessage t)
           :stacktrace (throwable-stacktrace t)}
    (ex-data t) (assoc :data (edn-readable-data (ex-data t)))
    (.getCause t) (assoc :cause (throwable-data (.getCause t)))))

(defn- edn-scalar?
  [value]
  (or (nil? value)
      (string? value)
      (keyword? value)
      (symbol? value)
      (number? value)
      (true? value)
      (false? value)
      (char? value)
      (uuid? value)
      (inst? value)))

(defn- safe-pr-str
  [value]
  (try
    (pr-str value)
    (catch Throwable e
      (str "#<pr-str failed: " (.getName (class e))
           (when-let [message (.getMessage e)]
             (str " " message))
           ">"))))

(defn- object-data
  [value]
  {:type :object
   :class (symbol (.getName (class value)))
   :pr-str (safe-pr-str value)})

(defn- array-data
  [value]
  (mapv (fn [idx]
          (edn-readable-data (java.lang.reflect.Array/get value idx)))
        (range (java.lang.reflect.Array/getLength value))))

(defn edn-readable-data
  "Recursively coerce data into values readable by clojure.edn/read-string.

  EDN scalar leaves pass through. Throwables retain class/message/stacktrace,
  common collection types recurse, arrays/iterables become vectors, and other
  objects become maps with class and pr-str detail."
  [value]
  (cond
    (edn-scalar? value)
    value

    (instance? Throwable value)
    (throwable-data value)

    (map? value)
    (into {}
          (map (fn [[k v]] [(edn-readable-data k) (edn-readable-data v)]))
          value)

    (instance? java.util.Map value)
    (into {}
          (map (fn [[k v]] [(edn-readable-data k) (edn-readable-data v)]))
          value)

    (vector? value)
    (mapv edn-readable-data value)

    (set? value)
    (into #{} (map edn-readable-data) value)

    (seq? value)
    (doall (map edn-readable-data value))

    (.isArray (class value))
    (array-data value)

    (instance? Iterable value)
    (mapv edn-readable-data value)

    :else
    (object-data value)))

(defn write-result-files!
  "Write readable EDN result files for failing/erroring canonical entries.

  Returns a vector of written file paths."
  [dir entries]
  (mapv (fn [entry]
          (let [file (io/file dir (result-file-name entry))]
            (spit file (pr-str (edn-readable-data entry)))
            (.getPath file)))
        (filter failure-entry? entries)))
