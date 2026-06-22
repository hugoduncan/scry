(ns scry.kaocha-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.core :as scry]
   [scry.temp-ns :as temp-ns]))

(defmacro when-kaocha-available
  [& body]
  `(if-let [run-var# (try
                       (requiring-resolve 'scry.kaocha/run)
                       (catch java.io.FileNotFoundException _# nil))]
     (do ~@body)
     (is true "Kaocha adapter tests are skipped unless the :kaocha alias is active")))

(defn- kaocha-var
  [sym]
  (require 'scry.kaocha)
  (ns-resolve 'scry.kaocha sym))

(defn- normalize-config
  [cfg]
  (require 'kaocha.config)
  ((requiring-resolve 'kaocha.config/normalize) cfg))

(defn- kaocha-run
  ([] ((kaocha-var 'run)))
  ([opts] ((kaocha-var 'run) opts)))

(defn- suite-skip-map
  [cfg]
  (into {}
        (map (juxt :kaocha.testable/id :kaocha.testable/skip))
        (:kaocha/tests cfg)))

(defn- write-temp-project-file
  [project relative-path content]
  (let [file (io/file project relative-path)]
    (io/make-parents file)
    (spit file content)
    file))

(defn- temp-project
  []
  (.toFile (java.nio.file.Files/createTempDirectory
            "scry-kaocha-test-"
            (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- delete-recursive!
  [file]
  (when (.exists file)
    (when (.isDirectory file)
      (doseq [child (.listFiles file)]
        (delete-recursive! child)))
    (.delete file)))

(defmacro with-temp-project
  [[sym] & body]
  `(let [~sym (temp-project)]
     (try
       ~@body
       (finally
         (delete-recursive! ~sym)))))

(defn- remove-namespaces!
  [ns-syms]
  (doseq [ns-sym ns-syms]
    (remove-ns ns-sym)))

(defmacro with-user-dir
  [dir & body]
  `(let [old-dir# (System/getProperty "user.dir")]
     (System/setProperty "user.dir" (.getAbsolutePath (io/file ~dir)))
     (try
       ~@body
       (finally
         (System/setProperty "user.dir" old-dir#)))))

(defmacro with-user-dir-and-ns-cleanup
  [dir ns-syms & body]
  `(let [old-dir# (System/getProperty "user.dir")
         ns-syms# ~ns-syms]
     (System/setProperty "user.dir" (.getAbsolutePath (io/file ~dir)))
     (try
       ~@body
       (finally
         (System/setProperty "user.dir" old-dir#)
         (remove-namespaces! ns-syms#)))))

(defn- unique-ns
  [prefix leaf]
  (temp-ns/unique-ns prefix leaf))

(defn- exact-ns-pattern
  [ns-name]
  (java.util.regex.Pattern/quote (str ns-name)))

(defn- ns->test-path
  [ns-name]
  (str "test/" (-> ns-name
                   (str/replace "." "/")
                   (str/replace "-" "_"))
       ".clj"))

(defn- write-suite-test-ns
  [project ns-name pass?]
  (write-temp-project-file
   project
   (ns->test-path ns-name)
   (str "(ns " ns-name "\n"
        "  (:require [clojure.test :refer [deftest is]]))\n\n"
        "(deftest suite-test\n"
        "  (is " (if pass? "true" "false") "))\n")))

(defn- write-mixed-test-ns
  "Write a two-var namespace with two failing vars (`keep-test` and
  `drop-test`) for focus-filtering assertions. Both vars fail so the
  suite-scope `:results` collection reflects the executed var set
  (suite scope omits passing entries)."
  [project ns-name]
  (write-temp-project-file
   project
   (ns->test-path ns-name)
   (str "(ns " ns-name "\n"
        "  (:require [clojure.test :refer [deftest is]]))\n\n"
        "(deftest keep-test\n"
        "  (is (= 1 2)))\n\n"
        "(deftest drop-test\n"
        "  (is (= 3 4)))\n")))

(defn- write-tests-edn
  [project content]
  (write-temp-project-file project "tests.edn" content))

(defn- suite-config
  ([ids]
   (suite-config ids "scry\\.fixtures\\.passing"))
  ([ids ns-pattern]
   (-> {:kaocha/tests (mapv (fn [id]
                              {:kaocha.testable/id id
                               :kaocha.testable/type :kaocha.type/clojure.test
                               :kaocha/source-paths []
                               :kaocha/test-paths [(str (.getAbsolutePath (io/file "test" "scry" "fixtures")))]
                               :kaocha/ns-patterns [ns-pattern]})
                            ids)
        :kaocha/reporter [:existing/reporter]
        :kaocha/color? true
        :custom/key :preserved}
       normalize-config
       (assoc :custom/key :preserved)
       (dissoc :kaocha/plugins))))

(def nested-kaocha-result (atom nil))

(defn invoke-kaocha-run-fixture
  []
  (kaocha-run {:config (suite-config [:unit] "scry\\.fixtures\\.failing")
               :result-format
               {:suite {:top-level-keys [:summary :pass? :results :failures]
                        :entry-keys [:var :status :assertions :out]
                        :assertions? true
                        :output? true}}}))

(deftest invokes-kaocha-run-test
  (when-kaocha-available
   (println "outer kaocha before")
   (let [result (invoke-kaocha-run-fixture)]
     (reset! nested-kaocha-result result)
     (is (false? (:pass? result)))
     (is (= 'scry.fixtures.failing/equality-fails
            (:var (first (:results result))))))
   (println "outer kaocha after")))

(deftest suite-selection-application-preserves-resolved-exact-ids-test
  ;; Selected suite ids are resolved before being passed to Kaocha, and the
  ;; resolved exact ids are passed through without converting them back to CLI
  ;; strings that would lose namespace/type information.
  (when-kaocha-available
   (testing "namespace-qualified keyword ids are not rematched by name"
     (let [cfg {:kaocha/tests [{:kaocha.testable/id :alpha/unit}
                               {:kaocha.testable/id :beta/unit}]}
           result ((kaocha-var 'select-suites) cfg [:alpha/unit])]
       (is (= {:alpha/unit nil
               :beta/unit true}
              (suite-skip-map result)))))
   (testing "string ids remain exact after selector resolution"
     (let [cfg {:kaocha/tests [{:kaocha.testable/id "unit"}
                               {:kaocha.testable/id :alpha/unit}
                               {:kaocha.testable/id :beta/unit}]}
           result ((kaocha-var 'select-suites) cfg ["unit"])]
       (is (= {"unit" nil
               :alpha/unit true
               :beta/unit true}
              (suite-skip-map result)))))))

(deftest suite-selector-validation-test
  ;; Suite option validation is strict so REPL calls fail clearly instead of
  ;; silently running all suites or no suites.
  (when-kaocha-available
   (testing "supplying both :suite and :suites is an ex-info API error"
     (try
       ((kaocha-var 'suite-selectors) {:suite :unit :suites [:integration]})
       (is false "Expected :suite/:suites conflict to throw")
       (catch clojure.lang.ExceptionInfo e
         (is (re-find #"either :suite or :suites" (ex-message e)))
         (is (= {:suite :unit :suites [:integration]} (ex-data e))))))
   (testing "invalid plural :suites values are ex-info API errors"
     (doseq [invalid ["unit" {:suite :unit} :unit []]]
       (try
         ((kaocha-var 'suite-selectors) {:suites invalid})
         (is false (str "Expected invalid :suites to throw for " (pr-str invalid)))
         (catch clojure.lang.ExceptionInfo e
           (is (re-find #":suites must be a non-empty collection" (ex-message e)))
           (is (= {:suites invalid} (ex-data e)))))))))

(deftest suite-selector-resolution-test
  ;; Selector resolution prefers exact ids, then unique text fallback, and
  ;; reports unknown or ambiguous selectors with actionable ex-data.
  (when-kaocha-available
   (let [available [:alpha/unit :beta/unit :integration "string-suite"]]
     (testing "exact ids are selected before name fallback"
       (is (= :alpha/unit ((kaocha-var 'resolve-suite-selector) available :alpha/unit)))
       (is (= "string-suite" ((kaocha-var 'resolve-suite-selector) available "string-suite"))))
     (testing "unique keyword, symbol, and string text fallback is supported"
       (is (= :integration ((kaocha-var 'resolve-suite-selector) available 'integration)))
       (is (= :integration ((kaocha-var 'resolve-suite-selector) available "integration"))))
     (testing "unknown selectors include available ids"
       (try
         ((kaocha-var 'resolve-suite-selector) available :missing)
         (is false "Expected unknown selector to throw")
         (catch clojure.lang.ExceptionInfo e
           (is (re-find #"Unknown Kaocha suite selector" (ex-message e)))
           (is (= :missing (:selector (ex-data e))))
           (is (= available (:available-suite-ids (ex-data e)))))))
     (testing "ambiguous text fallback includes matching ids"
       (try
         ((kaocha-var 'resolve-suite-selector) available 'unit)
         (is false "Expected ambiguous selector to throw")
         (catch clojure.lang.ExceptionInfo e
           (is (re-find #"Ambiguous Kaocha suite selector" (ex-message e)))
           (is (= 'unit (:selector (ex-data e))))
           (is (= [:alpha/unit :beta/unit] (:matching-suite-ids (ex-data e))))))))))

(deftest runtime-defaults-test
  ;; Scry adapter runtime defaults keep loaded/supplied config quiet while
  ;; preserving unrelated Kaocha settings.
  (when-kaocha-available
   (testing "runtime plugins are appended only once and quiet settings are forced"
     (let [cfg {:kaocha/plugins [:existing/plugin
                                 :kaocha.plugin/capture-output
                                 :kaocha.plugin/filter]
                :kaocha/reporter [:existing/reporter]
                :kaocha/color? true
                :custom/key :preserved}
           result ((kaocha-var 'apply-runtime-defaults) cfg)]
       (is (= [:existing/plugin :kaocha.plugin/capture-output :kaocha.plugin/filter]
              (:kaocha/plugins result)))
       (is (= [] (:kaocha/reporter result)))
       (is (false? (:kaocha/color? result)))
       (is (= :preserved (:custom/key result)))))
   (testing "capture-output and filter plugins are appended when absent"
     (is (= [:existing/plugin :kaocha.plugin/capture-output :kaocha.plugin/filter]
            (:kaocha/plugins ((kaocha-var 'apply-runtime-defaults)
                              {:kaocha/plugins [:existing/plugin]})))))))

(deftest full-config-selection-and-preservation-test
  ;; Supplied full config is authoritative: no fallback source/test/ns-pattern
  ;; options are merged, while suite selection and runtime defaults still apply.
  (when-kaocha-available
   (let [cfg (suite-config [:unit :integration])
         resolved ((kaocha-var 'apply-runtime-defaults)
                   ((kaocha-var 'select-suites) cfg [:integration]))]
     (is (= {:unit true :integration nil} (suite-skip-map resolved)))
     (is (= :preserved (:custom/key resolved)))
     (is (= [:kaocha.plugin/capture-output :kaocha.plugin/filter]
            (:kaocha/plugins resolved)))
     (is (= [] (:kaocha/reporter resolved)))
     (is (false? (:kaocha/color? resolved))))))

(deftest tests-edn-loading-and-suite-selection-test
  ;; tests.edn loading uses the current user.dir, runs configured suites by
  ;; default, and suite selection runs only the chosen configured suite.
  (when-kaocha-available
   (with-temp-project [project]
     (let [unit-ns (unique-ns "demo" "unit-test")
           integration-ns (unique-ns "demo" "integration-test")]
       (write-suite-test-ns project unit-ns true)
       (write-suite-test-ns project integration-ns false)
       (write-tests-edn
        project
        (str "#kaocha/v1\n"
             "{:tests [{:id :unit\n"
             "          :type :kaocha.type/clojure.test\n"
             "          :test-paths [\"test\"]\n"
             "          :ns-patterns [" (pr-str (exact-ns-pattern unit-ns)) "]}\n"
             "         {:id :integration\n"
             "          :type :kaocha.type/clojure.test\n"
             "          :test-paths [\"test\"]\n"
             "          :ns-patterns [" (pr-str (exact-ns-pattern integration-ns)) "]}]}"))
       (with-user-dir-and-ns-cleanup project [unit-ns integration-ns]
         (testing "unselected run uses configured tests.edn suites"
           (let [result (kaocha-run)]
             (is (false? (:pass? result)))
             (is (= 2 (get-in result [:summary :test])))
             (is (= 1 (get-in result [:summary :fail])))))
         (testing "plural suite selection runs only the requested suite"
           (let [result (kaocha-run {:suites [:unit]})]
             (is (true? (:pass? result)))
             (is (= 1 (get-in result [:summary :test])))
             (is (= 0 (get-in result [:summary :fail])))))
         (testing "single-suite convenience selection runs only the requested suite"
           (let [result (kaocha-run {:suite :integration})]
             (is (false? (:pass? result)))
             (is (= 1 (get-in result [:summary :test])))
             (is (= 1 (get-in result [:summary :fail]))))))))))

(deftest no-tests-edn-fallback-test
  ;; Projects without tests.edn still use the synthetic :unit fallback config
  ;; with caller-provided source/test/ns-pattern options.
  (when-kaocha-available
   (with-temp-project [project]
     (let [sample-ns (unique-ns "fallback" "sample-test")]
       (write-suite-test-ns project sample-ns true)
       (with-user-dir-and-ns-cleanup project [sample-ns]
         (let [result (kaocha-run {:test-paths ["test"]
                                   :ns-patterns [(exact-ns-pattern sample-ns)]})]
           (is (true? (:pass? result)))
           (is (= 1 (get-in result [:summary :test])))
           (is (= 1 (get-in result [:summary :pass])))))))))

(deftest no-tests-edn-fallback-focus-filters-execution-test
  ;; :kaocha-extra {:focus ...} actually filters executed vars on the synthetic
  ;; :unit fallback path (no tests.edn, no explicit :config, caller-provided
  ;; :test-paths/:ns-patterns). This locks in the ensure-runtime-plugins
  ;; behaviour that adds :kaocha.plugin/filter to the fallback config, which
  ;; would otherwise omit Kaocha's default plugin chain.
  (when-kaocha-available
   (with-temp-project [project]
     (let [sample-ns (unique-ns "fallback" "mixed-test")
           keep-var (keyword (str sample-ns) "keep-test")
           drop-var (keyword (str sample-ns) "drop-test")
           result-format {:suite {:top-level-keys [:summary :pass? :results :failures]}}
           executed (fn [result] (set (map :var (:results result))))
           run-opts {:test-paths ["test"]
                     :ns-patterns [(exact-ns-pattern sample-ns)]
                     :result-format result-format}]
       (write-mixed-test-ns project sample-ns)
       (with-user-dir-and-ns-cleanup project [sample-ns]
         (let [all-result (kaocha-run run-opts)
               focused-result (kaocha-run (assoc run-opts :kaocha-extra {:focus [keep-var]}))]
           (is (= #{(symbol keep-var) (symbol drop-var)} (executed all-result))
               "unfocused fallback run executes both fixture vars")
           (is (= 2 (get-in all-result [:summary :var-count])))
           (is (= #{(symbol keep-var)} (executed focused-result))
               "focused fallback run executes only the focused var")
           (is (= 1 (get-in focused-result [:summary :var-count])))))))))

(deftest result-formatting-test
  ;; Kaocha adapter results continue to use scry's scoped result model and
  ;; honor suite-scope result formatting options.
  (when-kaocha-available
   (testing "custom suite format can include failing entries and assertion detail"
     (let [cfg (suite-config [:unit] "scry\\.fixtures\\.failing")
           result (kaocha-run {:config cfg
                               :result-format
                               {:suite {:top-level-keys [:summary :pass? :results]
                                        :entry-keys [:var :status :assertions]
                                        :assertions? true}}})]
       (is (= #{:summary :pass? :results} (set (keys result))))
       (is (= 2 (get-in result [:summary :test])))
       (is (= 'scry.fixtures.failing/equality-fails
              (:var (first (:results result)))))
       (is (= [:fail]
              (mapv :type (:assertions (first (:results result))))))))))

(deftest kaocha-extra-focus-coercion-test
  ;; :focus raw values are coerced to a vector of keywords matching the Kaocha
  ;; filter plugin's --focus parse semantics, regardless of -m string / -X EDN
  ;; origin shape.
  (when-kaocha-available
   (let [coerce-focus (kaocha-var 'coerce-focus)]
     (is (= [:my.ns/test-foo] (coerce-focus "my.ns/test-foo")))
     (is (= [:my.ns/test-foo] (coerce-focus ":my.ns/test-foo")))
     (is (= [:my.ns/test-foo] (coerce-focus :my.ns/test-foo)))
     (is (= [:my.ns/test-foo] (coerce-focus 'my.ns/test-foo)))
     (is (= [:a :b :c] (coerce-focus ["a" :b 'c]))))))

(deftest kaocha-extra-merge-config-authoritative-test
  ;; :kaocha-extra merges into the resolved config's :kaocha/cli-options with the
  ;; resolved config authoritative on conflict; non-conflicting keys still apply.
  (when-kaocha-available
   (let [apply-kaocha-extra (kaocha-var 'apply-kaocha-extra)
         result (apply-kaocha-extra {:kaocha/cli-options {:focus [:keep/this]}}
                                    {:focus "override/ignored" :threads 4})]
     (is (= [:keep/this] (get-in result [:kaocha/cli-options :focus]))
         "explicit config :focus wins over pass-through")
     (is (= 4 (get-in result [:kaocha/cli-options :threads]))
         "non-conflicting pass-through key applies"))
   (testing "no :kaocha-extra leaves config unchanged"
     (let [apply-kaocha-extra (kaocha-var 'apply-kaocha-extra)
           cfg {:kaocha/cli-options {:focus [:a]}}]
       (is (= cfg (apply-kaocha-extra cfg nil)))
       (is (= cfg (apply-kaocha-extra cfg {})))))))

(deftest kaocha-extra-focus-filters-execution-test
  ;; :kaocha-extra {:focus ...} actually filters executed vars to the focused
  ;; var(s), not merely setting a config key. The filter plugin is ensured even
  ;; for configs that omit Kaocha's default plugin chain.
  (when-kaocha-available
   (let [cfg (suite-config [:unit] "scry\\.fixtures\\.mixed")
         result-format {:suite {:top-level-keys [:summary :pass? :results :failures]}}
         executed (fn [result] (set (map :var (:results result))))
         all-result (kaocha-run {:config cfg :result-format result-format})
         focused-result (kaocha-run {:config cfg
                                     :kaocha-extra {:focus [:scry.fixtures.mixed/pass-then-fail]}
                                     :result-format result-format})]
     (is (= #{'scry.fixtures.mixed/pass-then-fail
              'scry.fixtures.mixed/fail-then-error}
            (executed all-result))
         "unfocused run executes both fixture vars")
     (is (= 2 (get-in all-result [:summary :var-count])))
     (is (= #{'scry.fixtures.mixed/pass-then-fail} (executed focused-result))
         "focused run executes only the focused var")
     (is (= 1 (get-in focused-result [:summary :var-count]))))))

(deftest nested-kaocha-run-is-isolated-from-outer-scry-capture-test
  ;; The optional adapter disables any enclosing scry capture while Kaocha runs,
  ;; so intentional fixture failures belong only to the adapter result.
  (when-kaocha-available
   (let [outer-test-fn (fn []
                         (println "outer kaocha before")
                         (let [inner-result (invoke-kaocha-run-fixture)]
                           (reset! nested-kaocha-result inner-result)
                           (is (false? (:pass? inner-result)))
                           (is (= 'scry.fixtures.failing/equality-fails
                                  (:var (first (:results inner-result))))))
                         (println "outer kaocha after"))
         outer-var (with-meta outer-test-fn
                     {:test outer-test-fn
                      :name 'outer-invokes-kaocha
                      :ns (find-ns 'scry.kaocha-test)})]
     (reset! nested-kaocha-result nil)
     (let [outer-result (scry/run {:vars [outer-var]})
           outer-entry (first (:results outer-result))
           inner-result @nested-kaocha-result]
       (is (true? (:pass? outer-result)))
       (is (= 1 (get-in outer-result [:summary :test])))
       (is (= 2 (get-in outer-result [:summary :pass])))
       (is (= 0 (get-in outer-result [:summary :fail])))
       (is (= 'scry.kaocha-test/outer-invokes-kaocha (:var outer-entry)))
       (is (= "outer kaocha before\nouter kaocha after\n" (:out outer-entry)))
       (is (false? (:pass? inner-result)))
       (is (= 'scry.fixtures.failing/equality-fails
              (:var (first (:results inner-result)))))))))
