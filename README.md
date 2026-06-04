# scry

`scry` is an in-process Clojure test runner designed for AI agents and REPL-driven development.

Instead of asking an agent to scrape terminal output, `scry` runs tests in the current Clojure process and returns an inspectable result map containing summaries, result entries, assertion details, stack traces, and captured output at a detail level matched to the invocation scope.

## Status

Early scaffold, but the core `clojure.test` runner and Kaocha adapter are implemented and tested.

## Why scry?

AI coding agents often need to answer questions like:

- Did the test run pass?
- Which test vars failed?
- What were the expected and actual forms?
- Where did the failure happen?
- What stdout/stderr was printed by a targeted test?

Traditional command-line test output is optimized for humans. `scry` is optimized for programmatic inspection.

## Usage

`scry` is intended to be driven primarily from a REPL so results remain available for follow-up inspection in the same process.

Basic REPL usage:

```clojure
(require '[scry.core :as scry])

(scry/run)
```

`scry.core/run` stores the most recent result in `scry.core/last-run`, which can be inspected after a run:

```clojure
(scry/last-result)
(:pass? (scry/last-result))
(:summary (scry/last-result))
(:results (scry/last-result))
(scry/failures)
(scry/failed-test 'my.project-test/failing-test)
(scry/output 'my.project-test/failing-test)
(println (scry/report-string (scry/last-result)))
```

## `clojure.test` runner

The default runner is implemented in `scry.clojure-test` and supports:

```clojure
(scry/run)
(scry/run {:dirs ["test"]})
(scry/run {:ns-pattern #".*-test$"})
(scry/run {:namespaces ['my.project-test]})
(scry/run {:vars [#'my.project-test/specific-test]})
```

It delegates actual execution to `clojure.test/test-vars`, so normal `clojure.test` behavior such as `:once` and `:each` fixtures is preserved.

## Scoped result shape

A result has this top-level shape by default:

```clojure
{:summary {:test 0
           :pass 0
           :fail 0
           :error 0
           :duration-ms 0.0
           :var-count 0
           :fail-var-count 0}
 :pass? true
 :results []
 :failures []}
```

`:results` is the canonical formatted collection. `:failures` is retained as a compatibility collection containing the failing/erroring subset when included by the selected format.

The default detail level depends on how the run was invoked:

- Suite or multi scope (`(scry/run)`, multiple namespaces, or multiple vars): compact entries for failing/erroring vars only, with `:assertion-summary`; no per-assertion details or output.
- Single namespace scope (`{:namespaces ['my.project-test]}`): entries for every executed var, including passing vars, with all assertion details; no stdout/stderr keys.
- Single var scope (`{:vars [#'my.project-test/specific-test]}`): one entry with all assertion details and captured `:out`/`:err`.

Detailed entries look like:

```clojure
{:var 'my.project-test/specific-test
 :ns 'my.project-test
 :status :pass ;; :pass, :fail, :error, or rarely :unknown
 :assertions [{:type :pass
               :message nil
               :expected '(= 2 (+ 1 1))
               :actual '(= 2 (+ 1 1))
               :file "project_test.clj"
               :line 42
               :contexts ["outer testing" "inner testing"]}]
 :out "captured stdout"   ;; single var scope by default
 :err "captured stderr"}
```

Error assertions also include `:stacktrace`.

Suite-scope compact entries look like:

```clojure
{:var 'my.project-test/failing-test
 :ns 'my.project-test
 :status :fail
 :assertion-summary {:pass 0 :fail 1 :error 0}}
```

## Custom result formatting

Returned keys and inclusions can be configured per invocation scope with `:result-format`:

```clojure
(scry/run
 {:namespaces ['my.project-test]
  :result-format
  {:namespace {:top-level-keys [:summary :pass? :results]
               :entry-keys [:var :status]
               :assertions? true
               :output? false}}})
```

Scopes are `:suite`, `:namespace`, and `:var`. Supported per-scope options are:

- `:top-level-keys` — top-level keys to return.
- `:entry-keys` — keys to project for each result entry.
- `:assertions?` — authoritative assertion gate; `true` adds `:assertions`, `false` removes it.
- `:output?` — authoritative output gate; `true` adds `:out`/`:err`, `false` removes them.

If custom `:top-level-keys` omits both `:results` and `:failures`, helpers such as `scry/failures` return empty/nil values because there is no collection to inspect.

## Kaocha adapter

A Kaocha adapter lives in `scry.kaocha` and is only available when the Kaocha adapter path and dependencies are on the classpath. Repository development uses the `:kaocha` alias; library consumers can opt in with the separate `org.hugoduncan/scry-kaocha` artifact at the same version as `org.hugoduncan/scry`, keeping core users free of a hard Kaocha dependency:

```clojure
(require '[scry.kaocha :as k])

(k/run)                              ;; loads tests.edn when present
(k/run {:suites [:unit :integration]})
(k/run {:suite :unit})               ;; single-suite convenience
(k/run {:config full-kaocha-config}) ;; full config override
```

When `:config` is omitted, the adapter loads the current project's `tests.edn` if it exists. Projects without `tests.edn` keep the synthetic fallback `:unit` suite using `:source-paths`, `:test-paths`, and `:ns-patterns` options.

Suite selectors match configured suite ids by exact value first, then by unique text (`"string"` ids/selectors as-is, keywords and symbols by `name`). Unknown or ambiguous selectors throw `ex-info`. Use `:suite` for one selector; plural `:suites` must be a non-empty collection. Supplying both `:suite` and `:suites` is an API error.

The adapter preserves supplied full `:config` maps without merging fallback paths into them, then applies suite selection and quiet scry runtime defaults: capture-output plugin enabled, reporter `[]`, and color disabled. It transforms Kaocha's result tree into the same scoped result model and defaults to suite scope because its public options do not mirror `scry.clojure-test` namespace/var selectors.

Note: Kaocha's capture-output plugin merges stdout and stderr into one captured stream. `scry.kaocha` places that combined output in `:out` and leaves `:err` empty.

## Contributor and agent guidance

Development workflow, repository conventions, test commands, and architectural constraints live in [`AGENTS.md`](AGENTS.md), not in this README.
