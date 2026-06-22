# scry

[![Clojars Project](https://img.shields.io/clojars/v/org.hugoduncan/scry.svg)](https://clojars.org/org.hugoduncan/scry)

`scry` is a Clojure test runner that produces inspectable, structured artifacts for each failed test instead of human-oriented terminal output that an agent has to scrape.

It solves an issue I observed, with AI constantly re-running tests, trying to narrow down relevant test information.

It supports two complementary use cases:

- **In the REPL / in-process API**, `scry` runs tests in the current Clojure process and returns an inspectable result map containing summaries, result entries, assertion details, stack traces, and captured output at a detail level matched to the invocation scope. The result stays available for follow-up inspection in the same process.
- **At the command line**, `scry` runs tests as a process and writes per-failed-test structured artifacts as EDN files under `.scry-results/`, alongside live progress, summary counts, and a meaningful exit code.

Both surfaces are designed for AI agents and REPL-driven development, where programmatic inspection of failures matters more than reading formatted console text.

## Status

Initial public alpha / pre-1.0. The documented core `clojure.test` runner/API, CLI, scoped result model, nested in-process capture isolation, build/release automation, and optional Kaocha adapter are usable and tested. APIs and result shapes should still be treated as pre-1.0 and may evolve before a future stable release.

## AI Disclaimer

The project leans heavily on AI-generated code and AI review processes.

## Why scry?

AI coding agents often need to answer questions like:

- Did the test run pass?
- Which test vars failed?
- What were the expected and actual forms?
- Where did the failure happen?
- What stdout/stderr was printed by a targeted test?

`scry` answers these directly from its structured results, without parsing console text.

## Installation

Add `scry` as a test/development dependency. A conventional `deps.edn` setup uses a `:test` alias with the project's test classpath and the core artifact:

```clojure
{:aliases
 {:test
  {:extra-paths ["test"]
   :extra-deps
   {org.hugoduncan/scry {:mvn/version "RELEASE"}}}}}
```

If your project already has a `:test` alias, merge the `:extra-deps` entry into it rather than replacing project-specific paths or options. Projects that use a different test source directory should adjust `:extra-paths` accordingly.

Then run the REPL/API or CLI on the same alias:

```sh
clojure -M:test -m scry.cli
clojure -X:test scry.cli/run
```

Copyable snippets use the Clojars `"RELEASE"` token for convenience. Published concrete versions are generated from the Git commit count, such as `0.1.N` / `0.1.<git-count>`. For reproducible builds, replace `"RELEASE"` with the latest concrete published version from Clojars and keep that version pinned.

### Optional Kaocha adapter

Kaocha support is packaged separately so core `scry` users do not take a hard Kaocha dependency. To use `scry.kaocha` or Kaocha CLI mode, add the optional adapter artifact under a composable alias:

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

Use the same version token/value for `org.hugoduncan/scry` and `org.hugoduncan/scry-kaocha`. The adapter artifact depends on the same-version `scry` core artifact and on Kaocha, so users do not need to declare Kaocha separately just to use `scry.kaocha`. Projects that already manage Kaocha can still override or add their preferred Kaocha dependency through normal `deps.edn` resolution.

Run Kaocha support by composing the aliases:

```sh
clojure -M:test:kaocha -m scry.cli --runner kaocha unit
clojure -X:test:kaocha scry.cli/run :runner :kaocha :suite :unit
```

## Usage

See [`doc/API.md`](doc/API.md) for the generated public API reference. `scry` runs `clojure.test` tests from a REPL through `scry.core/run` and from the command line through `scry.cli`.

### REPL usage

`scry.core/run` runs tests in the current process and returns an inspectable result map. The most recent result is retained for follow-up inspection in the same process.

```clojure
(require '[scry.core :as scry])

(scry/run)
```

Inspect the most recent result after a run:

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

### Command-line usage

`scry.cli` runs tests as a process for shell and CI use. It prints live per-var progress, writes structured failure EDN under `.scry-results/`, and returns a process-oriented exit code.

Run with either `-m` or `-X`:

```sh
clojure -M:test -m scry.cli
clojure -X:test scry.cli/run
```

Core `clojure.test` selectors are available from both entry points:

```sh
clojure -M:test -m scry.cli --dir test --ns-pattern '.*-test$'
clojure -M:test -m scry.cli --namespace my.project-test
clojure -M:test -m scry.cli --var my.project-test/specific-test

clojure -X:test scry.cli/run :dirs '["test"]' :namespaces '[my.project-test]'
clojure -X:test scry.cli/run :vars '[my.project-test/specific-test]'
```

While tests run, the CLI prints one progress item per result: `.` to stdout for passing vars and the unqualified test name to stderr for failing, erroring, or unknown vars. After the run it prints a stdout summary with passed, failed, and errored assertion and result-entry counts.

At the start of every CLI run, `.scry-results/` in the current working directory is cleared and recreated. Failed and erroring vars write detailed namespace-prefixed EDN files such as `.scry-results/my.project-test__specific-test.edn`, including assertion details, stack traces for errors, and captured output. Passing runs may leave `.scry-results/` as an empty directory.

On a failing run the CLI also writes a short stderr diagnostic pointing at the results directory. For a load/suite error (a namespace that fails to compile or require), it additionally prints the failing entry's message and its root-cause class/message, so the failure is visible inline without opening the EDN file. This is supplementary human output; `:scry.cli/outcome-kind` and `.scry-results/*.edn` remain the authoritative signals.

The CLI exits `0` only when at least one concrete test var runs and all vars pass; every other case exits non-zero. Structured outcomes carry a machine-readable `:scry.cli/outcome-kind` that is authoritative for exit status. The `-X` entry point returns the outcome map on success and throws `ex-info` with structured data on non-zero outcomes. Inspect `:scry.cli/outcome-kind` and `.scry-results/*.edn` rather than parsing human stderr. See the [`scry.cli/run` API reference](doc/API.md#scry.cli/run) for the full set of outcome kinds and the thrown `ex-info` data.

Kaocha CLI mode is available when the optional adapter is on the classpath:

```sh
clojure -M:test:kaocha -m scry.cli --runner kaocha unit
clojure -M:test:kaocha -m scry.cli --runner kaocha unit integration
clojure -X:test:kaocha scry.cli/run :runner :kaocha :suite :unit
clojure -M:test:kaocha -m scry.cli --runner kaocha --focus my.ns/test-foo
clojure -X:test:kaocha scry.cli/run :runner :kaocha :focus '"my.ns/test-foo"'
```

Kaocha CLI mode accepts Kaocha suite/config options and fallback `:source-paths`, `:test-paths`, and `:ns-patterns` options. `--dir` / `:dirs` maps to fallback `:test-paths` when no explicit Kaocha `:config` is supplied. Core-only namespace, var, and `:ns-pattern` selectors are rejected in Kaocha mode. As with the adapter API, Kaocha-captured stdout/stderr are preserved as merged `:out` with empty `:err` in result files unless the adapter supplies separate streams.

Kaocha-specific options can be passed through to the underlying Kaocha runner:

- On `-m`, the named `--focus SYM` flag (repeatable) forwards Kaocha's focus selector, and the generic `--kaocha-opt KEY VALUE` flag forwards any other raw Kaocha cli-option. Unknown bare `-m` flags are still rejected as argument errors, and these Kaocha-only flags are rejected in core (`--runner clojure-test`) mode.
- On `-X`, any top-level key outside scry's own option set (for example `:focus "my.ns/test-foo"`) is forwarded as pass-through. Because `-X` has no unknown-key rejection, a mistyped `-X` key is forwarded and surfaces as a Kaocha runner/load error rather than an argument error.

Forwarded options are merged into the resolved Kaocha config's `:kaocha/cli-options`, with an explicit `:config` authoritative on conflict. `:focus` values are coerced to the keyword shape Kaocha's filter plugin expects, so `--focus my.ns/test-foo` (`-m`) and `:focus "my.ns/test-foo"` (`-X`) both run only the focused test.

Use `clojure -M:test -m scry.cli --help` for supported main-style flags.

## `clojure.test` runner

`scry/run` runs `clojure.test` tests and supports these selectors:

```clojure
(scry/run)
(scry/run {:dirs ["test"]})
(scry/run {:ns-pattern #".*-test$"})
(scry/run {:namespaces ['my.project-test]})
(scry/run {:vars [#'my.project-test/specific-test]})
```

It preserves normal `clojure.test` behavior such as `:once` and `:each` fixtures, including standard fixture grouping and ordering semantics.

Nested in-process test runs (including `scry.kaocha/run` and raw `clojure.test` calls) are isolated from an enclosing `scry` run, so inner vars, assertions, and output do not pollute the outer result.

## Result shape

`scry/run` returns a map whose top level defaults to:

- `:summary` — pass/fail/error and var counts plus duration. Kaocha runs also include `:seed` (the randomize seed) so a failing order can be reproduced.
- `:pass?` — overall boolean.
- `:results` — the canonical formatted result entries.
- `:failures` — a compatibility subset of the failing/erroring entries.

Entry detail scales with invocation scope: broad/suite runs return compact failing-only entries, a single namespace returns every executed var with assertion details, and a single var also captures `:out`/`:err`. The `:result-format` option overrides the returned keys and inclusions per scope.

See the [`scry.core/run` API reference](doc/API.md#scry.core/run) for the full result map, entry shapes, scope rules, and `:result-format` options.

## Kaocha adapter

A Kaocha adapter lives in `scry.kaocha` and is only available when the Kaocha adapter path and dependencies are on the classpath. Repository development uses the `:kaocha` alias; library consumers can opt in with the separate `org.hugoduncan/scry-kaocha` artifact at the same version as `org.hugoduncan/scry`, keeping core users free of a hard Kaocha dependency:

```clojure
(require '[scry.kaocha :as k])

(k/run)                              ;; loads tests.edn when present
(k/run {:suites [:unit :integration]})
(k/run {:suite :unit})               ;; single-suite convenience
(k/run {:config full-kaocha-config}) ;; full config override
```

When `:config` is omitted, the adapter loads the current project's `tests.edn` if it exists, or otherwise builds a synthetic `:unit` suite from `:source-paths`, `:test-paths`, and `:ns-patterns`. Use `:suite` for a single selector or `:suites` for a non-empty collection. The adapter transforms Kaocha's result tree into the same scoped result model, defaulting to suite scope. Kaocha merges stdout and stderr, so combined output is placed in `:out` with an empty `:err`.

See the [`scry.kaocha/run` API reference](doc/API.md#scry.kaocha/run) for the supported options and selector-matching rules.

## License

`scry` is licensed under the Eclipse Public License 2.0 (`EPL-2.0`). See [`LICENSE`](LICENSE).

## Contributor and agent guidance

If you want a change, please open an issue rather than a PR.
