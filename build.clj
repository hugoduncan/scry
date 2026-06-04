(ns build
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def lib 'org.hugoduncan/scry)
(def kaocha-lib 'org.hugoduncan/scry-kaocha)
(def major-minor "0.1")
(def target-dir "target")
(def class-dir "target/classes")
(def kaocha-class-dir "target/classes-kaocha")
(def basis (b/create-basis {:project "deps.edn"}))
(def src-dirs ["src"])
(def kaocha-src-dirs ["src-kaocha"])
(def deps-file "deps.edn")
(def kaocha-dependency 'lambdaisland/kaocha)
(def project-url "https://github.com/hugoduncan/scry")
(def scm-connection "scm:git:https://github.com/hugoduncan/scry.git")
(def scm-developer-connection "scm:git:ssh://git@github.com/hugoduncan/scry.git")
(def core-description
  "An in-process Clojure test runner for AI agents and REPL-driven development.")
(def kaocha-description
  "Optional Kaocha adapter for scry's structured in-process test results.")
(def license-metadata
  {:name "Eclipse Public License 2.0"
   :url "https://www.eclipse.org/legal/epl-2.0/"
   :distribution "repo"
   :comments "SPDX-License-Identifier: EPL-2.0"})
(def developer-metadata
  {:id "hugoduncan"
   :name "Hugo Duncan"
   :email "hugo@hugoduncan.org"
   :url "https://github.com/hugoduncan"
   :roles ["maintainer" "developer"]})

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
  "Returns the core target jar path for version v."
  [v]
  (format "%s/scry-%s.jar" target-dir v))

(defn kaocha-jar-file
  "Returns the Kaocha adapter target jar path for version v."
  [v]
  (format "%s/scry-kaocha-%s.jar" target-dir v))

(defn pom-file
  "Returns the generated core pom path."
  []
  (b/pom-path {:lib lib
               :class-dir class-dir}))

(defn kaocha-pom-file
  "Returns the generated Kaocha adapter pom path."
  []
  (b/pom-path {:lib kaocha-lib
               :class-dir kaocha-class-dir}))

(defn- scm-tag
  "Returns the deterministic SCM tag value for artifact version v."
  [v]
  (str "v" v))

(defn- scm-metadata
  "Returns shared SCM metadata for artifact version v."
  [v]
  {:url project-url
   :connection scm-connection
   :developerConnection scm-developer-connection
   :tag (scm-tag v)})

(defn- artifact-metadata
  "Returns pinned public metadata for lib at artifact version v."
  [lib-symbol v]
  (case lib-symbol
    org.hugoduncan/scry
    {:name "scry"
     :description core-description
     :url project-url
     :scm (scm-metadata v)
     :license license-metadata
     :developer developer-metadata}

    org.hugoduncan/scry-kaocha
    {:name "scry-kaocha"
     :description kaocha-description
     :url project-url
     :scm (scm-metadata v)
     :license license-metadata
     :developer developer-metadata}))

(defn clean
  "Delete all build output."
  [_]
  (b/delete {:path target-dir}))

(defn- deps-edn
  []
  (edn/read-string (slurp deps-file)))

(defn kaocha-dependency-version
  "Returns the lambdaisland/kaocha version from the development :kaocha alias.

   The adapter pom deliberately reads this source of truth so alias and release
   artifact metadata cannot drift silently."
  []
  (let [version (get-in (deps-edn) [:aliases :kaocha :extra-deps kaocha-dependency :mvn/version])]
    (when (str/blank? version)
      (throw (ex-info "deps.edn :kaocha alias is missing lambdaisland/kaocha :mvn/version"
                      {:dependency kaocha-dependency})))
    version))

(defn- lib-parts
  [lib-symbol]
  (let [namespace (namespace lib-symbol)
        name (name lib-symbol)]
    (if namespace
      [namespace name]
      [name name])))

(defn- xml-escape
  [value]
  (-> (str value)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&apos;")))

(defn- dependency-xml
  [{:keys [lib version]}]
  (let [[group artifact] (lib-parts lib)]
    (str "    <dependency>\n"
         "      <groupId>" (xml-escape group) "</groupId>\n"
         "      <artifactId>" (xml-escape artifact) "</artifactId>\n"
         "      <version>" (xml-escape version) "</version>\n"
         "    </dependency>\n")))

(defn- xml-element
  [indent tag value]
  (str indent "<" tag ">" (xml-escape value) "</" tag ">\n"))

(defn- license-xml
  [indent {:keys [name url distribution comments]}]
  (str indent "<licenses>\n"
       indent "  <license>\n"
       (xml-element (str indent "    ") "name" name)
       (xml-element (str indent "    ") "url" url)
       (xml-element (str indent "    ") "distribution" distribution)
       (xml-element (str indent "    ") "comments" comments)
       indent "  </license>\n"
       indent "</licenses>\n"))

(defn- scm-xml
  [indent {:keys [url connection developerConnection tag]}]
  (str indent "<scm>\n"
       (xml-element (str indent "  ") "url" url)
       (xml-element (str indent "  ") "connection" connection)
       (xml-element (str indent "  ") "developerConnection" developerConnection)
       (xml-element (str indent "  ") "tag" tag)
       indent "</scm>\n"))

(defn- developer-xml
  [indent {:keys [id name email url roles]}]
  (str indent "<developers>\n"
       indent "  <developer>\n"
       (xml-element (str indent "    ") "id" id)
       (xml-element (str indent "    ") "name" name)
       (xml-element (str indent "    ") "email" email)
       (xml-element (str indent "    ") "url" url)
       (when (seq roles)
         (str indent "    <roles>\n"
              (apply str (map #(xml-element (str indent "      ") "role" %) roles))
              indent "    </roles>\n"))
       indent "  </developer>\n"
       indent "</developers>\n"))

(defn- pom-data
  "Returns tools.build pom-data for shared public metadata."
  [{:keys [description url license developer]}]
  (let [roles (into [:roles]
                    (map (fn [role] [:role role]))
                    (:roles developer))]
    [[:description description]
     [:url url]
     [:licenses
      [:license
       [:name (:name license)]
       [:url (:url license)]
       [:distribution (:distribution license)]
       [:comments (:comments license)]]]
     [:developers
      [:developer
       [:id (:id developer)]
       [:name (:name developer)]
       [:email (:email developer)]
       [:url (:url developer)]
       roles]]]))

(defn- metadata-xml
  [{:keys [description url license scm developer]}]
  (str (xml-element "  " "description" description)
       (xml-element "  " "url" url)
       (license-xml "  " license)
       (developer-xml "  " developer)
       (scm-xml "  " scm)))

(defn- pom-xml
  [{:keys [lib version dependencies]}]
  (let [[group artifact] (lib-parts lib)
        metadata (artifact-metadata lib version)]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
         "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
         "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
         "  <modelVersion>4.0.0</modelVersion>\n"
         "  <groupId>" (xml-escape group) "</groupId>\n"
         "  <artifactId>" (xml-escape artifact) "</artifactId>\n"
         "  <version>" (xml-escape version) "</version>\n"
         "  <name>" (xml-escape (:name metadata)) "</name>\n"
         (metadata-xml metadata)
         (when (seq dependencies)
           (str "  <dependencies>\n"
                (apply str (map dependency-xml dependencies))
                "  </dependencies>\n"))
         "</project>\n")))

(defn- pom-properties
  [{:keys [lib version]}]
  (let [[group artifact] (lib-parts lib)]
    (str "groupId=" group "\n"
         "artifactId=" artifact "\n"
         "version=" version "\n")))

(defn- write-adapter-pom
  [v]
  (let [pom-path (io/file (kaocha-pom-file))
        properties-path (io/file kaocha-class-dir
                                 "META-INF" "maven"
                                 (namespace kaocha-lib)
                                 (name kaocha-lib)
                                 "pom.properties")
        dependencies [{:lib lib :version v}
                      {:lib kaocha-dependency :version (kaocha-dependency-version)}]]
    (io/make-parents pom-path)
    (spit pom-path (pom-xml {:lib kaocha-lib
                             :version v
                             :dependencies dependencies}))
    (io/make-parents properties-path)
    (spit properties-path (pom-properties {:lib kaocha-lib :version v}))
    (str pom-path)))

(defn- delete-matching-files!
  [dir pred]
  (let [root (io/file dir)]
    (when (.exists root)
      (doseq [file (file-seq root)]
        (when (and (.isFile file) (pred (.getName file)))
          (io/delete-file file true))))))

(defn- clean-kaocha-output!
  []
  (b/delete {:path kaocha-class-dir})
  (delete-matching-files! target-dir #(re-matches #"scry-kaocha-.*\.jar" %)))

(defn- build-core-jar
  [v]
  (let [metadata (artifact-metadata lib v)]
    (b/copy-dir {:src-dirs src-dirs
                 :target-dir class-dir})
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version v
                  :basis basis
                  :src-dirs src-dirs
                  :scm (:scm metadata)
                  :pom-data (pom-data metadata)})
    (b/jar {:class-dir class-dir
            :jar-file (jar-file v)}))
  {:lib lib
   :version v
   :jar-file (jar-file v)
   :pom-file (pom-file)})

(defn- build-kaocha-jar
  [v]
  (b/copy-dir {:src-dirs kaocha-src-dirs
               :target-dir kaocha-class-dir})
  (write-adapter-pom v)
  (b/jar {:class-dir kaocha-class-dir
          :jar-file (kaocha-jar-file v)})
  {:lib kaocha-lib
   :version v
   :jar-file (kaocha-jar-file v)
   :pom-file (kaocha-pom-file)})

(defn jar
  "Build a core-only scry jar under target/."
  [_]
  (let [v (version)]
    (clean nil)
    (build-core-jar v)))

(defn kaocha-jar
  "Build the optional Kaocha adapter jar under target/.

   Standalone adapter builds delete only adapter-specific stale output and
   preserve any existing core jar output."
  [_]
  (let [v (version)]
    (clean-kaocha-output!)
    (build-kaocha-jar v)))

(defn jars
  "Build both release jars under target/, cleaning all build output once first."
  [_]
  (let [v (version)]
    (clean nil)
    {:version v
     :artifacts [(build-core-jar v)
                 (build-kaocha-jar v)]}))

(defn- blank-env?
  [env name]
  (str/blank? (get env name)))

(defn assert-deploy-credentials!
  "Verifies required Clojars deploy credentials are present in the environment."
  ([]
   (assert-deploy-credentials! (System/getenv)))
  ([env]
   (let [missing (->> ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"]
                      (filter #(blank-env? env %))
                      vec)]
     (when (seq missing)
       (throw (ex-info "Clojars deploy requires CLOJARS_USERNAME and CLOJARS_PASSWORD"
                       {:missing-env missing}))))
   true))

(defn- deploy-fn
  [opts]
  (or (:deploy-fn opts)
      (requiring-resolve 'deps-deploy.deps-deploy/deploy)))

(defn- deploy-artifact!
  [deploy-fn artifact]
  (deploy-fn {:installer :remote
              :artifact (:jar-file artifact)
              :pom-file (:pom-file artifact)}))

(defn deploy
  "Build and deploy the core-only scry jar to Clojars.

   Requires the :deploy alias so deps-deploy is available, and requires
   CLOJARS_USERNAME and CLOJARS_PASSWORD in the environment."
  [opts]
  (let [opts (or opts {})]
    (assert-deploy-credentials! (or (:env opts) (System/getenv)))
    (let [artifact (jar opts)]
      (deploy-artifact! (deploy-fn opts) artifact)
      artifact)))

(defn deploy-all
  "Build and deploy the core and Kaocha adapter jars to Clojars.

   Deploys the core artifact first because the adapter pom depends on the
   same-version core artifact."
  [opts]
  (let [opts (or opts {})]
    (assert-deploy-credentials! (or (:env opts) (System/getenv)))
    (let [{:keys [artifacts] :as result} (jars opts)
          deploy-fn (deploy-fn opts)]
      (doseq [artifact artifacts]
        (deploy-artifact! deploy-fn artifact))
      result)))
