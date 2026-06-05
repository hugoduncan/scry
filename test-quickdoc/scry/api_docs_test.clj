(ns scry.api-docs-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [scry.api-docs :as api-docs]))

(defn assert-includes
  [text fragments]
  (doseq [fragment fragments]
    (is (str/includes? (or text "") fragment)
        (str "expected API docs to include " (pr-str fragment)))))

(defn assert-omits
  [text fragments]
  (doseq [fragment fragments]
    (is (not (str/includes? (or text "") fragment))
        (str "expected API docs to omit " (pr-str fragment)))))

(defn var-anchor
  [namespace-name var-name]
  (str "## <a name=\"" namespace-name "/" var-name "\">`" var-name "`</a>"))

(defn namespace-section
  [markdown namespace-name]
  (let [anchor (str "# <a name=\"" namespace-name "\">" namespace-name "</a>")
        start (str/index-of markdown anchor)]
    (is start (str "expected API docs to contain namespace section " anchor))
    (when start
      (let [next-namespace (str/index-of markdown "\n-----\n# <a name=\"" (+ start (count anchor)))
            end (or next-namespace (count markdown))]
        (subs markdown start end)))))

(defn var-anchors-in-namespace
  [markdown namespace-name]
  (->> (str/split-lines (or (namespace-section markdown namespace-name) ""))
       (filter #(str/starts-with? % (str "## <a name=\"" namespace-name "/")))
       set))

(defn var-section
  [markdown namespace-name var-name]
  (let [anchor (var-anchor namespace-name var-name)
        start (str/index-of markdown anchor)]
    (is start (str "expected API docs to contain section " anchor))
    (when start
      (let [next-var (str/index-of markdown "\n## <a name=\"" (+ start (count anchor)))
            next-namespace (str/index-of markdown "\n-----" (+ start (count anchor)))
            end (or next-var next-namespace (count markdown))]
        (subs markdown start end)))))

(deftest generated-api-docs-curated-surface-test
  ;; API docs are generated and reproducible, but they also need a focused
  ;; content contract so generator/source changes cannot accidentally publish
  ;; internal namespaces, helper vars, or implementation-only arities.
  (let [markdown (api-docs/generated-markdown)
        committed-markdown (slurp api-docs/output-path)
        cli-run-section (var-section markdown "scry.cli" "run")]
    (testing "committed API docs match the source-controlled generator output"
      (is (= markdown committed-markdown)))

    (testing "generated docs include the required generated intro prose"
      (assert-includes markdown
                       ["`scry` is initial public alpha / pre-1.0"
                        "For installation, workflow-oriented examples, and command-line usage, start with [`README.md`](../README.md)."
                        "Regenerate the reference from the repository root with:"
                        "```sh\nbb api-docs\n```"
                        "Verify that the committed reference is up to date with:"
                        "```sh\nbb api-docs --check\n```"])
      (assert-includes markdown
                       ["The optional `scry.kaocha` namespace is documented here because the generation command composes the optional Kaocha classpath."
                        "The optional Kaocha adapter lives\n   in [`scry.kaocha`](#scry.kaocha) and is available when the adapter artifact or equivalent\n   optional Kaocha classpath is present."]))

    (testing "generated docs include exactly the curated scry.core public surface"
      (assert-includes markdown
                       ["# <a name=\"scry.core\">scry.core</a>"
                        (var-anchor "scry.core" "run")
                        (var-anchor "scry.core" "last-result")
                        (var-anchor "scry.core" "failures")
                        (var-anchor "scry.core" "failed-test")
                        (var-anchor "scry.core" "output")
                        (var-anchor "scry.core" "report-string")
                        (var-anchor "scry.core" "last-run")])
      (is (= #{(var-anchor "scry.core" "run")
               (var-anchor "scry.core" "last-result")
               (var-anchor "scry.core" "failures")
               (var-anchor "scry.core" "failed-test")
               (var-anchor "scry.core" "output")
               (var-anchor "scry.core" "report-string")
               (var-anchor "scry.core" "last-run")}
             (var-anchors-in-namespace markdown "scry.core"))))

    (testing "generated docs include exactly the user-facing scry.cli/run API"
      (assert-includes markdown
                       ["# <a name=\"scry.cli\">scry.cli</a>"
                        (var-anchor "scry.cli" "run")])
      (is (= #{(var-anchor "scry.cli" "run")}
             (var-anchors-in-namespace markdown "scry.cli")))
      (assert-includes cli-run-section
                       ["``` clojure\n(run opts)\n```"
                        "clojure -X:test scry.cli/run"
                        "clojure -M:test -m scry.cli"
                        ":type :scry.cli/non-zero"
                        ":summary"
                        ":error"
                        ":outcome"])
      (assert-omits cli-run-section
                    ["io-boundary"
                     "(run opts io-boundary)"]))

    (testing "generated docs include exactly the optional scry.kaocha public surface"
      (assert-includes markdown
                       ["# <a name=\"scry.kaocha\">scry.kaocha</a>"
                        (var-anchor "scry.kaocha" "run")
                        (var-anchor "scry.kaocha" "result->scry")
                        "optional `scry.kaocha` namespace"
                        "clojure -M:test:kaocha"])
      (is (= #{(var-anchor "scry.kaocha" "run")
               (var-anchor "scry.kaocha" "result->scry")}
             (var-anchors-in-namespace markdown "scry.kaocha"))))

    (testing "generated docs omit implementation namespaces and CLI helper vars"
      (assert-omits markdown
                    ["# <a name=\"scry.capture\">scry.capture</a>"
                     "# <a name=\"scry.clojure-test\">scry.clojure-test</a>"
                     "# <a name=\"scry.cli.results\">scry.cli.results</a>"
                     "io-boundary"
                     (var-anchor "scry.cli" "run-cli")
                     (var-anchor "scry.cli" "main-outcome")
                     (var-anchor "scry.cli" "parse-main-args")
                     (var-anchor "scry.cli" "normalize-exec-opts")
                     (var-anchor "scry.cli" "normalize-runner")
                     (var-anchor "scry.cli" "usage")
                     (var-anchor "scry.cli" "-main")]))))
