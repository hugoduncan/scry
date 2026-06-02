---
name: scry
description: >
  Use scry to run Clojure tests in-process and inspect structured test results.
  Trigger when debugging Clojure test failures, when an agent needs expected/actual
  assertion data, per-test output, or stack traces without scraping terminal output,
  or when working in a project that depends on scry.
lambda: "λclj_tests. run(in_process) → inspect({:summary :pass? :failures}) ∧ avoid(scrape_terminal_output)"
metadata:
  tags: ["clojure", "testing", "repl", "ai-agents", "structured-results"]
  language: "clojure"
allowed-tools: bash read psi-tool
---

# Scry

Use `scry` to run Clojure tests in-process and inspect structured test results instead of scraping human-oriented terminal output.

## When to use this skill

Use this skill when:

- You are in a Clojure project that has `scry` available on the classpath.
- You need to know whether tests passed and which test vars failed.
- You need failure details such as `:expected`, `:actual`, `:message`, `:file`, `:line`, testing contexts, or stack traces.
- You need stdout/stderr captured for failing test vars.
- You are debugging from a REPL or from an AI-agent shell command and want machine-readable results.

Do **not** scrape terminal output if `scry` can provide the structured result directly.

## Core command

Run the current project's test suite through `scry`:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"
```

Inspect the raw result map:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (clojure.pprint/pprint (scry/run))"
```

The top-level result shape is:

```clojure
{:summary   {:test 0
             :pass 0
             :fail 0
             :error 0
             :duration-ms 0.0
             :var-count 0
             :fail-var-count 0}
 :pass?     true
 :failures  []}
```

## REPL workflow

```clojure
(require '[scry.core :as scry])

(def result (scry/run))

(:pass? result)
(:summary result)
(scry/failures result)
(println (scry/report-string result))
```

`scry/run` stores the most recent result, so after a run you can inspect it interactively:

```clojure
(scry/last-result)
(scry/failures)
(scry/failed-test 'my.project-test/failing-test)
(scry/output 'my.project-test/failing-test)
```

## Targeted runs

Run discovered tests using defaults:

```clojure
(scry/run)
```

Run tests from explicit directories:

```clojure
(scry/run {:dirs ["test"]})
```

Run namespaces matching a pattern:

```clojure
(scry/run {:ns-pattern #".*-test$"})
```

Run explicit namespaces:

```clojure
(scry/run {:namespaces ['my.project-test]})
```

Run explicit vars:

```clojure
(scry/run {:vars [#'my.project-test/specific-test]})
```

## Interpreting failures

Each failure entry has this shape:

```clojure
{:var        'my.project-test/failing-test
 :ns         'my.project-test
 :status     :fail ;; or :error
 :assertions [{:type :fail
               :message "expected equality"
               :expected '(= 1 2)
               :actual '(not (= 1 2))
               :file "project_test.clj"
               :line 42
               :contexts ["outer testing" "inner testing"]}]
 :out        "captured stdout for this failed var"
 :err        "captured stderr for this failed var"}
```

Error assertions also include `:stacktrace`.

Debugging loop:

1. Run `(scry/run)` and check `:pass?`.
2. If false, inspect `(:failures result)` rather than rerunning with noisier output.
3. Use `:var`, `:file`, and `:line` to locate the failing test.
4. Use assertion `:expected`, `:actual`, `:contexts`, and `:stacktrace` to understand the failure.
5. Use `:out` and `:err` for per-failing-var diagnostic output.
6. Make the smallest fix and rerun the targeted namespace or var when possible.

## Kaocha adapter

If the project uses the optional Kaocha adapter, include the `:kaocha` alias:

```sh
clojure -M:test:kaocha -e "(require '[scry.kaocha :as k]) (clojure.pprint/pprint (k/run))"
```

The adapter returns the same top-level result shape as `scry.core/run`.

Caveat: Kaocha's capture-output plugin merges stdout and stderr into one stream. `scry.kaocha` puts the combined output in `:out` and leaves `:err` empty.

## Agent rules

- Prefer structured `scry` results over parsing console text.
- Preserve normal `clojure.test` semantics; `scry.clojure-test` delegates to `clojure.test/test-vars`.
- Use targeted `:namespaces` or `:vars` runs while iterating on a specific failure.
- After changing public behavior, update user-facing docs and tests.
