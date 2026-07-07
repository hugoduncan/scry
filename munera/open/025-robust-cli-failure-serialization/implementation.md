# Implementation Notes

- architectural review: AGENTS.md architecture context loaded; requested META.md and doc/architecture.md are not present in this checkout.
- no architectural review feedback
- ambiguity review added 4 new design steps
- no inconsistency review feedback
- design-step follow-up guidance: preserve the existing CLI contract from `AGENTS.md`/`src/scry/cli.clj` that `:scry.cli/outcome-kind` is the authoritative machine signal and human stdout/stderr diagnostics are supplementary; resolve wording so diagnostic serialization failures are additive metadata, not a scope change to normal test outcome semantics. Relevant implementation files: `src/scry/cli.clj`, `src/scry/cli/results.clj`, `test/scry/cli_test.clj`.
- design follow-up completed: `:scry.cli/diagnostic-error` is additive top-level outcome metadata, not an outcome-kind; entries+summary collection is the boundary after which diagnostic failures preserve the test-derived outcome. Result-file writing should move after normal summary emission to satisfy the summary-before-diagnostics requirement.
- no ambiguity review feedback
- review-slice handoff: when addressing design-step fallout, maintain the boundary that diagnostic serialization is a post-run CLI concern; consult `AGENTS.md` for the CLI contract and `src/scry/cli.clj` / `src/scry/cli/results.clj` for implementation behavior.
- ambiguity review added 3 new design steps: pin sanitizer truncation/string representation, cycle-detection path-vs-global semantics, and controlled Throwable frame shape before implementation tests lock in accidental choices.
- plan-review inconsistency pass: no new feedback
- design-step resolution guidance: keep sanitizer and Throwable shapes stable enough for CLI result-file assertions but avoid expanding public API beyond documented placeholders/diagnostic metadata; resolve ambiguities against existing `src/scry/cli/results.clj` file-writing needs and `test/scry/cli_test.clj` expectations, with `src/scry/cli.clj` preserving outcome/summary ordering.
- plan-follow-up scan: latest plan-review batch identified as commits `f6f388b`..`55044ba` with baseline `63f92f0`; `git diff 63f92f0..HEAD -- munera/open/025-robust-cli-failure-serialization/steps.md` added no checklist lines, so there are no attributed unchecked `steps.md` follow-up items to execute.

- plan-review ambiguity pass: no new feedback

- shared plan-review inconsistency turn: no new feedback
- design-steps handoff: resolve the remaining sanitizer/Throwable-shape questions before coding assertions; prefer explicit bounded sentinel data that is stable in `.scry-results/*.edn` but do not broaden public API beyond documented placeholders and top-level diagnostic metadata. Relevant files: `src/scry/cli/results.clj` for representation choices, `src/scry/cli.clj` for outcome/summary boundaries, `test/scry/cli_test.clj` for CLI contract regressions.

- 2026-07-07 implementation slice: `scry.cli.results/edn-readable-data` now accepts sanitizer options and emits bounded EDN placeholders for cycles, max-depth, capped collections, strings, non-EDN objects, and controlled Throwable shapes (`:type`, `:message`, `:at`, `:trace`, `:cause`, `:suppressed`, `:data`). `scry.cli/run-cli` now classifies and writes the normal summary before result-file writing; result-file write failures attach top-level `:scry.cli/diagnostic-error` and preserve the test-derived outcome. Added CLI tests for sanitizer bounds, pathological cyclic failure data, and forced diagnostic write failure. Verification: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` passed (54 tests, 431 assertions).
