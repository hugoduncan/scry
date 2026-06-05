# scry API reference

This reference is generated from source docstrings with `borkdude/quickdoc` and source-controlled generation configuration. Do not hand-edit `doc/API.md`; regenerate it instead.

`scry` is initial public alpha / pre-1.0. The documented APIs are usable and tested, but names and result shapes may still evolve before a future stable release.

For installation, workflow-oriented examples, and command-line usage, start with [`README.md`](../README.md). This reference focuses on the public API vars and their docstrings.

Regenerate the reference from the repository root with:

```sh
bb api-docs
```

Verify that the committed reference is up to date with:

```sh
bb api-docs --check
```

The optional `scry.kaocha` namespace is documented here because the generation command composes the optional Kaocha classpath. To use it in a project, include the optional `org.hugoduncan/scry-kaocha` adapter and run with aliases such as `clojure -M:test:kaocha ...`. Core `scry` usage does not require Kaocha.

-----

# Table of contents
-  [`scry.cli`](#scry.cli)  - Command-line and <code>clojure -X</code> entry points for scry.
    -  [`run`](#scry.cli/run) - <code>clojure -X</code> entry point for the scry CLI.
-  [`scry.core`](#scry.core)  - Public entry point for scry, an in-process test runner for AI agents.
    -  [`failed-test`](#scry.core/failed-test) - Return the failure/error entry for fully-qualified test var symbol <code>var-sym</code>.
    -  [`failures`](#scry.core/failures) - Return failure/error entries of <code>result</code> (defaults to the last run).
    -  [`last-result`](#scry.core/last-result) - Return the most recent run result, or nil if nothing has run.
    -  [`output`](#scry.core/output) - Return {:out s :err s} captured for failed test var <code>var-sym</code>, when present.
    -  [`report-string`](#scry.core/report-string) - Render a human/agent-readable report of <code>result</code> (defaults to last run).
    -  [`run`](#scry.core/run) - Run clojure.test tests in-process and return the inspectable result map.
-  [`scry.kaocha`](#scry.kaocha)  - In-process kaocha runner producing scry's inspectable result map.
    -  [`run`](#scry.kaocha/run) - Run kaocha tests in-process and return scry's inspectable result map.

-----
# <a name="scry.cli">scry.cli</a>


Command-line and `clojure -X` entry points for scry.




## <a name="scry.cli/run">`run`</a>
``` clojure
(run opts)
```
Function.

`clojure -X` entry point for the scry CLI.

Normalizes EDN options, runs the shared CLI implementation, and returns the successful structured outcome map. When the CLI result is non-zero, throws `ex-info` with `:type :scry.cli/non-zero`, `:exit-code`, `:scry.cli/outcome-kind`, `:summary`, `:error`, and `:outcome` data so `clojure -X` exits non-zero without calling `System/exit`.

Typical invocations:

```sh
clojure -X:test scry.cli/run
clojure -X:test scry.cli/run :vars '[my.project-test/specific-test]'
clojure -X:test:kaocha scry.cli/run :runner :kaocha :suite :unit
```

Main-style CLI usage is run through project aliases, for example `clojure -M:test -m scry.cli` and `clojure -M:test:kaocha -m scry.cli --runner kaocha --suite unit`.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/cli.clj#L604-L611">Source</a></sub></p>

-----
# <a name="scry.core">scry.core</a>


Public entry point for scry, an in-process test runner for AI agents.

   [`run`](#scry.core/run) executes clojure.test tests in-process and returns an inspectable
   result map; the most recent result is retained for interactive inspection
   through [`last-result`](#scry.core/last-result). The optional Kaocha adapter lives in [`scry.kaocha`](#scry.kaocha)
   and is available when the adapter artifact or equivalent optional Kaocha
   classpath is present.




## <a name="scry.core/failed-test">`failed-test`</a>
``` clojure
(failed-test var-sym)
(failed-test result var-sym)
```
Function.

Return the failure/error entry for fully-qualified test var symbol `var-sym`.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L49-L55">Source</a></sub></p>

## <a name="scry.core/failures">`failures`</a>
``` clojure
(failures)
(failures result)
```
Function.

Return failure/error entries of `result` (defaults to the last run).

   Prefers the compatibility :failures collection when present and otherwise
   filters canonical :results. Returns an empty vector when the selected result
   format omits both collections.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L37-L47">Source</a></sub></p>

## <a name="scry.core/last-result">`last-result`</a>
``` clojure
(last-result)
```
Function.

Return the most recent run result, or nil if nothing has run.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L28-L31">Source</a></sub></p>

## <a name="scry.core/output">`output`</a>
``` clojure
(output var-sym)
(output result var-sym)
```
Function.

Return {:out s :err s} captured for failed test var `var-sym`, when present.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L57-L62">Source</a></sub></p>

## <a name="scry.core/report-string">`report-string`</a>
``` clojure
(report-string)
(report-string result)
```
Function.

Render a human/agent-readable report of `result` (defaults to last run).
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L88-L96">Source</a></sub></p>

## <a name="scry.core/run">`run`</a>
``` clojure
(run)
(run opts)
```
Function.

Run clojure.test tests in-process and return the inspectable result map.

   Supports directory, namespace, namespace-pattern, var, and result-format
   options documented in the README. The result is also available through
   [`last-result`](#scry.core/last-result).
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L18-L26">Source</a></sub></p>

-----
# <a name="scry.kaocha">scry.kaocha</a>


In-process kaocha runner producing scry's inspectable result map.

   Kaocha already captures per-test clojure.test events and (via its
   capture-output plugin) per-test output. This adapter runs kaocha
   programmatically and transforms its result tree into scry's result model.

   Note: kaocha merges stdout and stderr into a single captured stream, so for
   kaocha results the combined output is placed in :out and :err is empty.




## <a name="scry.kaocha/run">`run`</a>
``` clojure
(run)
(run opts)
```
Function.

Run kaocha tests in-process and return scry's inspectable result map.

   Options:
     :config             a fully-formed kaocha config map (overrides loading tests.edn)
     :suite              a single suite selector
     :suites             a collection of suite selectors
     :source-paths       fallback source dirs when no :config or tests.edn exists
     :test-paths         fallback test dirs when no :config or tests.edn exists
     :ns-patterns        fallback namespace-name regex strings
     :result-format      suite-scope formatting overrides
     :progress-callback  optional function called after each completed test var

   Returns the same scoped result model as [`scry.core/run`](#scry.core/run).
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src-kaocha/scry/kaocha.clj#L270-L298">Source</a></sub></p>
