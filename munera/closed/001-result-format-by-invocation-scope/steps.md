# Steps

## Slice 1: Baseline and API orientation

- [x] Read `src/scry/capture.clj` and identify the current capture state, report events, and result construction assumptions.
- [x] Read `src/scry/clojure_test.clj` and identify where options are resolved to executable vars.
- [x] Read `src/scry/core.clj` and list helper/reporting assumptions about `:failures`.
- [x] Read `src-kaocha/scry/kaocha.clj` and identify whether its raw data can support the same formatted result model.
- [x] Choose the concrete `:result-format` option shape and write the chosen defaults in implementation notes before coding.

## Slice 2: Scope classification

- [x] Add a function that classifies result scope as `:suite`, `:namespace`, or `:var` from original run options and resolved executable test vars.
- [x] Implement explicit `:vars` precedence over `:namespaces` when executable test vars are present.
- [x] Implement fallback to namespace classification when explicit `:vars` resolves to no executable test vars and an explicit namespace selector remains.
- [x] Ensure discovered single-namespace runs still classify as `:suite` unless exactly one namespace was explicitly requested.
- [x] Add unit tests for scope classification including mixed `:vars`/`:namespaces`, filtered non-test vars, empty var selection, and discovery.

## Slice 3: Capture completeness

- [x] Update `scry.capture/report-fn` so `:pass` events are retained as assertion events for the current var while still incrementing pass counts.
- [x] Preserve fail/error assertion detail including expected, actual, message, file, line, contexts, and error stack traces.
- [x] Add per-var assertion summary data for pass/fail/error counts.
- [x] Add status derivation for every executed var with error taking precedence over fail, then pass, then unknown if needed.
- [x] Update capture tests to verify passing assertion events can be present in detailed results.

## Slice 4: Formatting layer

- [x] Implement default result-format configuration for `:suite`, `:namespace`, and `:var` scopes.
- [x] Implement merging of caller-provided `:result-format` overrides with defaults by scope.
- [x] Decide and document precedence when `:entry-keys` conflicts with `:assertions?` or `:output?` (for example, whether `:assertions? false` removes `:assertions` even if listed in `:entry-keys`, and whether `:output? true` adds `:out`/`:err` when absent from `:entry-keys`).
  - Done in plan: inclusion booleans are authoritative semantic gates applied after key projection.
- [x] Decide and document compatibility behavior when custom `:top-level-keys` omits `:results` and/or `:failures` (including what `scry.core/failures`, `failed-test`, and `report-string` should read in that case).
  - Done in plan: top-level projection is respected; helpers prefer `:failures`, fall back to filtering `:results`, then return documented nil/empty values if both collections are omitted.
- [x] Build canonical `:results` entries from captured per-var data before applying projection.
- [x] Format suite/multi defaults with aggregate counts, no output keys, no per-assertion details, and compact failing/erroring entries by default.
- [x] Format single namespace defaults with every executed var, all assertion details, and no output keys.
- [x] Format single var defaults with the executed var, all assertion details, and stdout/stderr keys.
- [x] Ensure `:failures`, when included by the selected/defaulted `:top-level-keys`, remains a filtered failing/erroring compatibility collection independent of whether `:results` is also included.
- [x] Reconcile the `:failures` formatting step with the plan's custom `:top-level-keys` behavior: only include `:failures` when requested/defaulted, not merely whenever `:results` is present.
  - Done by updating the Slice 4 compatibility step to respect selected/defaulted `:top-level-keys`.
- [x] Support configurable top-level keys per scope.
- [x] Support configurable per-entry keys per scope.
- [x] Support configurable assertion inclusion per scope.
- [x] Support configurable stdout/stderr inclusion per scope.

## Slice 5: Public helper compatibility

- [x] Update `scry.core/failures` to return compatibility failure entries from the current result shape.
- [x] Update `scry.core/failed-test` to find failing/erroring entries reliably after scoped formatting.
- [x] Update `scry.core/output` to return output when present and a documented nil/empty result when the selected format omits output.
- [x] Update `scry.core/report-string` so it renders failures from the compatibility collection or canonical results without assuming assertion/output keys always exist.
- [x] Add tests for `last-result`, `failures`, `failed-test`, `output`, and `report-string` against the new scoped defaults.

## Slice 6: Kaocha adapter alignment

- [x] Attempt to route Kaocha adapter output through the shared formatter using the same default/custom format options.
- [x] Preserve Kaocha's existing top-level summary/pass/failure behavior during migration.
- [x] Add tests or documentation for any Kaocha limitation around passing assertion detail or stdout/stderr separation.

## Slice 7: Behavior tests

- [x] Add default broad/discovered run test asserting aggregate assertion counts and no stdout/stderr keys in result entries.
- [x] Add default multiple-namespace run test asserting aggregate assertion counts and no stdout/stderr keys in result entries.
- [x] Add default single-namespace run test asserting all executed vars and all assertion details, including passing assertions, are returned without output keys.
- [x] Add default single-var run test asserting one entry with all assertion details and stdout/stderr keys.
- [x] Add custom suite-scope formatting test.
- [x] Add custom namespace-scope formatting test.
- [x] Add custom var-scope formatting test.
- [x] Update existing tests that asserted only the old `:failures` shape while preserving backward-compatible expectations where required.

## Slice 8: Documentation

- [x] Update README result-shape documentation to make `:results` canonical and explain scoped defaults.
- [x] Update README examples for broad, namespace, var, and custom `:result-format` usage.
- [x] Update AGENTS.md with the new result map expectations and testing guidance.
- [x] Update SKILL.md with scoped result behavior and recommended inspection workflow.

## Slice 9: Verification and notes

- [x] Run the full Clojure test suite.
- [x] Run or document Kaocha adapter verification if Kaocha dependencies are available.
- [x] Append final implementation decisions, trade-offs, and any limitations to `implementation.md`.
- [x] Mark completed checklist items in this file as implementation progresses.


## Implementation review follow-ups

- [x] Fix scope classification when explicit `:vars` resolves to no executable tests but `:namespaces` supplies fallback vars; classify by namespace selector intent, not the fallback executable var count.

## Docs review follow-ups

- [x] Add a `CHANGELOG.md` entry/file documenting the user-visible scoped result-shape/API behavior change.
