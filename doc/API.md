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

The structured outcome's `:scry.cli/outcome-kind` is authoritative for exit status; only `:scry.cli/pass` exits `0`. The outcome kinds are:

- `:scry.cli/pass` — at least one test var ran and all passed.
- `:scry.cli/argument-error` — option parsing or normalization failed.
- `:scry.cli/runner-error` — runner infrastructure failed before producing results.
- `:scry.cli/load-error` — a suite-level load failure produced no concrete var.
- `:scry.cli/test-failure` — test vars failed or errored.
- `:scry.cli/unknown-result` — unknown-status entries with no higher-precedence signal.
- `:scry.cli/zero-tests` — no executable test vars were produced.

Typical invocations:

```sh
clojure -X:test scry.cli/run
clojure -X:test scry.cli/run :vars '[my.project-test/specific-test]'
clojure -X:test:kaocha scry.cli/run :runner :kaocha :suite :unit
```

Main-style CLI usage is run through project aliases, for example `clojure -M:test -m scry.cli` and `clojure -M:test:kaocha -m scry.cli --runner kaocha unit`.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/cli.clj#L785-L792">Source</a></sub></p>

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
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L123-L129">Source</a></sub></p>

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
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L111-L121">Source</a></sub></p>

## <a name="scry.core/last-result">`last-result`</a>
``` clojure
(last-result)
```
Function.

Return the most recent run result, or nil if nothing has run.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L102-L105">Source</a></sub></p>

## <a name="scry.core/output">`output`</a>
``` clojure
(output var-sym)
(output result var-sym)
```
Function.

Return {:out s :err s} captured for failed test var `var-sym`, when present.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L131-L136">Source</a></sub></p>

## <a name="scry.core/report-string">`report-string`</a>
``` clojure
(report-string)
(report-string result)
```
Function.

Render a human/agent-readable report of `result` (defaults to last run).
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L162-L170">Source</a></sub></p>

## <a name="scry.core/run">`run`</a>
``` clojure
(run)
(run opts)
```
Function.

Run clojure.test tests in-process and return the inspectable result map.

   Options:
     :dirs          test directories to scan (default ["test"])
     :namespaces    explicit collection of namespace symbols to run
     :ns-pattern    regex matching namespace names to run
     :vars          explicit collection of test var refs to run
     :result-format per-scope formatting overrides (see below)

   The result is also available through [`last-result`](#scry.core/last-result).

   Result shape

   By default a result has this top-level shape:

       {:summary {:test 0 :pass 0 :fail 0 :error 0 :duration-ms 0.0
                  :var-count 0 :fail-var-count 0}
        :pass? true
        :results []
        :failures []}

   `:results` is the canonical formatted collection. `:failures` is a
   compatibility collection holding the failing/erroring subset when the
   selected format includes it.

   Scoped detail

   Default entry detail depends on how the run was invoked:

   - Suite or multi scope (`(run)`, multiple namespaces, or multiple vars):
     compact entries for failing/erroring vars only, each with
     `:assertion-summary`; no per-assertion details or output.
   - Single namespace scope (`{:namespaces ['my.ns-test]}`): an entry for every
     executed var, including passing vars, with all assertion details; no
     stdout/stderr keys.
   - Single var scope (`{:vars [#'my.ns-test/a-test]}`): one entry with all
     assertion details and captured `:out`/`:err`.

   A detailed entry looks like:

       {:var 'my.ns-test/a-test
        :ns 'my.ns-test
        :status :pass ;; :pass, :fail, :error, or rarely :unknown
        :assertions [{:type :pass :message nil
                      :expected '(= 2 (+ 1 1)) :actual '(= 2 (+ 1 1))
                      :file "ns_test.clj" :line 42
                      :contexts ["outer" "inner"]}]
        :out "captured stdout"
        :err "captured stderr"}

   Error assertions also include `:stacktrace`. A compact suite-scope entry
   looks like:

       {:var 'my.ns-test/a-test :ns 'my.ns-test :status :fail
        :assertion-summary {:pass 0 :fail 1 :error 0}}

   Custom result formatting

   `:result-format` overrides returned keys and inclusions per scope. Scopes are
   `:suite`, `:namespace`, and `:var`; each supports:

     :top-level-keys  top-level keys to return
     :entry-keys      keys to project for each result entry
     :assertions?     authoritative assertion gate; true adds `:assertions`,
                      false removes it
     :output?         authoritative output gate; true adds `:out`/`:err`,
                      false removes them

   For example:

       (run {:namespaces ['my.ns-test]
             :result-format {:namespace {:top-level-keys [:summary :pass? :results]
                                         :entry-keys [:var :status]
                                         :assertions? true
                                         :output? false}}})

   If custom `:top-level-keys` omits both `:results` and `:failures`, helpers
   such as [`failures`](#scry.core/failures) return empty/nil values because there is no collection to
   inspect.
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src/scry/core.clj#L18-L100">Source</a></sub></p>

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
     :kaocha-argv        a vector of raw `-m` CLI strings forwarded verbatim by
                         the scry CLI in Kaocha mode (every token that is not a
                         scry-owned flag: unknown `--flags`, their values, and
                         positional suite names). They are parsed with Kaocha's
                         own CLI machinery (its `tools.cli` spec plus active
                         plugins' option hooks); parsed cli-options are merged
                         like `:kaocha-extra` (resolved `:config` authoritative
                         on conflict) and positional selectors are routed through
                         the same `:suite`/`:suites` resolution. Malformed Kaocha
                         options surface as a runner/load error rather than an
                         argument error. This option is `-m`-only; the `-X` map
                         path uses `:kaocha-extra`.
     :kaocha-extra       a map of raw Kaocha cli-options forwarded by the scry
                         CLI's bounded pass-through (e.g. `:focus`). It is merged
                         into the resolved config's :kaocha/cli-options with the
                         resolved :config authoritative on conflict. Known values
                         are coerced (`:focus` raw string/symbol/keyword scalar or
                         collection becomes a vector of keywords); unknown keys are
                         forwarded as-is, so a mistyped key surfaces as a runner or
                         load error rather than an argument error.

   When :config is omitted, the current project's tests.edn is loaded if it
   exists; otherwise a synthetic :unit suite is built from :source-paths,
   :test-paths, and :ns-patterns.

   Suite selectors match configured suite ids by exact value first, then by
   unique text (`"string"` ids/selectors as-is, keywords and symbols by
   `name`). Use :suite for a single selector; :suites must be a non-empty
   collection. Unknown or ambiguous selectors, and supplying both :suite and
   :suites, throw `ex-info`.

   The adapter defaults to suite scope because its public options do not mirror
   the namespace/var selectors of [`scry.core/run`](#scry.core/run). Kaocha's capture-output
   plugin merges stdout and stderr, so combined output is placed in :out and
   :err is empty.

   When Kaocha randomizes test order (its default), the randomize seed is
   surfaced as `:seed` in the result `:summary` so a failing order can be
   reproduced; the framework's own stray "Randomized with --seed N" stdout
   print is suppressed.

   Returns the same scoped result model as [`scry.core/run`](#scry.core/run).
<p><sub><a href="https://github.com/hugoduncan/scry/blob/master/src-kaocha/scry/kaocha.clj#L372-L453">Source</a></sub></p>
