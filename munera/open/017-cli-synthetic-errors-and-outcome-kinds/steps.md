# Steps

## Slice 1: Baseline and focused characterization

- [x] Inspect current `scry.cli`, `scry.cli.results`, focused core CLI tests, and optional Kaocha CLI tests to identify existing naming/progress/outcome seams.
- [x] Add focused `scry.cli.results` coverage for failing/erroring entries with nil, absent, or otherwise non-concrete `:var` values, including deterministic synthetic filenames.
- [x] Add focused `run-cli` coverage using the injected `:run-clojure-test` boundary for a synthetic nil-var failing/erroring canonical entry that previously crashed result-file naming.
- [x] Add focused progress coverage showing synthetic nil-var failing/erroring/unknown entries print useful labels instead of throwing.

## Slice 2: Synthetic entry identity and result files

- [x] Add a helper for recognizing concrete var-backed entries: `:var` is a symbol with both namespace and name.
- [x] Add synthetic token generation for non-var-backed entries using per-status 1-based counters: `suite-error-N`, `suite-fail-N`, and `suite-unknown-N`.
- [x] Preserve existing var-backed result-file names exactly for normal test vars.
- [x] Implement synthetic failing/erroring result-file names with optional encoded `:ns` prefix.
- [x] Compute result-file assignments from the whole canonical entry collection with a used-filename set and deterministic `--2`, `--3`, ... collision suffixes.
- [x] Ensure `write-result-files!` writes EDN-readable data for synthetic failing/erroring entries and returns the written paths.

## Slice 3: Synthetic progress labels

- [x] Refactor CLI progress callback creation so each run has per-status synthetic counters.
- [x] Keep var-backed progress unchanged: passing vars print `.`, failing/erroring/unknown vars print the unqualified var name.
- [x] Make non-var-backed failing/erroring/unknown progress print synthetic tokens, optionally namespace-prefixed when `:ns` is present.
- [x] Ensure progress handling tolerates nil/absent/non-concrete `:var` without throwing for all statuses.

## Slice 4: Outcome classification

- [x] Add a classifier that returns `:scry.cli/outcome-kind` using the design precedence and make it authoritative for `:exit-code` (`:scry.cli/pass` => `0`, every other kind => non-zero).
- [x] Classify structured argument errors as `:scry.cli/argument-error` on `clojure -X`/direct structured surfaces.
- [x] Classify runner infrastructure errors, including malformed results without vector `:canonical-results`, as `:scry.cli/runner-error`.
- [x] Classify failing/erroring non-concrete canonical entries as `:scry.cli/load-error` once a valid canonical result vector exists.
- [x] Classify concrete var-backed failures/errors or aggregate failing/erroring assertion counts as `:scry.cli/test-failure`.
- [x] Classify canonical `:unknown` entries as `:scry.cli/unknown-result` when no higher-precedence kind applies.
- [x] Classify no concrete executable var-backed entries as `:scry.cli/zero-tests` when no higher-precedence kind applies.
- [x] Classify successful runs with at least one concrete executable passing var and no non-zero signal as `:scry.cli/pass`.
- [x] Add focused tests for pass, normal test failure, synthetic load/suite error, unknown result, zero tests, runner error, aggregate assertion failure classification, and synthetic-only passing entries exiting non-zero as zero-tests.

## Slice 5: `clojure -X` propagation and parser boundary checks

- [x] Add top-level `:scry.cli/outcome-kind` to every `run-cli` outcome map.
- [x] Add top-level `:scry.cli/outcome-kind` to `:scry.cli/non-zero` ex-data produced by `scry.cli/run`.
- [x] Verify the embedded `:outcome` in non-zero ex-data contains the same outcome kind.
- [x] Add focused tests for `clojure -X` argument-error ex-data classification.
- [x] Add or update focused tests confirming `-m` parser errors remain process-oriented human stderr plus non-zero exit code, without requiring structured outcome maps.

## Slice 6: Documentation

- [x] Update `README.md` command-line usage to document `:scry.cli/outcome-kind`, initial keyword vocabulary, and machine-caller guidance to inspect structured outcomes/result files instead of stderr text.
- [x] Update top-level `SKILL.md` CLI guidance to tell agents to inspect `:scry.cli/outcome-kind` for `-X` outcomes/non-zero ex-data and `.scry-results/*.edn` for failure detail.
- [x] Update `AGENTS.md` final-verification/CLI guidance to mention structured CLI outcome classification for agents.
- [x] Update `CHANGELOG.md` Unreleased with the CLI outcome classification and synthetic suite-level result-file handling.

## Slice 7: Verification and task notes

- [x] Run focused core CLI tests and record the exact command/result in `implementation.md`.
- [x] Run optional Kaocha CLI focused tests if Kaocha-facing behavior or docs are touched, and record the exact command/result in `implementation.md`.
- [x] Run the appropriate documented final command-line verification for changed CLI surfaces and record the exact command/result in `implementation.md`.
- [x] Run `git diff --check` and record the result in `implementation.md`.
- [x] Update `steps.md` as implementation slices complete.

## Plan ambiguity review follow-up

- [x] Pin whether `:scry.cli/outcome-kind` is authoritative for `:exit-code` (`:scry.cli/pass` yields `0`; every other kind yields non-zero), and update the implementation/test slices so synthetic-only passing entries cannot exit `0` through the old total-entry `var-count` logic.
- [x] Decide and document how CLI summary `:tests` counts and `:var-count` treat synthetic/non-var-backed canonical entries (count all result entries versus only concrete executable vars), including the expected stdout summary wording for synthetic load/unknown/zero-test cases.
