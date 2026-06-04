(ns build
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def lib 'org.hugoduncan/scry)
(def major-minor "0.1")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def src-dirs ["src"])
(def target-dir "target")

(defn git-rev-count
  "Returns the repository commit count from Git.

   Throws ExceptionInfo when Git metadata is unavailable or the command returns
   a non-numeric count. The build intentionally avoids silently inventing a
   fallback version. An optional process-fn may be supplied for focused tests;
   nil uses clojure.tools.build.api/process."
  ([]
   (git-rev-count nil))
  ([process-fn]
   (let [{:keys [exit out err]} ((or process-fn b/process)
                                 {:command-args ["git" "rev-list" "--count" "HEAD"]
                                  :out :capture
                                  :err :capture})
         count-text (str/trim (or out ""))]
     (when-not (zero? exit)
       (throw (ex-info "Unable to compute version: git rev-list --count HEAD failed"
                       {:exit exit :err err})))
     (when-not (re-matches #"\d+" count-text)
       (throw (ex-info "Unable to compute version: git rev-list --count HEAD returned an invalid count"
                       {:out out :err err})))
     count-text)))

(defn version
  "Returns the build version as 0.1.<git-revcount>."
  []
  (str major-minor "." (git-rev-count)))

(defn jar-file
  "Returns the target jar path for version v."
  [v]
  (format "%s/scry-%s.jar" target-dir v))

(defn clean
  "Delete build output."
  [_]
  (b/delete {:path target-dir}))

(defn jar
  "Build a core-only scry jar under target/."
  [_]
  (let [v (version)]
    (clean nil)
    (b/copy-dir {:src-dirs src-dirs
                 :target-dir class-dir})
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version v
                  :basis basis
                  :src-dirs src-dirs})
    (b/jar {:class-dir class-dir
            :jar-file (jar-file v)})
    {:lib lib
     :version v
     :jar-file (jar-file v)}))
