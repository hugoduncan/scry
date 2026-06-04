# Implementation notes

Product implementation is partially complete; see reconciliation notes below for the code/test work already present.

## Architecture review - 2026-06-02

Reviewed design.md against AGENTS.md; META.md and doc/architecture.md are absent in this repo. No new actionable architectural-fit feedback: the design keeps Kaocha changes isolated under src-kaocha, preserves scry.core's no-Kaocha boundary, reuses Kaocha config/selection semantics, remains REPL-first, and preserves the scoped scry result model.

## Ambiguity review - 2026-06-02

Found actionable ambiguities in the proposed Kaocha API/config behavior: the `:suite` plus `:suites` conflict policy is intentionally undecided; suite-id matching is left as exact and/or `name`-based without collision/namespace rules; full `:config` normalization/default responsibility is not pinned down; and the merge/override policy for capture-output, reporter, and color defaults with loaded `tests.edn` is unclear. Added design follow-up items for these points.

## Design follow-up execution - 2026-06-02

Completed all newly added ambiguity-review follow-up items in design.md. Decisions recorded: `:suite` plus `:suites` throws a clear `ex-info`; suite selectors match exact suite ids first, then unique `(name ...)`, with unknown and ambiguous selectors raising `ex-info`; `:config` is treated as already resolved and is not normalized or merged with fallback source/test/ns-pattern options; scry adapter runtime defaults are merged by preserving plugins while ensuring capture-output, and forcing quiet reporter plus color false for all config sources.

## Inconsistency review - 2026-06-02

Found one actionable inconsistency: design.md allows string suite selectors, but its fallback matching rule is phrased in terms of `(name selector)`, while Clojure strings do not support `name`. Added a follow-up to make string selector matching explicit and consistent with the documented selector set.

## Design follow-up execution - 2026-06-02

Completed the newly added inconsistency-review follow-up item in design.md. String selectors now use the string value for fallback text matching; string suite ids also use the string value. Non-string selectors and suite ids use `(name ...)` only when they support it, and values with neither string nor named semantics can only match by exact equality.

## Plan ambiguity review - 2026-06-02

Found actionable ambiguities in the test/verification plan: it does not pin down where Kaocha adapter tests live or which alias combination prevents core `:test` runs from requiring the optional Kaocha dependency, and it does not specify how `tests.edn` loading tests isolate and restore the process working directory instead of reading the repository root. Added follow-up steps for both points.

## Plan follow-up execution - 2026-06-02

Completed the newly added plan-review follow-up items. `plan.md` now records that Kaocha adapter tests will live in `test/scry/kaocha_test.clj`, will be run explicitly with `clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.kaocha-test)"`, and will not be part of core `:test` runs unless the optional `:kaocha` alias is present. It also records the fixture strategy for `tests.edn` loading tests: create temporary project directories, temporarily set `user.dir`, and restore it in `try`/`finally` so repository-root config cannot be read and working-directory changes cannot leak. No blockers.

## Plan inconsistency review - 2026-06-02

Reviewed design.md, plan.md, steps.md, implementation.md, src-kaocha/scry/kaocha.clj, deps.edn, README.md, AGENTS.md, and SKILL.md for cross-task-file inconsistencies. No new actionable inconsistency feedback found: plan and steps consistently preserve the full :config path, pin tests.edn loading versus synthetic fallback precedence, align selector conflict/matching semantics with design.md including string fallback text, keep Kaocha changes under src-kaocha with optional :kaocha tests, and match the documented temporary user.dir isolation strategy.

## Architecture review pass - 2026-06-02

Re-reviewed design.md against AGENTS.md; META.md and doc/architecture.md remain absent. No new actionable architectural-fit feedback beyond the existing architecture review: the design still respects the optional Kaocha boundary, REPL-first API, Kaocha-owned config semantics, and scry scoped result model.

## Ambiguity review pass - 2026-06-02

Found one new actionable ambiguity: `design.md` does not pin down `:suites` edge semantics, especially whether plural `:suites` must be a collection and what an empty collection means. Added a design follow-up item to decide/document this so implementation and tests do not accidentally diverge between treating `[]` as all suites, no suites, or an API error, and between accepting or rejecting scalar `:suites` values.

## Design follow-up execution - 2026-06-02

Completed the newly added ambiguity-review follow-up item in `design.md`. Decision recorded: plural `:suites` must be a non-empty collection of selectors; scalar values, strings, maps, and empty collections are API errors with `ex-info`; callers should use `:suite` for one selector, and an empty collection is not interpreted as all suites or no suites.

## Design inconsistency review pass - 2026-06-02

Found actionable inconsistency feedback: `design.md` now makes `:suite` part of option semantics and conflict handling, but still describes the single-suite form as optional/conditional in the proposed API and acceptance criteria; referenced `src-kaocha/scry/kaocha.clj` also currently diverges from the design's documented `:suites` API-error semantics by accepting scalar `:suites` and treating empty collections as no selection. Added follow-up items to reconcile these before implementation/review completion.

## Design follow-up execution - 2026-06-02

Completed the newly added design-inconsistency follow-up items. `design.md` now treats `:suite` as a supported single-suite convenience form consistently in proposed API and acceptance criteria. `src-kaocha/scry/kaocha.clj` now rejects invalid plural `:suites` values with `ex-info`: scalar selectors, strings, maps, and empty collections are no longer accepted through the plural option; callers must use `:suite` for one selector. No blockers.

## Plan ambiguity review pass - 2026-06-02

Found one new actionable ambiguity: the plan resolves selectors against exact ids first, but then says to pass resolved ids through `kaocha.config/apply-cli-args` if suitable without pinning whether that API preserves non-string/qualified ids or rematches by CLI text. Added follow-up steps to decide/document the application strategy and test that exact namespace-qualified or string ids are not made ambiguous after resolution.

## Plan follow-up execution - 2026-06-02

Completed the newly added plan-ambiguity follow-up items. Inspected `kaocha.config/apply-cli-args` with namespace-qualified keyword ids, symbols, strings, and scalar ids. Decision: after scry selector resolution, pass resolved suite id values directly to `apply-cli-args`; do not stringify them. Direct values preserve exact id equality, while stringifying non-string ids loses type/namespace information and can skip every suite. Added a focused Kaocha adapter test proving selection application preserves exact namespace-qualified keyword ids and exact string ids without rematching by fallback text. No blockers.

## Plan inconsistency review pass - 2026-06-02

Found one new actionable inconsistency: `implementation.md` still opens with "No product implementation yet" and most implementation checklist items in `steps.md` remain unchecked, but `src-kaocha/scry/kaocha.clj` and `test/scry/kaocha_test.clj` already contain suite-selection implementation/test changes referenced by later task notes. Added a follow-up to reconcile the task records so plan/steps/implementation state matches the actual code before further implementation proceeds.

## Review follow-up execution - 2026-06-02

Reconciled task records with already-applied implementation/test work. `src-kaocha/scry/kaocha.clj` already contains config resolution helpers (`tests-edn-exists?`, `load-tests-edn-config`, fallback normalization, `resolve-config`), runtime default merging, suite option validation, selector resolution, and `apply-cli-args`-based selection wired into `run`. `test/scry/kaocha_test.clj` already contains focused coverage proving exact resolved namespace-qualified keyword ids and string ids are passed through selection application without stringification/rematching. Marked corresponding completed checklist items in `steps.md`; left broader end-to-end loaded-config/full-config/fallback, fixture, docs, and verification items unchecked because they are not yet covered by the current code/tests. No blockers.

## Implementation pass - 2026-06-02

Completed broad Kaocha adapter coverage and docs. Added focused `test/scry/kaocha_test.clj` tests for `tests.edn` loading from isolated temporary projects, `:suites`/`:suite` selection, selector validation and errors, full-config preservation, synthetic fallback, runtime defaults, and result formatting. Test namespace now loads Kaocha lazily so core `:test` verification can still load all test namespaces without optional Kaocha dependencies.

Product adjustments made during test hardening: `tests.edn` detection/loading now uses `user.dir` explicitly and absolutizes loaded/fallback paths so temporary project isolation works even though the JVM process cwd is unchanged; selected suites are un-skipped after `apply-cli-args` so repeated in-process runs on normalized configs cannot retain stale skip state; `kaocha.api/run` binds Kaocha's five-key report counters to avoid nil `:pending` counter arithmetic when called from `clojure.test`; `result->scry` ignores skipped leaves in summary/results.

Docs updated in README, AGENTS.md, and SKILL.md with REPL-first `(k/run)`, `:suites`, `:suite`, `:config`, selector semantics, and `tests.edn`/fallback behavior.

Verification:

- `clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.kaocha-test)"` — 8 tests, 48 assertions, 0 failures/errors.
- `clojure -M:test -e "(require '[scry.core :as scry]) (scry/run)"` — pass, 22 tests, 78 assertions.

## Implementation review - 2026-06-02

Reviewed task artifacts, `src-kaocha/scry/kaocha.clj`, `test/scry/kaocha_test.clj`, `deps.edn`, README, AGENTS.md, and SKILL.md against the task design and architecture boundary. Focused Kaocha adapter verification and core scry verification pass. No new actionable implementation-quality issues found.

## Test review - 2026-06-02

Reviewed `design.md`, `plan.md`, `steps.md`, `implementation.md`, `src-kaocha/scry/kaocha.clj`, `test/scry/kaocha_test.clj`, docs, and verification results using the task-test-review criteria. Tests are well formed, cover the specified Kaocha behaviors (tests.edn loading, suite selection/validation/resolution, full-config preservation, fallback config, runtime defaults, result formatting, optional dependency boundary), and use real temporary projects/configs rather than mocks or stubs. Re-ran focused Kaocha adapter tests and core scry verification; both pass. No new actionable test-quality issues found.

## Test-shaper review - 2026-06-02

Reviewed `test/scry/kaocha_test.clj`, `src-kaocha/scry/kaocha.clj`, docs, and task artifacts using the test-shaper criteria: tests are simple, behavior-focused, deterministic via isolated temp projects and `user.dir` restoration, economical across suite-selection partitions, and keep optional Kaocha coverage explicit. Re-ran focused Kaocha adapter tests and core scry verification; both pass. No new actionable test-shaping issues found.

## Docs review - 2026-06-02

Reviewed README.md, AGENTS.md, SKILL.md, CHANGELOG.md, task artifacts, and `src-kaocha/scry/kaocha.clj` using review-task-docs. README/AGENTS/SKILL accurately describe REPL-first Kaocha `tests.edn` loading, `:suite`/`:suites`, full `:config`, selector errors, quiet defaults, and capture-output behavior; no `doc/` directory exists. Found one actionable documentation issue: CHANGELOG.md omits the user-visible Kaocha adapter changes.

## Docs review follow-up execution - 2026-06-02

Completed the newly added docs-review follow-up item. `CHANGELOG.md` Unreleased now records the user-visible Kaocha adapter changes: default `tests.edn` loading with synthetic fallback only when absent, REPL suite selection via `:suite`/`:suites`, selector matching/error semantics, and preservation of supplied full `:config` with scry quiet structured output defaults. No code or test changes were needed for this documentation-only follow-up.

## Docs review pass - 2026-06-02

Reviewed README.md, AGENTS.md, SKILL.md, CHANGELOG.md, task artifacts, and `src-kaocha/scry/kaocha.clj` using review-task-docs after the changelog follow-up. No `doc/` directory exists. User-facing docs are accurate, complete, and consistent for the REPL-first Kaocha API: `tests.edn` loading with fallback, `:suite`/`:suites` selection and selector errors, full `:config` preservation, quiet runtime defaults, scoped result formatting, and capture-output behavior. No new actionable documentation issues found.

## Code-shaper review - 2026-06-02

Reviewed `src-kaocha/scry/kaocha.clj`, `test/scry/kaocha_test.clj`, docs, and task artifacts using the code-shaper criteria. The implementation is locally comprehensible with small helpers for config resolution, suite-selector validation/resolution, runtime defaults, selection application, and result conversion; data shapes and option precedence are consistent with the task design; robustness concerns around optional Kaocha loading, `user.dir` isolation, exact id preservation, skipped suites, and quiet defaults are covered. No new actionable code-shaping issues found.
