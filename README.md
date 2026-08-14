# scry

[![Clojars Project](https://img.shields.io/clojars/v/org.hugoduncan/scry.svg)](https://clojars.org/org.hugoduncan/scry)

`scry` is a Clojure test runner for AI agents and REPL-driven development. It returns structured, inspectable test results so callers can identify failures, assertions, stack traces, and captured output without scraping terminal text.

It supports two complementary workflows:

- **REPL / in-process API:** run tests in the current Clojure process, inspect the returned result map, and revisit the most recent result.
- **Command line:** run tests with process exit semantics and write detailed EDN artifacts for failed tests under `.scry-results/`.

## Why scry?

AI coding agents often need to determine:

- whether a test run passed;
- which test vars failed;
- what the expected and actual forms were;
- where a failure occurred; and
- what stdout or stderr a targeted test produced.

`scry` exposes those answers as Clojure data. Broad runs stay compact, while focused namespace and var runs include progressively more detail.

## Status

`scry` is an initial public alpha / pre-1.0 project. The core `clojure.test` runner, in-process API, CLI, scoped result model, and optional Kaocha adapter are usable and tested. APIs and result shapes may still change before a stable release.

The project leans heavily on AI-generated code and AI review processes.

## Installation

Add `scry` as a test or development dependency. A conventional `deps.edn` setup is:

```clojure
{:aliases
 {:test
  {:extra-paths ["test"]
   :extra-deps
   {org.hugoduncan/scry {:mvn/version "RELEASE"}}}}}
```

If your project already has a `:test` alias, merge the dependency into that alias rather than replacing its paths or options. Adjust `:extra-paths` if your tests live somewhere other than `test`.

The examples use Clojars' `"RELEASE"` token for convenience. For reproducible builds, replace it with the latest concrete version shown on [Clojars](https://clojars.org/org.hugoduncan/scry) and keep that version pinned.

## First structured test result

Start a REPL on your test alias:

```sh
clojure -M:test
```

Run one test namespace and retain the result:

```clojure
(require '[scry.core :as scry])

(def result
  (scry/run {:namespaces ['my.project-test]}))
```

Inspect the outcome and canonical result entries:

```clojure
(select-keys result [:pass? :summary])
(:results result)
(scry/failures result)
```

`scry` also retains the most recent result for follow-up inspection:

```clojure
(scry/last-result)
(println (scry/report-string))
```

A single explicit namespace includes every executed var and its assertion details. To capture stdout and stderr as well, run one explicit test var:

```clojure
(require '[my.project-test])

(def var-result
  (scry/run {:vars [#'my.project-test/specific-test]}))

(-> var-result :results first (select-keys [:out :err]))
```

For a failed or erroring var, `scry/output` retrieves the same captured streams by fully qualified symbol:

```clojure
(scry/output 'my.project-test/specific-test)
```

You now have a focused result that can be queried as Clojure data instead of parsed from terminal output.

## Common workflows

### Run `clojure.test` tests in the REPL

Use explicit namespaces or vars during development (after requiring the target namespace as shown above):

```clojure
(scry/run {:namespaces ['my.project-test]})
(scry/run {:vars [#'my.project-test/specific-test]})
```

Use a directory or namespace pattern for broader discovery:

```clojure
(scry/run)
(scry/run {:dirs ["test"]})
(scry/run {:ns-pattern #".*-test$"})
```

Normal `clojure.test` fixtures retain their standard grouping and ordering behavior. Nested in-process test runs are isolated from the enclosing `scry` result.

### Run tests from the command line

Run all discovered tests with either CLI entry point:

```sh
clojure -M:test -m scry.cli
clojure -X:test scry.cli/run
```

Target a namespace or test var:

```sh
clojure -M:test -m scry.cli --namespace my.project-test
clojure -M:test -m scry.cli --var my.project-test/specific-test

clojure -X:test scry.cli/run :namespaces '[my.project-test]'
clojure -X:test scry.cli/run :vars '[my.project-test/specific-test]'
```

The CLI prints live per-var progress and a summary. At the start of each run it clears and recreates `.scry-results/` in the current working directory. Failed and erroring vars produce namespace-prefixed EDN files such as:

```text
.scry-results/my.project-test__specific-test.edn
```

These files contain assertion details, error stack traces, and captured output. Passing runs can leave the directory empty.

The CLI exits `0` only when at least one concrete test var runs and all tests pass. Structured outcomes expose the authoritative `:scry.cli/outcome-kind`; the `-X` entry point returns the outcome map on success and throws `ex-info` with structured outcome data on non-zero results. Inspect the outcome and `.scry-results/*.edn` rather than parsing progress or diagnostic text.

See the [`scry.cli/run` reference](doc/API.md#scry.cli/run) for outcome kinds, result-file behavior, and error data. Run `clojure -M:test -m scry.cli --help` for the supported main-style options.

### Run Kaocha tests

Kaocha support is packaged separately so the core artifact does not depend on Kaocha. Add the adapter under a composable alias:

```clojure
{:aliases
 {:test
  {:extra-paths ["test"]
   :extra-deps
   {org.hugoduncan/scry {:mvn/version "RELEASE"}}}
  :kaocha
  {:extra-deps
   {org.hugoduncan/scry-kaocha {:mvn/version "RELEASE"}}}}}
```

Use the same version for `org.hugoduncan/scry` and `org.hugoduncan/scry-kaocha`. The adapter brings its own Kaocha dependency; projects can override that dependency through normal `deps.edn` resolution.

Compose the `:test` and `:kaocha` aliases, then run configured suites from the command line:

```sh
clojure -M:test:kaocha -m scry.cli --runner kaocha unit
clojure -M:test:kaocha -m scry.cli --runner kaocha unit integration
clojure -X:test:kaocha scry.cli/run :runner :kaocha :suite :unit
```

Main-style Kaocha mode forwards Kaocha options and positional suite selectors to Kaocha's CLI parser:

```sh
clojure -M:test:kaocha -m scry.cli --runner kaocha --focus my.ns/test-foo
clojure -M:test:kaocha -m scry.cli --runner kaocha --no-randomize unit
```

Run Kaocha in-process from a REPL with `scry.kaocha`:

```clojure
(require '[scry.kaocha :as kaocha])

(kaocha/run)                              ;; loads tests.edn when present
(kaocha/run {:suite :unit})
(kaocha/run {:suites [:unit :integration]})
(kaocha/run {:config full-kaocha-config})
```

Without an explicit `:config`, the adapter loads the current project's `tests.edn` when present. Otherwise it builds a synthetic `:unit` suite from source paths, test paths, and namespace patterns. Kaocha results use the same scoped result model; captured stdout and stderr are currently merged into `:out`.

See the [`scry.kaocha/run` reference](doc/API.md#scry.kaocha/run) for all adapter options and suite-selection rules. Use `clojure -M:test:kaocha -m scry.cli --runner kaocha --help` for Kaocha CLI options.

## Result model at a glance

By default, `scry/run` returns:

```clojure
{:summary  ...
 :pass?    ...
 :results  ...  ;; canonical formatted entries
 :failures ...} ;; compatibility failing/erroring subset
```

Default detail depends on how narrowly the run is targeted:

| Invocation | Default result detail |
| --- | --- |
| Discovery, multiple namespaces, or multiple vars | Compact failing/erroring entries |
| One explicit namespace | Every executed var and all assertion details |
| One explicit executable var | One var, all assertion details, and captured `:out` / `:err` |

Use `:result-format` to override the returned keys and inclusions for each scope. See the [`scry.core/run` reference](doc/API.md#scry.core/run) for the complete result shape, scope rules, and formatting options.

## Reference

- [`scry.core` API](doc/API.md#scry.core) — in-process runner and inspection helpers
- [`scry.cli/run` API](doc/API.md#scry.cli/run) — structured CLI outcomes and `-X` behavior
- [`scry.kaocha` API](doc/API.md#scry.kaocha) — optional Kaocha adapter
- `clojure -M:test -m scry.cli --help` — core CLI options
- `clojure -M:test:kaocha -m scry.cli --runner kaocha --help` — Kaocha CLI options

## License

`scry` is licensed under the Eclipse Public License 2.0 (`EPL-2.0`). See [`LICENSE`](LICENSE).

## Contributing

If you want a change, please open an issue rather than a pull request.
