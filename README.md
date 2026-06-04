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

From this repository, include the test path when running tests:

```sh
clojure -M:test
```

Run tests through `scry` from a REPL or one-off expression:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (prn (scry/run))"
```

Basic REPL usage:

```clojure
(require '[scry.core :as scry])

(def result (scry/run))

(:pass? result)
;;=> true or false

(:summary result)
;;=> {:test 10, :pass 39, :fail 0, :error 0,
;;    :duration-ms 12.34, :var-count 10, :fail-var-count 0}

(:results result)
;;=> canonical formatted result entries for this invocation scope

(scry/failures result)
;;=> filtered failing/erroring result entries

(println (scry/report-string result))
```

`scry.core/run` also stores the most recent result in `scry.core/last-run`, which can be inspected after a run:

```clojure
(scry/last-result)
(scry/failures)
(scry/failed-test 'my.project-test/failing-test)
(scry/output 'my.project-test/failing-test)
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

A Kaocha adapter lives in `scry.kaocha` and is only available when the `:kaocha` alias is on the classpath:

```sh
clojure -M:test:kaocha -e "(require '[scry.kaocha :as k]) (prn (k/run))"
```

The adapter transforms Kaocha's result tree into the same scoped result model. It currently defaults to suite scope because its public options do not mirror `scry.clojure-test` namespace/var selectors.

Note: Kaocha's capture-output plugin merges stdout and stderr into one captured stream. `scry.kaocha` places that combined output in `:out` and leaves `:err` empty.

## Development

Run the project test suite through `scry`:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"
```

Run with raw result output:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (clojure.pprint/pprint (scry/run))"
```

Start an nREPL with CIDER middleware:

```sh
clojure -M:nrepl
```

## Project layout

```text
src/scry/core.clj          Public API
src/scry/capture.clj       clojure.test report and output capture
src/scry/clojure_test.clj  In-process clojure.test runner
src-kaocha/scry/kaocha.clj Optional Kaocha adapter
test/scry/                 Tests and fixtures
```

## Design constraints

- Keep `scry.core` free of a hard dependency on Kaocha.
- Preserve normal `clojure.test` semantics where possible.
- Return structured data suitable for agents and REPL users.
- Retain detailed failure/error information, not just pass/fail status.
- Avoid huge default payloads for broad runs.
