# Implementation notes

No implementation yet. The task was created from the maintainer request to add command-line-specific progress output and `.scry-results/` EDN failure files, with explicit open questions recorded in `design.md` before implementation.

## Open-question resolution - 2026-06-03

Resolved the initial CLI ambiguities from maintainer feedback and updated `design.md` accordingly:

- Support both `clj`/`clojure -m` and `clj`/`clojure -X` invocation through a common implementation.
- Support all existing selectors, including core runner selectors and preferably a Kaocha mode when the adapter is on the classpath.
- Progress must be live while test vars run.
- A command-line progress item is a test var, not an assertion.
- Mixed-status vars print only the failing/erroring test name, not a dot.
- Vars with both failures and errors count as errored.
- Failure result files use `.edn`, not `..edn`.
- Result file names should be namespace-prefixed to avoid same-name collisions while terminal failure output remains unqualified.
- `.scry-results/` may exist empty after a passing run.
- Failure EDN includes all details, including stdout/stderr.
- Dots and summary go to stdout; failing/erroring test names go to stderr.

## Exit-code clarification - 2026-06-03

Clarified that CLI exit status must be `0` only when all selected/executed test vars pass. Any failed, errored, unknown-status, invalid-selection, runner exception, or no-tests-executed state must exit non-zero; in particular a run discovering/selecting zero executable tests is not command-line success.

## Architecture review - 2026-06-03

No new actionable architectural-fit feedback. The design keeps command-line behavior in a dedicated CLI path, preserves quiet structured `scry.core/run` REPL/API behavior, uses existing capture/runner result machinery for detailed data, and respects the optional Kaocha boundary by requiring an optional bridge rather than a hard core dependency. Consulted `AGENTS.md`; `META.md` and `doc/architecture.md` are absent.

## Ambiguity review - 2026-06-03

Found actionable ambiguity feedback: pin the concrete `-m`/`-X` option input model for selectors (especially vars and regexes), decide whether Kaocha CLI mode is mandatory or explicitly deferred/narrowed, and specify how `-m` and `-X` apply process exit/non-zero behavior while keeping a testable shared implementation.

## Design ambiguity follow-up - 2026-06-03

Completed the review-added `design-steps.md` items by updating `design.md` with:

- A concrete shared normalized option map for `-m` and `-X`.
- `-X` EDN coercions for runner, dirs, namespaces, vars, namespace regex aliases, result format, and Kaocha-only options.
- `-m` string flags for runner, directories, namespaces, vars, namespace regex, result-format EDN, and Kaocha suite/config flags.
- A mandatory-in-scope Kaocha CLI mode when the optional adapter is on the classpath, loaded dynamically to preserve the core/adapter dependency boundary.
- A process-exit contract where shared `run-cli` returns structured outcome data without calling `System/exit`, `-main` exits with the returned code, and the `-X` entry throws `ex-info` on non-zero outcomes.

All newly added design follow-up items from the preceding ambiguity review are now checked.

## Design inconsistency review - 2026-06-03

Found actionable inconsistency feedback: the design's selector language implies Kaocha mode may support the same directory/namespace/var selector set as the core runner, but the referenced `scry.kaocha` API only supports suites/config/fallback paths and later design text narrows Kaocha `-m` flags to suite/config; clarify the per-runner selector contract and any `:dirs`/`:test-paths` mapping. Also clarify Kaocha result-file output semantics because the design asks for stdout/stderr detail while the referenced adapter merges both streams into `:out` with empty `:err`.

## Design inconsistency follow-up - 2026-06-03

Completed the review-added `design-steps.md` items by updating `design.md` with:

- Runner-specific selector rules: core mode supports `:dirs`, `:namespaces`, `:vars`, and `:ns-pattern`; Kaocha mode supports suite/config/fallback Kaocha options and rejects core-only namespace/var/ns-pattern selectors.
- A narrow Kaocha mapping for `:dirs` / `--dir`: it becomes fallback `:test-paths` only when no explicit Kaocha config supplies suites, and it does not imply core-style namespace discovery.
- A conflict note for supplying both Kaocha `:test-paths` and CLI `:dirs` so planning/implementation must document or reject precedence.
- Kaocha result-file semantics: CLI EDN files preserve the current adapter behavior where captured stdout/stderr are merged into `:out` and `:err` is empty, rather than introducing a new separation mechanism.

All newly added design follow-up items from the preceding inconsistency review are now checked.

## Plan ambiguity review - 2026-06-03

Found actionable ambiguity feedback: the plan does not pin the focused CLI test namespace/aliases/commands for core versus optional Kaocha CLI checks; it says CLI should request detailed entries but does not decide whether `:canonical-results` is retained, exposed by helper, or reconciled with user `:result-format`; it leaves the exact `.scry-results/*.edn` namespace-prefixed filename scheme to implementation; and it says Kaocha progress may be post-run despite design/acceptance requiring live progress, so the expected Kaocha behavior needs an explicit plan-level decision.

## Plan ambiguity follow-up - 2026-06-03

Completed the review-added plan ambiguity items by updating `plan.md` with:

- Focused core CLI test namespace and exact command: `test/scry/cli_test.clj` via `clojure -M:test -e ...`.
- Focused optional Kaocha CLI test namespace and exact command: `test/scry/cli_kaocha_test.clj` via `clojure -M:test:kaocha -e ...`.
- A detailed-result mechanism: CLI runs force `:canonical-results` retention in `:top-level-keys` while preserving user-supplied `:result-format` projection for `:results`; CLI summaries/result files use `:canonical-results` and treat its absence as a runner error.
- The deterministic namespace-prefixed result-file naming scheme: encoded namespace, `__`, encoded var name, `.edn`, preserving common filename characters and hex-escaping other code points.
- A plan-level decision that Kaocha CLI progress must be live per var when the optional adapter is available, implemented through an adapter progress callback/reporter hook rather than post-run rendering.

All newly added plan ambiguity follow-up items in `steps.md` are now checked.

## Plan inconsistency review - 2026-06-03

Found actionable inconsistency feedback: the plan's Approach and Slice 4 checklist still allow/document "best available" or post-run Kaocha progress if live callbacks are difficult, while the design, acceptance criteria, and later plan ambiguity follow-up make live per-var Kaocha progress mandatory and a blocking issue if unavailable. Reconcile the earlier plan text and Slice 4 step so implementation cannot satisfy the task with deterministic post-run Kaocha progress.

## Plan inconsistency follow-up - 2026-06-03

Completed the review-added plan inconsistency item by reconciling `plan.md` and `steps.md` around Kaocha CLI progress:

- Rewrote the earlier plan language that allowed deterministic post-run/"best available" Kaocha progress.
- Updated the Slice 4 checklist item so Kaocha progress is consistently mandatory live per test var through an adapter progress callback/reporter hook when the optional adapter is available.
- Preserved the explicit blocking stance: if live Kaocha progress cannot be implemented without compromising the optional adapter boundary, record it as a blocker rather than silently using post-run progress.

No code changes were made; this follow-up only corrected task artifacts before implementation.

## Slice 1 core progress hook - 2026-06-03

Implemented the smallest core progress hook without changing default API behavior:

- Added `scry.capture/current-var-result` so an end-of-var callback can see the canonical entry before `:current` is cleared.
- Added optional `:progress-callback` support to `scry.clojure-test/run`; it is invoked once on each `:end-test-var` with the canonical per-var entry after final status is known.
- The callback receives `:var`, `:ns`, `:status`, `:assertion-summary`, assertions, and captured output, which is enough for future CLI progress and result files.
- Added `scry.fixtures.mixed` plus focused `progress-callback-test` coverage for execution order and fail/error precedence (`:error` outranks `:fail`).

Verification:

```sh
clojure -M:test -e "(require '[scry.clojure-test-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.clojure-test-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run)] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"
```

Both passed. The remaining Slice 1 item is the CLI-specific detailed result-format helper; it is deferred until `scry.cli` exists in the next slice because current default scoped result shapes should remain unchanged.

## Slice 2 option normalization and parser - 2026-06-03

Implemented the initial `scry.cli` namespace with parser/normalization support, keeping execution and filesystem effects for the next slice:

- Added shared usage text and structured argument errors tagged with `:type :scry.cli/argument-error`.
- Added `normalize-exec-opts` for `clojure -X` option maps, including runner aliases, directory normalization, namespace symbols, regex aliases, result-format validation, and CLI-enforced `:canonical-results` retention for every scope.
- Added fully-qualified test var resolution that requires namespaces and rejects unqualified, unresolved, non-Var, and non-test values before runner execution.
- Added runner-specific validation: core mode rejects Kaocha-only/fallback keys; Kaocha mode rejects core-only namespace/var/ns-pattern selectors, rejects `:suite` plus `:suites`, and maps `:dirs` to fallback `:test-paths` while rejecting documented `:dirs` conflicts.
- Added `parse-main-args` for the explicit `-m` flag model, including repeatable flags, EDN parsing for `--result-format`, `--suites`, and `--config`, and `--help` handling.
- Added focused `scry.cli-test` parser/normalization coverage for accepted core and Kaocha forms plus conflicts, missing values, invalid EDN/regexes, invalid vars, and help.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 5 tests, 40 assertions, 0 failures, 0 errors.

## Slice 3 core CLI runner/effects - 2026-06-03

Implemented the shared `run-cli` path for core `clojure.test` mode:

- Added `run-cli` outcome maps with `:exit-code`, `:result`, `:summary`, `:result-files`, and `:error` keys.
- Added an injectable IO boundary for stdout/stderr writers, cwd, and core runner dispatch. Filesystem effects use the boundary cwd and real local filesystem so tests can assert state without mocks.
- Added `.scry-results/` lifecycle: delete recursively at run start, recreate, and leave empty after passing/no-result-file runs.
- Added deterministic namespace-prefixed EDN filenames using the planned `namespace__var.edn` scheme with portable segment encoding.
- Added core live progress printing through the Slice 1 progress callback: passing vars write `.` to stdout; failing/erroring/unknown vars write the unqualified var name to stderr; both streams flush per item.
- Added CLI summary computation/text for assertion pass/fail/error counts and test-var pass/fail/error/unknown counts.
- Added exit-code classification: success only when at least one var executed and no failed/error/unknown vars occurred.
- Added runner exception handling that returns non-zero, prints a terse stderr diagnostic, preserves exception data, and still clears stale result files.
- Added focused temporary-directory tests for stale file removal, empty passing result dirs, EDN file content, progress streams, summary text, same-name collision avoidance, no-test non-success, and runner exceptions. Added two same-name fixture namespaces for collision coverage.

Kaocha mode is intentionally still a later slice; requesting `:runner :kaocha` now returns a structured non-zero runner error through `run-cli` rather than loading the optional adapter.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run)] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"
```

Both passed. Focused CLI tests: 14 tests, 67 assertions, 0 failures, 0 errors. Core scry run: 55 tests, 241 pass, 0 fail, 0 error.

## Slice 4 Kaocha CLI bridge - 2026-06-03

Implemented Kaocha CLI mode while preserving the optional dependency boundary:

- `scry.cli/run-cli` now dynamically resolves `scry.kaocha/run` only when `:runner :kaocha` is requested, so loading `scry.cli` in the core jar does not require the optional adapter.
- Requesting Kaocha mode without `src-kaocha`/Kaocha on the classpath returns a non-zero structured runner error with the clear message that the optional adapter is required.
- Normalized Kaocha `:suite`, `:suites`, `:config`, `:source-paths`, `:test-paths`, `:ns-patterns`, and CLI `:dirs`→`:test-paths` fallback options pass through the same `run-cli` path as core mode.
- `scry.kaocha/run` now accepts an optional `:progress-callback`. It installs a quiet additional Kaocha reporter that tracks assertion counts between `:begin-test-var` and `:end-test-var`, then invokes the callback live once per completed test var. Existing adapter calls stay quiet when no callback is supplied.
- Kaocha CLI result files are written from `:canonical-results` and preserve adapter output semantics: merged stdout/stderr in `:out`, empty `:err` unless the adapter supplies otherwise.
- Added focused optional tests in `test/scry/cli_kaocha_test.clj` for suite selection, fallback `:dirs`, live adapter progress callback, summary/progress output, and result-file output semantics. Core CLI tests now also cover the unavailable-adapter error path.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Both passed. Core CLI tests: 14 tests, 70 assertions, 0 failures, 0 errors. Optional Kaocha CLI tests: 3 tests, 17 assertions, 0 failures, 0 errors.

## Slice 5 entry points - 2026-06-03

Implemented the command-line entry-point layer on top of the existing shared `run-cli` path:

- Added `scry.cli/run` as the `clojure -X` entry point. It normalizes EDN options, calls `run-cli`, returns successful outcomes, and throws `ex-info` with `:type :scry.cli/non-zero`, `:exit-code`, `:summary`, `:error`, and full `:outcome` for non-zero CLI outcomes.
- Added `scry.cli/main-outcome` as a testable main-style boundary that parses string args, handles `--help`, delegates test-running invocations to `run-cli`, prints terse argument errors, and returns an exit code without terminating the process.
- Added `scry.cli/-main`, which simply calls `main-outcome` and then `System/exit` with the returned code.
- Extended focused CLI tests to cover successful `run`, throwing `run`, `--help`, argument-error diagnostics, and main-style delegation without mutating the real project `.scry-results/`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test -m scry.cli --help
clojure -X:test scry.cli/run :vars '[scry.fixtures.passing/arithmetic-passes]'
clojure -M:test -m scry.cli
```

Focused CLI tests passed: 19 tests, 87 assertions, 0 failures, 0 errors. Manual entry-point checks passed; the broad `-m` run reported 60 passing tests and 264 passing assertions, then the transient root `.scry-results/` directory was removed.

## Slice 6 documentation and housekeeping - 2026-06-03

Completed user-facing and maintainer documentation for the CLI behavior:

- Added `.scry-results/` to `.gitignore` so local CLI result directories are not committed.
- Updated `README.md` with `-m` and `-X` CLI examples, core selector examples, live progress/summary behavior, result-file directory semantics, exit-code semantics, and optional Kaocha CLI examples/selector constraints.
- Updated `AGENTS.md` to keep REPL-first guidance primary while documenting when to use the dedicated CLI fallback, focused CLI verification commands, and the `scry.cli`/optional Kaocha dynamic-loading architecture boundary.
- Updated `CHANGELOG.md` Unreleased with the new CLI entry points, progress, summary, `.scry-results/*.edn`, selector, Kaocha mode, and exit-code behavior.

No implementation behavior changes were made in this slice beyond ignoring `.scry-results/`.

## Slice 7 regression verification - 2026-06-03

Completed the pinned regression checks after documentation/housekeeping:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run)] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"
clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.build-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -T:build jar
```

Results:

- Focused core CLI tests: 19 tests, 87 assertions, 0 failures, 0 errors.
- Focused optional Kaocha CLI tests: 3 tests, 17 assertions, 0 failures, 0 errors.
- Existing core suite through `scry`: 60 tests, 264 pass, 0 fail, 0 error.
- Focused build checks: 6 tests, 72 assertions, 0 failures, 0 errors.
- `clojure -T:build jar` completed successfully, verifying the core artifact still builds with `scry.cli` included and optional Kaocha adapter code excluded by existing build rules.

No limitations or deviations from the planned CLI behavior were discovered in this verification pass.

## Implementation review - 2026-06-03

Found one actionable implementation-quality issue: the `clojure -X` entry point (`scry.cli/run`) lets option-normalization `:scry.cli/argument-error` exceptions escape directly, so argument errors do not carry the required non-zero `:exit-code` / structured `:outcome` data promised for `-X` failures. Focused core and optional Kaocha CLI tests pass, but they do not cover this `-X` argument-error path.

## Implementation review follow-up - 2026-06-03

Completed the review-added `-X` argument-error follow-up:

- Added a shared `non-zero-exception` helper for `scry.cli/run` so non-zero test outcomes and normalization/argument errors use the same `:scry.cli/non-zero` `ex-info` contract.
- Converted `:scry.cli/argument-error` exceptions from `normalize-exec-opts` into structured outcomes carrying `:exit-code 1`, nil `:summary`, and full `:error` / `:outcome` data.
- Added focused coverage that `cli/run` with an invalid `:runner` throws `:scry.cli/non-zero` and preserves the underlying `:scry.cli/argument-error` data.
- Marked the implementation-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 19 tests, 94 assertions, 0 failures, 0 errors.

## Implementation review - 2026-06-03

Found one actionable implementation-quality issue: error result files are written with raw Throwable values in assertion `:actual`, so `pr-str` emits `#error` and `.scry-results/*.edn` cannot be read with `clojure.edn/read-string`. This violates the readable EDN result-file requirement for erroring vars; focused CLI tests cover failing EDN files but not error EDN readability.

## Implementation review follow-up - 2026-06-03

Completed the review-added EDN readability follow-up for error result files:

- Added CLI result-file sanitization that recursively replaces raw `Throwable` values with EDN-readable maps containing `:type :throwable`, exception class, message, rendered stacktrace, ex-data when present, and nested cause detail.
- Result files still preserve the existing assertion-level `:stacktrace` string for error assertions, while `:actual` no longer prints as unreadable `#error` data.
- Added focused coverage for `scry.fixtures.erroring/throws-exception` verifying the generated `.scry-results/*.edn` file reads with `clojure.edn/read-string` and preserves exception detail.
- Marked the implementation-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run)] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"
```

Results:

- Focused core CLI tests: 21 tests, 104 assertions, 0 failures, 0 errors.
- Existing core suite through `scry`: 61 tests, 281 pass, 0 fail, 0 error.

## Implementation review - 2026-06-03

Found one actionable implementation-quality issue: result-file sanitization now handles raw `Throwable` values, but other non-EDN-readable values in assertion data (for example arbitrary objects in `:expected`/`:actual`, ex-data, or nested structures) still print as unreadable `#object` forms. Since CLI files are documented as `.edn` and intended for `clojure.edn/read-string`, result-file writing should recursively coerce any non-EDN-readable leaf to a readable tagged data representation while preserving useful `pr-str`/class detail.

## Implementation review follow-up - 2026-06-03

Completed the review-added arbitrary-object EDN readability follow-up:

- Extended CLI result-file sanitization beyond `Throwable` values: EDN scalar leaves pass through, maps/vectors/sets/seqs/arrays/iterables recurse, Java maps are coerced to plain maps, and arbitrary remaining objects are represented as readable maps with `:type :object`, `:class`, and `:pr-str` detail.
- Throwable sanitization continues to preserve class/message/stacktrace/ex-data/cause, with ex-data recursively sanitized by the same general leaf coercion.
- Added focused core CLI coverage using an injected runner result whose failing assertion contains arbitrary `Object` values in both direct and nested assertion data; the generated `.scry-results/*.edn` reads with `clojure.edn/read-string` and preserves useful object class/`pr-str` detail.
- Marked the implementation-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 22 tests, 114 assertions, 0 failures, 0 errors.

Note: I did not rerun the full core suite after the focused follow-up because the broad suite currently includes intentional failing fixture vars used by CLI tests when run under discovery, causing `scry/run` to report non-zero; focused CLI verification covers this follow-up.

## Implementation review - 2026-06-03

Found one actionable implementation-quality issue: Kaocha-mode option normalization rejects `:ns-pattern` as a core-only selector, but the documented `:namespace-pattern` and `:namespace-regex` aliases are not rejected and are silently ignored. This lets invalid `clojure -X` Kaocha invocations appear normalized while dropping the user's selector instead of producing the required argument error. Focused core and optional Kaocha CLI tests pass, but they do not cover the Kaocha alias rejection path.

## Implementation review follow-up - 2026-06-03

Completed the review-added Kaocha namespace-pattern alias rejection follow-up:

- Updated `scry.cli` so Kaocha-mode core-only selector validation includes all namespace-pattern aliases: `:ns-pattern`, `:namespace-pattern`, and `:namespace-regex`.
- Added focused core CLI normalization coverage asserting `:runner :kaocha` rejects both alias forms instead of silently ignoring them.
- Marked the implementation-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results:

- Focused core CLI tests: 22 tests, 116 assertions, 0 failures, 0 errors.
- Focused optional Kaocha CLI tests: 3 tests, 17 assertions, 0 failures, 0 errors.

## Implementation review - 2026-06-03

No new actionable implementation-quality issues found. Reviewed the task design/plan/steps, `scry.cli`, core capture/runner progress hooks, optional Kaocha progress bridge, focused CLI tests, README/AGENTS/CHANGELOG coverage, and reran focused core plus optional Kaocha CLI checks successfully:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (22 tests, 116 assertions); focused optional Kaocha CLI tests passed (3 tests, 17 assertions).

## Test review - 2026-06-03

Found one actionable test-quality issue: the arbitrary-object EDN readability coverage uses an injected `:run-clojure-test` function that fabricates a canonical result, so it does not verify the real `clojure.test` capture/CLI result-file path for arbitrary non-EDN values. Focused core CLI tests and optional Kaocha CLI tests pass.

## Test review follow-up - 2026-06-03

Completed the review-added arbitrary-object test-quality follow-up:

- Added `scry.fixtures.arbitrary/arbitrary-object-fails`, a real `clojure.test` fixture whose failing assertion contains arbitrary `Object` values in the captured expected/actual form.
- Replaced the fabricated-runner arbitrary-object EDN readability coverage with an end-to-end `run-cli` invocation selecting that fixture var through the real core runner.
- The test now verifies CLI progress, deterministic result-file naming, `clojure.edn/read-string` readability, and sanitized object leaf maps with class/`pr-str` detail in the real captured assertion tree.
- Marked the test-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results:

- Focused core CLI tests: 23 tests, 113 assertions, 0 failures, 0 errors.
- Focused optional Kaocha CLI tests: 3 tests, 17 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03

Found one actionable test-quality issue: focused core CLI execution coverage exercises explicit vars and no-test namespace selection, but not successful non-var core selectors end-to-end. Add `run-cli` coverage for namespace and directory/ns-pattern discovery selectors so the documented core CLI selector support is verified beyond normalization. Focused core CLI tests and optional Kaocha CLI tests pass.

## Test review follow-up - 2026-06-03

Completed the review-added core selector coverage follow-up:

- Added end-to-end `run-cli` coverage for a successful explicit namespace selector using `scry.fixtures.passing`.
- Added end-to-end `run-cli` coverage for successful directory plus `:ns-pattern` discovery selecting the same passing fixture namespace.
- Both tests verify exit code, stdout progress/summary, empty stderr, empty `.scry-results/`, and the canonical executed var, so documented non-var core selectors are now verified beyond normalization.
- Marked the test-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 26 tests, 123 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03

Found one actionable test-quality issue: optional Kaocha CLI tests cover `tests.edn` suite selection and fallback `:dirs`/`:test-paths`, but do not exercise a `run-cli` invocation with an explicit `:config` map. Since explicit Kaocha config is a documented/accepted selector path and has distinct normalization/adapter behavior from loaded `tests.edn` and fallback config, add end-to-end optional Kaocha CLI coverage for it. Focused core CLI tests and optional Kaocha CLI tests pass:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (26 tests, 123 assertions); focused optional Kaocha CLI tests passed (3 tests, 17 assertions).

## Test review follow-up - 2026-06-03

Completed the review-added optional Kaocha explicit-config coverage follow-up:

- Added end-to-end `run-cli` coverage in `scry.cli-kaocha-test` for an explicit normalized Kaocha `:config` map, distinct from `tests.edn` loading and fallback `:dirs` / `:test-paths` behavior.
- The new test verifies selecting a passing suite from the explicit config succeeds with stdout progress/summary, empty stderr, no result files, and the expected canonical executed var.
- The same explicit config is then used to select a failing suite, verifying non-zero exit, stderr progress name, deterministic `.scry-results/*.edn` output, readable result data, and captured adapter `:out` detail.
- Marked the test-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 4 tests, 29 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03

Found one actionable test-quality issue: `parse-main-args` coverage exercises the documented long `-m` flags but not the documented short/alias forms (`-r`, `-d`, `--ns`/`-n`, `-v`, namespace-pattern aliases, and `-s`), so alias parser behavior is only verified by implementation inspection. Focused core CLI tests and optional Kaocha CLI tests pass:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (26 tests, 123 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Test review follow-up - 2026-06-03

Completed the review-added `parse-main-args` alias coverage follow-up:

- Added focused parser coverage for documented `-m` short/alias flags: `-r`, `-d`, `--ns`, `-n`, `-v`, `--namespace-pattern`, `--namespace-regex`, and `-s`.
- The new assertions verify aliases normalize to the same core/Kaocha option shapes as the long flags, including repeated namespace aliases and repeated short Kaocha suite selection.
- Marked the test-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 26 tests, 132 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03

Found one actionable test-quality issue: `run-cli-no-tests-and-runner-errors-test` still verifies runner-exception handling by injecting a throwing `:run-clojure-test` function, which is effectively a stubbed runner boundary. To align with the task-test-review/testing-without-mocks standard, replace or supplement it with state-based coverage that triggers the real runner path (or a narrower nullable process/IO boundary) without fabricating runner behavior. Focused core CLI tests and optional Kaocha CLI tests pass:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (26 tests, 132 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Test review follow-up - 2026-06-03

Completed the review-added runner-exception test-quality follow-up:

- Replaced the injected throwing `:run-clojure-test` coverage in `run-cli-no-tests-and-runner-errors-test` with a state-based real-runner path: selecting a deliberately missing namespace causes the actual core runner `require` path to throw.
- The test still verifies the CLI contract for runner exceptions: non-zero outcome, stale `.scry-results/` cleanup, terse stderr diagnostic, and preserved exception object data.
- Removed the stubbed runner function from focused CLI tests; the remaining `:run-clojure-test` boundary is only the production nullable boundary documented in `scry.cli`.
- Marked the test-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 26 tests, 132 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03

Found one actionable test-quality issue: focused CLI tests cover passing, failing, erroring, runner-error, and no-test outcomes, but do not exercise an executed test var with no assertion events / `:unknown` status. The design explicitly requires unknown-status vars to print a name to stderr, appear in the summary as unknown, write no failure/error result file, and exit non-zero, so add end-to-end real-runner coverage for that state. Focused core CLI tests and optional Kaocha CLI tests pass:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (26 tests, 132 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Test review follow-up - 2026-06-03

Completed the review-added unknown-status coverage follow-up:

- Added `scry.fixtures.unknown/no-assertions`, a real `deftest` fixture that executes without emitting assertion events.
- Added end-to-end core `run-cli` coverage selecting that fixture through the real runner.
- The new test verifies non-zero exit, stderr progress name, stdout summary with `1 unknown`, stale `.scry-results/` cleanup with no failure/error result file, and canonical `:unknown` result data.
- Marked the test-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 28 tests, 138 assertions, 0 failures, 0 errors.

## Implementation review - 2026-06-03

No new actionable implementation-quality issues found. Reviewed the current task artifacts, `scry.cli`, core capture/runner progress hook, optional Kaocha progress bridge, focused core CLI tests, focused optional Kaocha CLI tests, and user-facing docs. Re-ran focused CLI verification successfully:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (28 tests, 138 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Test review - 2026-06-03

Found one actionable test-quality issue: focused CLI tests verify default detailed result-file writing, but do not exercise the planned interaction with a user-supplied `:result-format`. Add end-to-end coverage where `:result-format` projects or omits normal `:results`, while CLI-injected `:canonical-results` still drives detailed `.scry-results/*.edn` output with assertions and captured output. Focused core CLI tests and optional Kaocha CLI tests pass:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (28 tests, 138 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Test review follow-up - 2026-06-03

Completed the review-added user `:result-format` / canonical-result-file coverage follow-up:

- Added end-to-end core `run-cli` coverage selecting the real `scry.fixtures.output/noisy-and-fails` fixture with a user-supplied `:result-format` whose `:var` scope omits `:results` from the returned projection.
- The new test verifies the returned user-facing result preserves that projection (`:results` absent) while the CLI-injected `:canonical-results` still lets `.scry-results/scry.fixtures.output__noisy-and-fails.edn` be written.
- The result-file assertions verify detailed canonical data survives projection, including the failing assertion form plus captured stdout and stderr.
- Marked the test-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Passed: 30 tests, 149 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03

No new actionable test-quality issues found. Reviewed task design/plan/steps, focused core CLI tests, optional Kaocha CLI tests, CLI fixtures, `scry.cli`, core/Kaocha progress hooks, and README/AGENTS/CHANGELOG coverage. Re-ran focused checks successfully:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (30 tests, 149 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Test-shaper review - 2026-06-03

Found one actionable test-quality issue: focused CLI tests do not exercise the explicit mixed fail+error per-var precedence end-to-end through `run-cli`. The design requires a var with both failures and errors to count as errored, print only the unqualified name, write one result file, and exit non-zero; current coverage checks runner callback precedence but not the CLI summary/result-file contract for that mixed state. Focused core and optional Kaocha CLI tests pass:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (30 tests, 149 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Test-shaper follow-up - 2026-06-03

Completed the review-added mixed fail+error CLI coverage follow-up:

- Added end-to-end core `run-cli` coverage selecting the real `scry.fixtures.mixed/fail-then-error` fixture through the real runner.
- The new test verifies non-zero exit, exactly one stderr progress name, errored test-var summary precedence, exactly one `.scry-results/scry.fixtures.mixed__fail-then-error.edn` file, result-file `:status :error`, combined fail/error assertion summary, and canonical result `:error` status.
- Marked the test-shaper follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (32 tests, 159 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Test-shaper review - 2026-06-03

No new actionable test-quality issues found. Reviewed the task design/plan/steps, focused core CLI tests, optional Kaocha CLI tests, CLI fixtures, core/Kaocha progress hooks, result-file sanitization coverage, parser/entry-point coverage, and README/AGENTS/CHANGELOG references. Re-ran focused checks successfully:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (32 tests, 159 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Docs review - 2026-06-03

Found one actionable documentation issue: the user-facing CLI `--help` usage text in `scry.cli/usage` shows `clojure -X:test scry.cli/run '{:runner :clojure-test}'`, but `clojure -X` examples in README use the supported key/value argument form. Update the help text so the built-in CLI documentation matches the documented invocation syntax. Reviewed README, CHANGELOG, AGENTS, CLI usage text, and referenced CLI/Kaocha implementation; no other new docs issues found.

## Docs review follow-up - 2026-06-03

Completed the review-added CLI usage documentation follow-up:

- Updated `scry.cli/usage` so the built-in `--help` text shows the supported `clojure -X` key/value argument form (`clojure -X:test scry.cli/run :runner :clojure-test`) instead of a quoted EDN map literal.
- Verified `clojure -M:test -m scry.cli --help` prints the corrected invocation example.
- Marked the docs-review follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test -m scry.cli --help
```

Results: focused core CLI tests passed (32 tests, 159 assertions); help output shows `clojure -X:test scry.cli/run :runner :clojure-test`.

## Docs review - 2026-06-03

No new actionable documentation issues found. Reviewed `README.md`, `CHANGELOG.md`, `AGENTS.md`, `scry.cli/usage`, and the referenced CLI/Kaocha implementation against the task design and plan. README documents the separate CLI entry points, selector examples, progress/summary/result-file behavior, exit-code semantics, and optional Kaocha mode; CHANGELOG records the user-visible CLI change; no `doc/` directory is present. Existing docs-review follow-up for the `-X` help example is complete.

## Code-shaper review - 2026-06-03

Found one actionable code-quality issue: `src/scry/cli.clj` is now a large multi-responsibility namespace (argument normalization, runner dispatch, filesystem lifecycle, result-file naming/writing, EDN sanitization, summaries, and entry points). The result-file/EDN-sanitization section is separable and would improve local comprehensibility and future mutation if extracted behind a small helper namespace or clearly isolated boundary. Focused core and optional Kaocha CLI checks pass:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (32 tests, 159 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Code-shaper follow-up - 2026-06-03

Completed the review-added CLI result-file extraction follow-up:

- Added `scry.cli.results` as a focused helper namespace for `.scry-results/` directory lifecycle, deterministic namespace-prefixed file naming, failure/error result-file writing, and recursive EDN-readable sanitization.
- Updated `scry.cli` to delegate result directory preparation and result-file writing to that helper namespace, leaving CLI parsing, runner orchestration, progress, summaries, and entry points easier to inspect locally.
- Preserved the existing result-file behavior and public CLI contract; no tests required behavior changes.
- Marked the code-shaper follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (32 tests, 159 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Code-shaper review - 2026-06-03

Found one actionable robustness issue: `scry.cli.results/delete-recursive!` uses `java.io.File` recursion and follows directory symlinks, so a malicious or accidental `.scry-results` symlink to another directory could cause CLI run-start cleanup to delete files outside the intended result directory. Focused core CLI and optional Kaocha CLI checks pass.

## Code-shaper follow-up - 2026-06-03

Completed the review-added symlink-safe `.scry-results/` cleanup follow-up:

- Replaced `scry.cli.results/delete-recursive!` directory detection/deletion with `java.nio.file.Files` operations using `LinkOption/NOFOLLOW_LINKS`, so symlinked directories are deleted as links rather than traversed.
- Preserved recursive cleanup for real directories and the existing clear/recreate result-directory lifecycle.
- Added focused core CLI coverage that makes `.scry-results` a symlink to an outside directory, runs a passing CLI invocation, verifies the outside target file remains intact, and verifies `.scry-results` is recreated as a real empty directory.
- Marked the code-shaper follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (34 tests, 164 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Code-shaper review - 2026-06-03

Found one actionable consistency issue: `parse-main-args` silently accepts multiple namespace-pattern flag aliases and keeps the last value (for example `--ns-pattern a --namespace-pattern b`), while `normalize-exec-opts` rejects multiple namespace-pattern option keys and the design says conflicting aliases are argument errors. Make the `-m` parser track which namespace-pattern alias was supplied and reject repeated/conflicting namespace-pattern flags instead of overwriting. Focused code inspection found no other new code-shaping issues.

## Code-shaper follow-up - 2026-06-03

Completed the review-added namespace-pattern alias consistency follow-up:

- Updated `parse-main-args` to track the supplied namespace-pattern flag alias and reject any repeated/conflicting namespace-pattern option (`--ns-pattern`, `--namespace-pattern`, or `--namespace-regex`) instead of silently keeping the last value.
- Kept the normalized option map clean by removing the parser-only tracking key before `normalize-exec-opts` sees the raw options.
- Added focused parser coverage for repeated namespace-pattern alias combinations.
- Marked the code-shaper follow-up item complete in `steps.md`.

Verification:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (34 tests, 166 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).

## Code-shaper review - 2026-06-03

No new actionable code-quality issues found. Reviewed task artifacts plus `scry.cli`, `scry.cli.results`, core/Kaocha progress hooks, focused CLI tests, and user-facing docs against the code-shaper criteria for simplicity, consistency, local comprehensibility, and robustness. The previous result-file extraction, symlink-safe cleanup, and namespace-pattern parser consistency follow-ups are complete. Re-ran focused core and optional Kaocha CLI checks successfully:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

Results: focused core CLI tests passed (34 tests, 166 assertions); focused optional Kaocha CLI tests passed (4 tests, 29 assertions).
