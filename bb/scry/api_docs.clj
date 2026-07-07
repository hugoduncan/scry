(ns scry.api-docs
  "Generate the source-controlled quickdoc API reference."
  (:require
   [clojure.java.io :as io]
   [quickdoc.api :as quickdoc]))

(def output-path "doc/API.md")

(def quickdoc-version
  "io.github.borkdude/quickdoc v0.2.6 (git SHA ce86780).")

(def intro
  (str "# scry API reference\n\n"
       "This reference is generated from source docstrings with `borkdude/quickdoc` "
       "and source-controlled generation configuration. Do not hand-edit "
       "`doc/API.md`; regenerate it instead.\n\n"
       "`scry` is initial public alpha / pre-1.0. The documented APIs are "
       "usable and tested, but names and result shapes may still evolve before "
       "a future stable release.\n\n"
       "For installation, workflow-oriented examples, and command-line usage, "
       "start with [`README.md`](../README.md). This reference focuses on the "
       "public API vars and their docstrings.\n\n"
       "Regenerate the reference from the repository root with:\n\n"
       "```sh\n"
       "bb api-docs\n"
       "```\n\n"
       "Verify that the committed reference is up to date with:\n\n"
       "```sh\n"
       "bb api-docs --check\n"
       "```\n\n"
       "The optional `scry.kaocha` namespace is documented here because the "
       "generation command composes the optional Kaocha classpath. To use it in "
       "a project, include the optional `org.hugoduncan/scry-kaocha` adapter and "
       "run with aliases such as `clojure -M:test:kaocha ...`. Core `scry` usage "
       "does not require Kaocha.\n\n"
       "-----\n\n"))

(def quickdoc-options
  {:outfile false
   :github/repo "https://github.com/hugoduncan/scry"
   :git/branch "master"
   :source-paths ["src/scry/core.clj"
                  "src/scry/cli.clj"
                  "src-kaocha/scry/kaocha.clj"]
   :overrides {'scry.cli
               {'run {:meta {:arglists '([opts])}
                      :doc (str "`clojure -X` entry point for the scry CLI.\n\n"
                                "Normalizes EDN options, runs the shared CLI implementation, "
                                "and returns the successful structured outcome map. When the "
                                "CLI result is non-zero, throws `ex-info` with `:type "
                                ":scry.cli/non-zero`, `:exit-code`, `:scry.cli/outcome-kind`, "
                                "`:summary`, `:error`, and `:outcome` data so `clojure -X` exits "
                                "non-zero without calling `System/exit`.\n\n"
                                "When a run reaches normal classification, outcome data includes the "
                                "test `:summary`, `:result-files`, and `:scry.cli/outcome-kind`. "
                                "If post-run diagnostic/result-file writing fails, the test-derived "
                                "outcome is preserved, `:result-files` is empty, and bounded "
                                "diagnostic metadata is attached as top-level "
                                "`:scry.cli/diagnostic-error`.\n\n"
                                "The structured outcome's `:scry.cli/outcome-kind` is authoritative "
                                "for exit status; only `:scry.cli/pass` exits `0`. The outcome kinds "
                                "are:\n\n"
                                "- `:scry.cli/pass` — at least one test var ran and all passed.\n"
                                "- `:scry.cli/argument-error` — option parsing or normalization failed.\n"
                                "- `:scry.cli/runner-error` — runner infrastructure failed before producing results.\n"
                                "- `:scry.cli/load-error` — a suite-level load failure produced no concrete var.\n"
                                "- `:scry.cli/test-failure` — test vars failed or errored.\n"
                                "- `:scry.cli/unknown-result` — unknown-status entries with no higher-precedence signal.\n"
                                "- `:scry.cli/zero-tests` — no executable test vars were produced.\n\n"
                                "Typical invocations:\n\n"
                                "```sh\n"
                                "clojure -X:test scry.cli/run\n"
                                "clojure -X:test scry.cli/run :vars '[my.project-test/specific-test]'\n"
                                "clojure -X:test:kaocha scry.cli/run :runner :kaocha :suite :unit\n"
                                "```\n\n"
                                "Main-style CLI usage is run through project aliases, for example "
                                "`clojure -M:test -m scry.cli` and "
                                "`clojure -M:test:kaocha -m scry.cli --runner kaocha unit`.")}}}})

(defn generated-markdown
  "Return the deterministic generated API reference markdown."
  []
  (str intro (:markdown (quickdoc/quickdoc quickdoc-options))))

(defn write-doc!
  "Regenerate doc/API.md."
  []
  (let [file (io/file output-path)]
    (io/make-parents file)
    (spit file (generated-markdown))))

(defn check-doc!
  "Fail if doc/API.md is missing or not up to date."
  []
  (let [expected (generated-markdown)
        file (io/file output-path)
        actual (when (.exists file) (slurp file))]
    (when-not (= expected actual)
      (binding [*out* *err*]
        (println (str output-path " is not up to date. Run `bb api-docs` and commit the result.")))
      (System/exit 1))))

(defn -main
  "Generate API docs, or check them with --check."
  [& args]
  (case (vec args)
    [] (write-doc!)
    ["--check"] (check-doc!)
    (do
      (binding [*out* *err*]
        (println "Usage: bb api-docs [--check]"))
      (System/exit 2))))
