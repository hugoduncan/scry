# Steps

## Slice 1: Baseline and characterization

- [x] Run the focused core slice to capture the pre-refactor baseline for capture/runner behavior.
- [x] Inspect the current `clojure.test/test-vars` and fixture execution path used by the project Clojure version.
- [x] Decide the smallest fixture-preserving per-var output strategy: reproduce the `test-vars` fixture loop locally or keep `test-vars` with stack-aware routing.
- [x] Add focused core regression tests, committed with the implementation that makes them pass, demonstrating that a nested `scry.clojure-test/run` does not leak inner var entries, assertions, failures, or output into the outer run.
- [x] Add focused core tests for raw non-owned nested `clojure.test` var execution being ignored by the enclosing scry capture.
- [x] Add focused core tests for per-var stdout/stderr isolation in multi-var runs.
- [x] Add focused core tests for fixture output ownership: `:each` output belongs to its var, and `:once` output is omitted from public var entries.

## Slice 2: Capture context primitives

- [x] Add a capture context constructor that wraps a fresh state atom and accepts intended vars/run metadata.
- [x] Add a dynamic current capture context var in `scry.capture` with helpers to bind a context and to run with capture disabled.
- [x] Update `report-fn` so it can be installed once but dispatches report events through the current dynamic context at event time.
- [x] Update `routing-writer` so it dispatches writes through the current dynamic context at write time and uses the captured fallback writer when no context is active.
- [x] Preserve compatibility for existing direct state-based capture tests by adapting `new-state`, `report-fn`, `routing-writer`, `build-result`, `current-var-result`, and `orphan-output` as needed.
- [x] Add or update capture unit tests for nil/disabled context semantics: report events are ignored and output is not appended to an enclosing capture state.

## Slice 3: Owned and ignored frame semantics

- [x] Store an intended-var allow-list in each core capture context.
- [x] Add state fields for an owned/ignored frame stack and current output owner without breaking result construction.
- [x] Make `:begin-test-var` for an intended var open an owned frame, initialize/resume that var entry, increment owned test count, and make the var the current output owner.
- [x] Make `:end-test-var` for an owned frame close that frame and restore the previous owned output owner.
- [x] Make `:begin-test-var` for a non-intended var open an ignored frame.
- [x] Make assertions and `:end-test-var` events inside ignored frames omit public result/count changes for the enclosing run.
- [x] Make stdout/stderr writes while an ignored frame is active omit public var output and, if retained at all, store only non-public orphan/ignored output.
- [x] Ensure `current-var-result` returns the just-ending owned var entry in the window needed by progress callbacks.

## Slice 4: Core runner integration and per-var output

- [x] Update `scry.clojure-test/run` to resolve executable vars, create a fresh capture context with that allow-list, and bind it during test execution.
- [x] Keep `clojure.test/report`, `test/*testing-contexts*`, and `test/*testing-vars*` reset/bound for each run as they are today.
- [x] Implement the selected fixture-preserving per-var output strategy so test body output and `:each` fixture setup/teardown output are captured in the owning var entry.
- [x] Ensure `:once` fixture setup/teardown output stays outside public var results.
- [x] Preserve existing result scope classification and `:result-format` behavior.
- [x] Preserve CLI progress callback invocation order and canonical entry shape for core runs.
- [x] Run focused core capture/runner tests and fix regressions.

## Slice 5: Nested core-run coverage

- [x] Add fixture namespaces or local test vars needed to exercise nested `scry.clojure-test/run` inside an outer test var.
- [x] Verify the nested inner result map still contains its own failures/errors/output for the outer test to assert against.
- [x] Verify the outer result captures only the outer test var's assertions and explicit output.
- [x] Verify inner nested stdout/stderr does not appear in the outer var's `:out`/`:err` unless printed by the outer test.
- [x] Verify raw nested non-owned `clojure.test` execution does not change the outer summary counts, failures, or public results.

## Slice 6: Kaocha adapter isolation

- [x] Update `src-kaocha/scry/kaocha.clj` so `kaocha.api/run` executes inside `scry.capture/without-context`.
- [x] Add optional Kaocha coverage where an outer focused `scry/run` invokes `scry.kaocha/run` over a generated fixture with an intentional failure.
- [x] Verify the outer `scry/run` records only the outer adapter-invoking var and its assertions.
- [x] Verify the inner Kaocha adapter result still reports the generated fixture failure in its own returned `:results`/`:failures`.
- [x] Verify optional Kaocha CLI progress/result-file behavior remains live and unchanged.

## Slice 7: CLI and formatting regression

- [x] Run focused core CLI tests for progress output, `.scry-results/` lifecycle, EDN result files, exit behavior, and parser normalization.
- [x] Run focused optional Kaocha CLI tests for progress and result-file behavior.
- [x] Run focused namespace/var scope checks to confirm public result maps remain stable.
- [x] Run focused build/release slices only if touched by the implementation.

## Slice 8: Documentation and final verification

- [x] Record the same-thread/cooperative dynamic-binding boundary and non-cooperative raw/parallel limitations in `implementation.md`.
- [x] Update `AGENTS.md` if the repository verification workflow or documented nested-run limitation changes.
- [x] Update `README.md` or `CHANGELOG.md` only if user-facing behavior or public API documentation changes.
- [x] Run final core command-line verification: `clojure -M:test -m scry.cli`.
- [x] Run final optional Kaocha verification when Kaocha code/tests changed: `clojure -M:test:kaocha -m scry.cli --runner kaocha` or the focused documented Kaocha commands.
- [x] Record all final verification commands and results in `implementation.md`.

## Review follow-up: plan ambiguity

- [x] Specify the compatibility API semantics for refactored capture helpers, especially accepted `report-fn` and `routing-writer` arities and whether state/context arguments are adapters around the dynamic context or legacy state-closing behavior.
- [x] Clarify the per-var output-owner mechanism for `:each` fixture setup/teardown: how output is attributed to the var before `clojure.test/test-var` emits `:begin-test-var` and after it emits `:end-test-var`, without creating duplicate test counts or progress entries.
- [x] Decide how the baseline characterization tests that are expected to fail before the refactor are introduced, run, and committed so the repository is not left with intentionally failing tests between slices.

## Review follow-up: plan inconsistency

- [x] Align Slice 1 and related test-addition steps with the characterization/red-test workflow: remove or rewrite the "add focused failing tests" wording so any red-first checks are explicitly temporary/REPL-only, and committed regression tests are added with the implementation that makes them pass.

## Review follow-up: implementation

- [x] Move core `:progress-callback` invocation outside the per-var output-owner/routing-writer capture window, or otherwise ensure callback writes to `*out*`/`*err*` are not appended to the just-completed test var's public `:out`/`:err` buffers while preserving inclusion of `:each` teardown output in the progress entry.

## Review follow-up: implementation second pass

- [x] Restore normal fixture assertion semantics for capture contexts: `:each` fixture assertions before/after `clojure.test/test-var` should be attributed to the owning var and make the run fail when they fail; handle `:once` fixture assertions consistently with the existing result model, and add regression coverage.
- [x] Fix `routing-writer` handling of Java's `Writer.write(String, off, len)` overload so substring writes route correctly instead of throwing `ClassCastException`, and add focused coverage.

## Review follow-up: implementation third pass

- [x] Make CLI outcome summary and exit-code account for aggregate runner `:summary`/`:pass?` failures or errors that are not attached to a canonical public var entry (for example `:once` fixture assertions), with focused regression coverage, while preserving per-var progress and result-file behavior.

## Review follow-up: implementation fourth pass

- [x] Update AGENTS.md Kaocha REPL-slice guidance so it no longer claims an outer `scry/run` would capture intentional nested Kaocha reports as outer failures after this task's replaceable/disabled capture implementation; keep the focused `clojure.test/run-tests` recommendation if still desired for deterministic optional Kaocha verification.

## Review follow-up: implementation fifth pass

- [x] Prevent raw nested non-owned `clojure.test/test-vars` namespace-level fixture output/assertions (especially `:once` setup/teardown before/after inner `:begin-test-var`) from inheriting the enclosing var output owner or being recorded as outer failures/counts; add focused regression coverage for non-owned nested vars with fixtures.

## Review follow-up: implementation sixth pass

- [x] Move intentionally failing raw-nested helper `deftest`s out of `scry.clojure-test-test`'s ordinary executable test set (for example into fixture namespaces or non-test helper vars) so `clojure.test/run-tests 'scry.clojure-test-test` passes while the scry acceptance wrappers still prove nested raw execution isolation.

## Review follow-up: implementation seventh pass

- [x] Keep all events inside an ignored non-owned raw `clojure.test` frame ignored, even if a nested event names a var that is allow-listed for the enclosing scry run; add focused regression coverage where a non-owned raw `test-var` invokes an allow-listed helper var and verify helper assertions/output/counts do not leak into the outer result.

## Review follow-up: implementation eighth pass

- [x] Preserve normal `:each` fixture short-circuit semantics: when an `:each` fixture does not invoke its supplied test function, do not create a public `:unknown` var entry or count/progress/result-file it merely because output ownership was pre-established; keep any fixture output non-public/orphan and add focused regression coverage.

## Review follow-up: implementation ninth pass

- [x] Update README.md core runner wording so it no longer claims direct delegation to `clojure.test/test-vars`; describe the local fixture-preserving execution loop/per-var output ownership while keeping the promise that normal `:once` and `:each` fixture semantics are preserved.

## Review follow-up: implementation tenth pass

- [x] Preserve raw nested `clojure.test/run-tests` / `test-vars` inner runner semantics while suppressing outer scry capture: when non-owned raw nested clojure.test execution is isolated, its own `clojure.test` assertion counters/result summary should still report pass/fail/error counts correctly, and focused regression coverage should prove those inner counts do not leak into the outer scry result.

## Review follow-up: implementation eleventh pass

- [x] Preserve singular raw nested `clojure.test/test-var` inner runner semantics while suppressing outer scry capture: when a non-owned raw `test-var` is invoked inside an active scry run, its own `clojure.test` assertion counters should still report pass/fail/error counts correctly, and focused regression coverage should prove those inner counts do not leak into the outer scry result or output.

## Review follow-up: test review

- [x] Make `fixture-assertion-ownership-test` restore all `scry.fixtures.asserting-fixtures/*-pass?` atoms after it mutates them, so running that test alone cannot leave fixture state contaminated for later checks.

## Review follow-up: test review second pass

- [x] Make `run-cli-run-level-fixture-failures-test` snapshot and restore all `scry.fixtures.asserting-fixtures/*-pass?` atoms in a `finally`, instead of resetting them to hard-coded `true` values, so focused REPL/debug runs cannot change pre-existing fixture state.

## Review follow-up: test review third pass

- [x] Add focused coverage/assertions proving raw non-owned nested `clojure.test/test-var` stdout/stderr do not leak into the outer scry result; the current singular test-var counter case uses a noisy failing var but only checks outer `:out`, not outer `:err`.
## Review follow-up: test review fourth pass

- [x] Update the stale `fixtures-are-honoured-test` comment so it no longer claims fixture execution is delegated to `clojure.test/test-vars`; it should describe the local fixture-preserving loop around `clojure.test/test-var` while preserving the same fixture-order assertion.

## Review follow-up: docs review

- [x] Update `CHANGELOG.md` Unreleased to mention the user-visible nested/reentrant capture fix: nested `scry`, optional Kaocha, and raw `clojure.test` runs no longer leak inner events/output into the enclosing scry result while preserving inner raw `clojure.test` counters.
- [x] Update `README.md` (or another appropriate user-facing doc if preferred) to document nested in-process runner capture isolation and the remaining cooperative/same-thread limitations at a user-facing level.
- [x] Update top-level `SKILL.md` so its agent rules no longer claim `scry.clojure-test` delegates execution to `clojure.test/test-vars`; describe the local fixture-preserving loop around `clojure.test/test-var` while preserving normal fixture semantics.
