# Command-line progress output and failure result files

## Goal

Add command-line-oriented behavior for running `scry` tests while preserving the current REPL/API behavior of `scry.core/run`.

When invoked from the command line, `scry` should:

- Clear `.scry-results/` at the start of each run.
- Print `.` to stdout live for each passing test var.
- Print the unqualified name of each failing/erroring test var live to stderr.
- Write detailed EDN data for each failed/erroring test var under `.scry-results/`.
- After all attempted tests finish, print a stdout summary count of passed/failed/errored assertions and tests.
- Exit `0` only when all selected/executed test vars pass; exit non-zero for any failed, errored, unknown, or not-executed/no-test run state.

## Context

Current behavior:

- `scry.core/run` is a REPL/API entry point that returns an inspectable result map and stores it in `scry.core/last-run`.
- The current command-line examples invoke Clojure expressions such as:

  ```sh
  clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"
  ```

- There is not yet a dedicated command-line runner namespace or `-main` / `-X` entry point.
- Result detail already exists in structured form. Suite-scope broad runs default to compact failing/erroring entries, while namespace/var scopes can include full assertions/output depending on result format.
- Existing `scry.core/run` behavior is optimized for AI/REPL inspection and should not be changed merely because a shell command calls it from `-e`.
- The optional Kaocha adapter is now packaged separately as `org.hugoduncan/scry-kaocha`; command-line support should include a Kaocha mode when that adapter is on the classpath.

## Resolved CLI decisions

The initial open questions have been resolved as follows:

1. **Invocation forms:** Support both `clj`/`clojure -M -m ...` and `clj`/`clojure -X ...` invocation through one common implementation.
2. **Selector support:** Support all selectors currently supported by each chosen underlying runner. Core `clojure.test` mode supports directories, namespace selectors, var selectors, and namespace pattern. Kaocha mode supports Kaocha suite/config selectors and Kaocha fallback config keys; it does not imply support for core-only namespace/var selectors unless those are explicitly mapped below.
3. **Progress timing:** Progress must be live while tests are running, so a long run shows activity before completion.
4. **Definition of “test”:** A command-line progress item is one test var, not one assertion.
5. **Mixed-status vars:** In-run progress is per test var status. Passing vars print only `.`, failed vars print only the test name, and errored vars print only the test name. A var with both pass and fail/error assertions does not also print `.`.
6. **Fail/error precedence:** If a test var has both failures and errors, count it as errored. This matches the current canonical status precedence where `:error` outranks `:fail`.
7. **Result file extension:** Use `.edn`; the earlier double-dot path was not intentional.
8. **File-name collisions:** Result files should always include the namespace prefix so same unqualified test names do not collide. Terminal failure/error progress remains unqualified.
9. **Directory lifecycle:** `.scry-results/` may exist as an empty directory after a passing run. It must be cleared at the start of every CLI run.
10. **EDN detail level:** Result EDN files should include all available details, including full assertions and captured stdout/stderr. In Kaocha mode, this follows the existing adapter behavior: combined captured output is recorded in `:out` and `:err` is empty unless a future adapter change separates the streams.
11. **Kaocha CLI:** Preferably support a parallel Kaocha CLI mode in this task. If the Kaocha adapter is not on the classpath, requesting Kaocha mode should fail clearly.
12. **Terminal streams:** Dots and final summary go to stdout. Failing/erroring test names go to stderr.

## Intended command-line shape

Add a dedicated CLI entry point, likely a new namespace such as `scry.cli`, that supports both main-style and exec-style invocation through a shared implementation.

Examples to pin during planning:

```sh
clj -M:test -m scry.cli
clj -X:test scry.cli/run
```

The exact aliases and docs should be pinned in `plan.md` and documented in README/AGENTS. The `-X` entry function should live in the same CLI namespace as `-main` and both invocation forms must call the same implementation path after argument parsing.

The CLI should run tests using the existing `scry` capture/runner machinery, but format terminal output differently from `scry.core/report-string`.

## Command-line option model

Both command-line entry points normalize into one data map before running. The shared implementation receives already-normalized options shaped as:

```clojure
{:runner :clojure-test          ;; or :kaocha
 :dirs ["test" ...]                 ;; core mode: test directories; Kaocha mode: maps to :test-paths
 :namespaces ['my.project-test ...]    ;; core mode only
 :vars [#'my.project-test/specific-test ...] ;; core mode only
 :ns-pattern #".*-test$"              ;; core mode only; Kaocha mode uses :ns-patterns in :config/fallback opts
 :result-format {...}
 :suite :unit
 :suites [:unit :integration]
 :config {...}}
```

For `-X`, callers pass EDN data directly. Accepted keys are the same normalized option names, with these coercions:

- `:runner` accepts `:clojure-test`, `:core`, `:test`, `:kaocha`, or equivalent strings/symbols; it normalizes to `:clojure-test` or `:kaocha` and defaults to `:clojure-test`.
- `:dirs` accepts a string or sequential collection of strings and normalizes to a vector of strings. In core mode it is passed as the core runner's test-directory selector. In Kaocha mode it is mapped to Kaocha fallback `:test-paths` only when no explicit `:config` supplies suites; it is not a namespace/var selector.
- `:namespaces` accepts a symbol/string or sequential collection of symbols/strings and normalizes to namespace symbols. It is valid only for core mode.
- `:vars` accepts a var, fully qualified symbol/string like `my.project-test/specific-test`, or a sequential collection of those. Symbols/strings are required and resolved to executable Vars before the runner is called. Unqualified var names are invalid because they are ambiguous. It is valid only for core mode.
- `:ns-pattern` accepts a regex pattern or string; strings compile with `re-pattern`. `:namespace-pattern` and `:namespace-regex` are aliases that normalize to `:ns-pattern`. It is valid only for core mode; Kaocha namespace pattern selection must be expressed through Kaocha `:config` or fallback `:ns-patterns`.
- `:result-format` is accepted as EDN and passed through after validation that it is a map when present. The CLI itself should request detailed canonical failure data for writing `.scry-results/` even if the terminal summary is compact.
- Kaocha-only keys `:suite`, `:suites`, and `:config` are accepted only for `:runner :kaocha`; `:suites` must be a non-empty sequential collection, matching `scry.kaocha/run` semantics.

For `-m`, callers pass string flags that normalize to the same map. The supported string model is intentionally small and explicit:

```sh
clj -M:test -m scry.cli \
  --runner clojure-test \
  --dir test --dir integration-test \
  --namespace my.project-test --namespace other.project-test \
  --var my.project-test/specific-test \
  --ns-pattern '.*-test$'

clj -M:test:kaocha -m scry.cli --runner kaocha --suite unit
clj -M:test:kaocha -m scry.cli --runner kaocha --suite unit --suite integration
```

`-m` flags map as follows:

- `--runner` / `-r`: `clojure-test` (default), `core`, `test`, or `kaocha`.
- `--dir` / `-d`: repeatable test directory.
- `--namespace` / `--ns` / `-n`: repeatable namespace symbol.
- `--var` / `-v`: repeatable fully qualified var symbol.
- `--ns-pattern` / `--namespace-pattern` / `--namespace-regex`: one regex string.
- `--result-format`: one EDN map string, parsed with `clojure.edn/read-string`.
- Kaocha mode only: repeatable `--suite` / `-s`; one occurrence normalizes to `:suite`, multiple occurrences normalize to `:suites`; `--suites` accepts one EDN non-empty collection string; `--config` accepts one EDN config map string. Supplying `--suite` together with `--suites` is invalid. `--dir` may be used in Kaocha mode only as fallback `:test-paths`; `--namespace`, `--var`, and `--ns-pattern` are core-only and are invalid with `--runner kaocha`.
- `--help` may print usage and exit successfully without running tests.

Unknown flags, missing flag values, invalid EDN, invalid regexes, unqualified vars, unresolved/non-test vars, conflicting aliases, and options for the wrong runner are argument errors.

## Selector and runner behavior

Selector support is runner-specific, not global.

Core `clojure.test` mode exposes the currently supported core runner selectors:

- test directories through `:dirs` / `--dir`,
- namespace regex/pattern through `:ns-pattern` / `--ns-pattern`,
- explicit namespaces through `:namespaces` / `--namespace`,
- explicit vars through `:vars` / `--var`.

The parsed core-mode options map to existing runner options rather than inventing a separate selection model. Explicit vars take precedence as in `scry.clojure-test/run`; namespace selectors are used for resolving vars only when no executable explicit vars are supplied, preserving existing behavior.

Kaocha CLI mode is in scope and mandatory for this task when the optional adapter is on the classpath. Requesting `:runner :kaocha` loads `scry.kaocha` dynamically so the core jar does not acquire a hard Kaocha dependency. If `scry.kaocha` or Kaocha is unavailable, the CLI reports a clear argument/runner error and exits non-zero.

Kaocha mode does not support core-only explicit namespace or var selectors in this task. Supplying `:namespaces`, `:vars`, or core `:ns-pattern` aliases with `:runner :kaocha` is an argument error. Kaocha namespace filtering may still be supplied through a full Kaocha `:config` map or through fallback keys that `scry.kaocha/run` already understands, such as `:ns-patterns`.

Kaocha CLI mode accepts `:suite`, `:suites`, and `:config` from `-X` EDN data and `--suite`, `--suites`, and `--config` from `-m` flags as described above. Other Kaocha fallback config keys already supported by `scry.kaocha/run`, such as `:source-paths`, `:test-paths`, and `:ns-patterns`, may also be accepted by `-X` and passed through. `:dirs` / `--dir` is the only shared selector with a Kaocha mapping: in Kaocha mode it normalizes to fallback `:test-paths` when no explicit `:config` supplies suites. It does not imply core-style namespace discovery. If both Kaocha `:test-paths` and CLI `:dirs` are supplied, that conflict is an argument error unless the implementation chooses one documented precedence during planning.

Kaocha result-file output follows the current `scry.kaocha` adapter capture semantics. The adapter merges captured stdout and stderr into each result entry's `:out` and leaves `:err` empty. CLI EDN files in Kaocha mode should preserve those adapter-provided fields rather than attempting to re-separate streams. The requirement to include stdout/stderr detail is therefore satisfied for Kaocha by including `:out` with merged output plus the empty `:err` field.

## Required live progress behavior

For each executed test var, in execution order:

- If the test var status is `:pass`, print `.` to stdout.
- If the test var status is `:fail` or `:error`, print the unqualified var name to stderr, for example `equality-fails` rather than `scry.fixtures.failing/equality-fails`.
- Do not print assertion-level progress.
- Do not print a dot for a var that ultimately fails or errors, even if it has some passing assertions.
- Flush output so long-running command-line runs visibly make progress.
- Attempt all selected/discovered tests even after failures/errors.

## Required summary behavior

After all tests have been attempted, print a human-readable stdout summary containing at least:

- passed assertion count,
- failed assertion count,
- errored assertion count,
- passed test-var count,
- failed test-var count,
- errored test-var count.

A test var with both fail and error events counts as errored, not failed. The exact wording can be chosen during planning, but it should be stable enough for focused tests.

## Process-exit and shared implementation contract

The shared CLI boundary should be pure/testable up to process termination. Design target:

```clojure
(scry.cli/run-cli normalized-options io-boundary)
;; => {:exit-code 0-or-nonzero
;;     :result scry-result-or-nil
;;     :summary cli-summary
;;     :result-files [paths...]
;;     :error nil-or-data}
```

`run-cli` performs the actual test run and filesystem/output effects through an injectable or bindable IO/process boundary where practical, but it does not call `System/exit`. It returns the exit code and data needed by tests. Both entry points use this common path after parsing/normalization:

- `-m` entry point (`-main`) parses string args, calls the shared implementation, prints/report effects during the run, and then calls `System/exit` with the returned exit code. `--help` may print usage and exit `0` without invoking the runner.
- `-X` entry point accepts an EDN option map, normalizes it, calls the shared implementation, and returns the result map when the exit code is `0`. For non-zero outcomes it should throw `ex-info` carrying at least `:exit-code`, `:summary`, and any error data, rather than calling `System/exit`; this matches `clojure -X` expectations and still causes the process to exit non-zero.
- Argument/normalization errors are represented as non-zero CLI outcomes before any tests run; `-m` exits non-zero after printing a terse diagnostic to stderr, while `-X` throws `ex-info` with structured data.
- Runner exceptions are caught by the shared implementation, reported tersely, and converted to non-zero outcomes with the exception data preserved in the returned/thrown map.

The process must exit `0` only when all selected/executed test vars pass. Any failed, errored, unknown-status, invalid-selection, runner exception, or no-tests-executed state must exit non-zero. In particular, a command-line invocation that discovers or selects zero executable tests must not be reported as success.

## Required result-file behavior

At the start of each CLI run:

- Delete or empty `.scry-results/` in the current working directory.
- Recreate `.scry-results/` so it may remain as an empty directory after a passing run.
- Ensure stale files from previous runs cannot remain.

For each failed/erroring test var:

- Write detailed EDN data to `.scry-results/`.
- Include enough data for an agent/human to inspect the failure without scraping terminal output: var symbol, namespace, status, assertion summary, full assertions including expected/actual/message/file/line/testing contexts, stack traces for errors, and captured `:out`/`:err`.
- Prefer EDN that can be read by `clojure.edn/read-string`.
- Use deterministic namespace-prefixed file names with `.edn` extension, for example `.scry-results/scry.fixtures.failing__equality-fails.edn` or another documented namespace-prefixed scheme.
- Terminal failure/error progress still prints only the unqualified test var name.

## Scope

In scope:

- Add a command-line runner namespace/entry point.
- Add a shared implementation for `-m` and `-X` invocation.
- Add CLI-oriented live progress output.
- Ensure CLI runs produce detailed failure/error EDN files under `.scry-results/`.
- Clear/recreate `.scry-results/` at the start of CLI runs.
- Support core runner selectors and a Kaocha mode where the optional adapter is present.
- Add CLI tests using temporary working directories where practical so real project `.scry-results/` is not mutated by focused tests.
- Add docs for the CLI command and result directory.
- Update `CHANGELOG.md` for the new command-line behavior.
- Update `AGENTS.md` if maintainer/agent CLI usage guidance changes.
- Update README because this is user-facing behavior.
- Add `.scry-results/` to `.gitignore` if not already ignored.

Out of scope:

- Changing the default return shape of `scry.core/run`.
- Replacing existing REPL-first guidance.
- Adding a standalone executable or uberjar.
- Changing release workflow behavior except any metadata/docs needed to expose the new CLI namespace in jars.
- Implementing a full general-purpose command-line parser beyond the supported scry options.

## Design considerations

- Keep API and CLI behavior separate. `scry.core/run` should remain structured and quiet unless explicitly asked for CLI behavior.
- Live per-test-var progress likely requires a progress callback/event hook in the existing capture layer or runner loop, rather than rendering only after the final result is built.
- Failure EDN should be generated from canonical/unprojected result entries so broad suite runs still write detailed failure data even though `:results` would normally be compact.
- Namespace-prefixed result files avoid collisions while preserving unqualified terminal progress for readability.
- CLI tests should verify filesystem clearing and EDN contents without relying on the real repository state.
- `-X` invocation receives EDN data, while `-m` receives strings; both should normalize into the same option map before running.
- Kaocha support may require a small optional bridge to avoid making the core artifact require `scry.kaocha` at compile/load time.

## Acceptance criteria

- Documented `-m` and `-X` command-line entry points exist.
- Both command-line entry points use a common implementation after argument parsing.
- Running the CLI attempts all selected/discovered tests.
- The CLI supports all existing core runner selectors.
- The CLI supports Kaocha mode when the optional adapter is on the classpath, or records a clear implementation decision if a review narrows this scope.
- The CLI prints `.` live to stdout for each passing test var.
- The CLI prints unqualified names live to stderr for failing/erroring test vars.
- The CLI prints progress in deterministic execution order.
- The CLI prints a final stdout summary with assertion pass/fail/error counts and test pass/fail/error counts.
- A test var containing both failures and errors counts as errored.
- The CLI exits with code `0` only when all selected/executed test vars pass.
- The CLI exits non-zero when any test fails, errors, has unknown status, when selection/discovery yields no executable tests, or when runner/argument errors occur.
- `.scry-results/` is cleared and recreated at the start of each CLI run.
- `.scry-results/` may exist as an empty directory after a passing run.
- Failed/erroring tests produce namespace-prefixed `.edn` files under `.scry-results/`.
- Result EDN files contain detailed, readable failure/error data including assertions, stack traces when present, and stdout/stderr; in Kaocha mode they preserve the adapter's merged-output `:out` and empty `:err` semantics.
- Stale `.scry-results/` files do not survive a new run.
- Same unqualified test names from different namespaces cannot overwrite each other.
- Existing `scry.core/run` REPL/API behavior remains unchanged.
- Focused tests cover CLI output streams, live/progress callback behavior where practical, exit behavior or return status, result-file writing, directory clearing, namespace-prefixed naming, and `-m`/`-X` normalization.
- README, AGENTS.md, and CHANGELOG.md are updated as appropriate.
