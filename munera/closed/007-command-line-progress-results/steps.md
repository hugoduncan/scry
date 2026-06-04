# Steps

## Plan ambiguity review follow-up

- [x] Pin the focused CLI test namespaces, aliases, and exact commands for core CLI tests and optional Kaocha CLI tests.
- [x] Decide and document the exact mechanism by which CLI runs obtain unprojected detailed entries for every executed var (for example `:canonical-results` retention versus a dedicated helper), including how it interacts with user-supplied `:result-format`.
- [x] Choose and document the exact namespace-prefixed `.scry-results/*.edn` file-name scheme before implementation/tests.
- [x] Clarify whether Kaocha CLI progress must be live per var or may be emitted deterministically after the Kaocha run, and align the plan/tests/docs with that decision.

## Plan inconsistency review follow-up

- [x] Reconcile plan.md and steps.md so Kaocha CLI progress is consistently mandatory live per var when the optional adapter is available, removing or rewriting the earlier fallback/"best available" post-run-progress language.

## Slice 1: Core progress hook and detailed CLI result support

- [x] Inspect `scry.capture` and `scry.clojure-test` to identify the smallest optional hook point for end-of-test-var progress.
- [x] Add an optional progress callback option to the core runner path that is invoked once per executed test var after its final status is known.
- [x] Ensure the progress callback receives enough data to print unqualified names and distinguish `:pass`, `:fail`, `:error`, and `:unknown` with error precedence.
- [x] Verify existing `scry.core/run` calls remain quiet and return the same scoped result shapes when no CLI progress callback is supplied.
- [x] Add focused tests for core progress callback ordering and mixed-status precedence.
- [x] Add or verify a CLI result-format helper that can request detailed entries including assertions, `:out`, and `:err` for all executed vars.

## Slice 2: CLI option normalization and `-m` parsing

- [x] Create `src/scry/cli.clj` with shared constants, usage text, and structured error helpers.
- [x] Implement runner normalization for `:clojure-test`, `:core`, `:test`, `:kaocha`, and equivalent strings/symbols.
- [x] Implement `-X` normalization for `:dirs`, `:namespaces`, `:vars`, namespace-regex aliases, `:result-format`, and Kaocha keys.
- [x] Implement fully-qualified var symbol/string resolution for CLI options, including requiring namespaces and rejecting unqualified, unresolved, or non-test vars.
- [x] Implement validation that core mode rejects Kaocha-only keys and Kaocha mode rejects core-only namespace/var/ns-pattern selectors.
- [x] Implement `:dirs` normalization and Kaocha `:dirs` to fallback `:test-paths` mapping, rejecting `:dirs` plus explicit `:test-paths` conflicts in Kaocha mode.
- [x] Implement the explicit `-m` parser for `--runner`, `--dir`, `--namespace`/`--ns`, `--var`, namespace regex aliases, `--result-format`, `--suite`, `--suites`, `--config`, and `--help`.
- [x] Parse `--result-format`, `--suites`, and `--config` with `clojure.edn/read-string` and return structured argument errors for invalid EDN or wrong shapes.
- [x] Add focused normalization/parser tests for accepted core forms, accepted Kaocha forms, aliases, conflicts, missing values, invalid regexes, invalid vars, and help.

## Slice 3: Shared CLI runner and `.scry-results/` effects

- [x] Define the `run-cli` outcome shape with `:exit-code`, `:result`, `:summary`, `:result-files`, and `:error` keys.
- [x] Implement a small injectable/bindable IO boundary for stdout/stderr writers, current working directory, filesystem operations, and runner dispatch where practical.
- [x] Implement `.scry-results/` clearing and recreation at the start of every test-running CLI invocation.
- [x] Implement deterministic namespace-prefixed `.edn` file naming for failed/erroring vars and test same unqualified names in different namespaces do not collide.
- [x] Write detailed EDN result files for failed/erroring entries, including var symbol, namespace, status, assertion summary, assertions, stack traces when present, and captured `:out`/`:err`.
- [x] Implement live progress printing for core mode: `.` to stdout for passing vars and unqualified names to stderr for failing/erroring/unknown vars, flushing after each item.
- [x] Implement final stdout summary text with passed/failed/errored assertion counts and passed/failed/errored test-var counts.
- [x] Implement exit-code classification: zero only when at least one test var executed and all vars passed; non-zero for fail, error, unknown, no tests, argument errors, and runner exceptions.
- [x] Catch runner exceptions in `run-cli`, print/report a terse diagnostic, preserve exception data in the outcome, and return non-zero.
- [x] Add focused tests using temporary working directories for directory clearing, stale file removal, empty results directory after passing runs, EDN contents, progress streams, summary text, and exit-code classification.

## Slice 4: Kaocha CLI bridge

- [x] Implement dynamic loading/invocation of `scry.kaocha/run` for `:runner :kaocha` without requiring `scry.kaocha` from `scry.core` or at CLI namespace load time.
- [x] Return a clear non-zero runner/argument error when Kaocha mode is requested but `scry.kaocha` or Kaocha dependencies are unavailable.
- [x] Pass normalized `:suite`, `:suites`, `:config`, and supported fallback config keys through to the Kaocha adapter.
- [x] Ensure Kaocha result-file entries preserve adapter semantics: merged captured output in `:out` and empty `:err` unless the adapter supplies otherwise.
- [x] Implement live Kaocha per-var progress through an adapter progress callback/reporter hook; if unavailable without compromising the optional adapter boundary, record it as a blocking implementation issue rather than using post-run progress.
- [x] Add focused Kaocha CLI tests under the optional `:kaocha` alias for suite/config handling, selector rejection, result-file output, and progress/summary behavior.

## Slice 5: Entry points and invocation behavior

- [x] Implement `scry.cli/run` as the `clojure -X` entry point that normalizes EDN opts, calls `run-cli`, returns successful outcomes, and throws `ex-info` with `:exit-code`, `:summary`, and error data on non-zero outcomes.
- [x] Implement `scry.cli/-main` as the `clojure -M -m` entry point that parses string args, handles `--help`, calls the shared implementation, prints terse diagnostics for argument errors, and exits with the returned code.
- [x] Verify both entry points use the same normalized option map and `run-cli` path after parsing.
- [x] Add focused tests for `run` throwing on non-zero outcomes and for `-main` parse/help plumbing without relying on real project `.scry-results/`.
- [x] Manually verify representative commands: `clojure -M:test -m scry.cli --help`, `clojure -M:test -m scry.cli`, and `clojure -X:test scry.cli/run`.

## Slice 6: Documentation and housekeeping

- [x] Add `.scry-results/` to `.gitignore`.
- [x] Update README with the new CLI `-m` and `-X` entry points, selector examples, progress/summary behavior, result-file directory, and exit-code semantics.
- [x] Update README with Kaocha CLI mode examples using the optional Kaocha alias/artifact.
- [x] Update AGENTS.md with maintainer/agent guidance for using the CLI while preserving REPL-first `scry.core/run` guidance.
- [x] Update CHANGELOG.md Unreleased with the new command-line progress, summary, result-file, selector, and exit-code behavior.
- [x] Update any build/artifact documentation if needed to note that `scry.cli` is part of the core jar while Kaocha CLI mode still requires the optional adapter jar/alias.

## Slice 7: Regression verification

- [x] Run focused CLI tests in core mode.
- [x] Run focused CLI/Kaocha tests with the optional `:kaocha` alias.
- [x] Run the existing core test suite through scry and verify scoped result behavior remains unchanged.
- [x] Run focused build checks to verify the core jar still excludes Kaocha adapter code/dependencies.
- [x] Run release/build-adjacent checks if CLI namespace inclusion affects jar artifact expectations.
- [x] Record implementation decisions, verification commands, and any limitations in `implementation.md`.

## Implementation review follow-up

- [x] Make `scry.cli/run` convert normalization/argument errors into the same structured non-zero `ex-info` contract as other `-X` failures, including `:exit-code` and error/outcome data, and add focused coverage for the `-X` argument-error path.
- [x] Make error result files EDN-readable by sanitizing Throwable-containing assertion data (while preserving stacktrace/details) and add focused coverage that an erroring CLI run's `.scry-results/*.edn` reads with `clojure.edn/read-string`.
- [x] Make CLI result-file sanitization recursively coerce all non-EDN-readable leaf values (not only `Throwable`) into readable data with useful class/`pr-str` detail, and add focused coverage that failure result files containing arbitrary object assertion data read with `clojure.edn/read-string`.
- [x] Make Kaocha-mode normalization reject namespace-pattern aliases (`:namespace-pattern` and `:namespace-regex`) the same way it rejects `:ns-pattern`, and add focused coverage for those alias rejection paths.

## Test review follow-up

- [x] Replace the stubbed arbitrary-object result-file test with an end-to-end fixture through the real `clojure.test` runner so non-EDN-readable assertion values are captured, sanitized, written, and read back without a fabricated runner result.
- [x] Add end-to-end core CLI `run-cli` coverage for successful namespace and directory/ns-pattern selectors, not only explicit vars/no-test namespaces, so the documented core selector support is verified beyond normalization.
- [x] Add end-to-end optional Kaocha CLI `run-cli` coverage for an explicit `:config` map, not only `tests.edn` suite selection and fallback `:dirs`/`:test-paths`, so the documented Kaocha config selector path is verified beyond normalization.
- [x] Add focused `parse-main-args` coverage for the documented `-m` short/alias flags (`-r`, `-d`, `--ns`/`-n`, `-v`, namespace-pattern aliases, and `-s`) so parser alias behavior is verified beyond implementation inspection.

## Test review follow-up

- [x] Replace or supplement the injected throwing `:run-clojure-test` runner-exception coverage with state-based coverage that exercises the real runner path (or a narrower nullable boundary) without a stubbed runner function.

## Test review follow-up

- [x] Add end-to-end core CLI coverage for an executed `:unknown` status test var (for example a real `deftest` with no assertions), verifying stderr progress name, summary unknown count, non-zero exit, and no failure/error result file.

## Test review follow-up

- [x] Add end-to-end CLI coverage for a user-supplied `:result-format` that projects or omits `:results`, verifying the returned user-facing projection is preserved while `.scry-results/*.edn` is still written from detailed `:canonical-results` with assertions and output.

## Test-shaper review follow-up

- [x] Add end-to-end core CLI `run-cli` coverage for a mixed-status test var with both failure and error assertions, verifying it counts as errored (not failed) in summary/result-file status, prints only the unqualified name to stderr, writes exactly one result file, and exits non-zero.

## Docs review follow-up

- [x] Update the user-facing `scry.cli/usage` `clojure -X` example so CLI `--help` documents the supported key/value argument form rather than a quoted map literal.

## Code-shaper review follow-up

- [x] Extract CLI result-file naming/writing and EDN-readable sanitization out of `scry.cli` (or behind a small isolated boundary) so option parsing/runner orchestration remains locally comprehensible while preserving existing result-file behavior and focused CLI coverage.

## Code-shaper review follow-up

- [x] Make `.scry-results/` cleanup symlink-safe so run-start deletion never recurses through a symlink or removes files outside the intended result directory.

## Code-shaper review follow-up

- [x] Make `parse-main-args` reject repeated/conflicting namespace-pattern aliases (`--ns-pattern`, `--namespace-pattern`, `--namespace-regex`) instead of silently keeping the last value, and add focused parser coverage.
