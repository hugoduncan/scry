# Steps

## Slice 1: Baseline and focused characterization

- [ ] Inspect current `scry.cli`, `scry.cli.results`, focused core CLI tests, and optional Kaocha CLI tests to identify existing naming/progress/outcome seams.
- [ ] Add focused `scry.cli.results` coverage for failing/erroring entries with nil, absent, or otherwise non-concrete `:var` values, including deterministic synthetic filenames.
- [ ] Add focused `run-cli` coverage using the injected `:run-clojure-test` boundary for a synthetic nil-var failing/erroring canonical entry that previously crashed result-file naming.
- [ ] Add focused progress coverage showing synthetic nil-var failing/erroring/unknown entries print useful labels instead of throwing.

## Slice 2: Synthetic entry identity and result files

- [ ] Add a helper for recognizing concrete var-backed entries: `:var` is a symbol with both namespace and name.
- [ ] Add synthetic token generation for non-var-backed entries using per-status 1-based counters: `suite-error-N`, `suite-fail-N`, and `suite-unknown-N`.
- [ ] Preserve existing var-backed result-file names exactly for normal test vars.
- [ ] Implement synthetic failing/erroring result-file names with optional encoded `:ns` prefix.
- [ ] Compute result-file assignments from the whole canonical entry collection with a used-filename set and deterministic `--2`, `--3`, ... collision suffixes.
- [ ] Ensure `write-result-files!` writes EDN-readable data for synthetic failing/erroring entries and returns the written paths.

## Slice 3: Synthetic progress labels

- [ ] Refactor CLI progress callback creation so each run has per-status synthetic counters.
- [ ] Keep var-backed progress unchanged: passing vars print `.`, failing/erroring/unknown vars print the unqualified var name.
- [ ] Make non-var-backed failing/erroring/unknown progress print synthetic tokens, optionally namespace-prefixed when `:ns` is present.
- [ ] Ensure progress handling tolerates nil/absent/non-concrete `:var` without throwing for all statuses.

## Slice 4: Outcome classification

- [ ] Add a classifier that returns `:scry.cli/outcome-kind` using the design precedence.
- [ ] Classify structured argument errors as `:scry.cli/argument-error` on `clojure -X`/direct structured surfaces.
- [ ] Classify runner infrastructure errors, including malformed results without vector `:canonical-results`, as `:scry.cli/runner-error`.
- [ ] Classify failing/erroring non-concrete canonical entries as `:scry.cli/load-error` once a valid canonical result vector exists.
- [ ] Classify concrete var-backed failures/errors or aggregate failing/erroring assertion counts as `:scry.cli/test-failure`.
- [ ] Classify canonical `:unknown` entries as `:scry.cli/unknown-result` when no higher-precedence kind applies.
- [ ] Classify no concrete executable var-backed entries as `:scry.cli/zero-tests` when no higher-precedence kind applies.
- [ ] Classify successful runs with at least one concrete executable passing var and no non-zero signal as `:scry.cli/pass`.
- [ ] Add focused tests for pass, normal test failure, synthetic load/suite error, unknown result, zero tests, runner error, and aggregate assertion failure classification.

## Slice 5: `clojure -X` propagation and parser boundary checks

- [ ] Add top-level `:scry.cli/outcome-kind` to every `run-cli` outcome map.
- [ ] Add top-level `:scry.cli/outcome-kind` to `:scry.cli/non-zero` ex-data produced by `scry.cli/run`.
- [ ] Verify the embedded `:outcome` in non-zero ex-data contains the same outcome kind.
- [ ] Add focused tests for `clojure -X` argument-error ex-data classification.
- [ ] Add or update focused tests confirming `-m` parser errors remain process-oriented human stderr plus non-zero exit code, without requiring structured outcome maps.

## Slice 6: Documentation

- [ ] Update `README.md` command-line usage to document `:scry.cli/outcome-kind`, initial keyword vocabulary, and machine-caller guidance to inspect structured outcomes/result files instead of stderr text.
- [ ] Update top-level `SKILL.md` CLI guidance to tell agents to inspect `:scry.cli/outcome-kind` for `-X` outcomes/non-zero ex-data and `.scry-results/*.edn` for failure detail.
- [ ] Update `AGENTS.md` final-verification/CLI guidance to mention structured CLI outcome classification for agents.
- [ ] Update `CHANGELOG.md` Unreleased with the CLI outcome classification and synthetic suite-level result-file handling.

## Slice 7: Verification and task notes

- [ ] Run focused core CLI tests and record the exact command/result in `implementation.md`.
- [ ] Run optional Kaocha CLI focused tests if Kaocha-facing behavior or docs are touched, and record the exact command/result in `implementation.md`.
- [ ] Run the appropriate documented final command-line verification for changed CLI surfaces and record the exact command/result in `implementation.md`.
- [ ] Run `git diff --check` and record the result in `implementation.md`.
- [ ] Update `steps.md` as implementation slices complete.

## Plan ambiguity review follow-up

- [ ] Pin whether `:scry.cli/outcome-kind` is authoritative for `:exit-code` (`:scry.cli/pass` yields `0`; every other kind yields non-zero), and update the implementation/test slices so synthetic-only passing entries cannot exit `0` through the old total-entry `var-count` logic.
- [ ] Decide and document how CLI summary `:tests` counts and `:var-count` treat synthetic/non-var-backed canonical entries (count all result entries versus only concrete executable vars), including the expected stdout summary wording for synthetic load/unknown/zero-test cases.
