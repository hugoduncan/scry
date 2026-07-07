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

- 2026-07-07 completion slice: added a focused `-X` regression proving result-file serialization failures preserve the test-derived non-zero outcome data (`:summary`, `:result-files`, `:scry.cli/outcome-kind`, and top-level `:scry.cli/diagnostic-error`) without adding duplicate summary text fields. Documented bounded result-file placeholders, bounded Throwable normalization, and post-run diagnostic-error fallback in `README.md`. No AGENTS or mementum/state update was needed because workflow/structure did not change. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (56 tests, 443 assertions) and `clojure -M:test -m scry.cli` (114 tests, 741 assertions).

- implementation review: added 3 steps to be addressed.

- 2026-07-07 review follow-up slice: addressed 3 implementation-review steps. Sanitizer identity tracking is now path-scoped so repeated shared objects in separate branches serialize normally while recursive cycles still emit `{:scry/cycle true :class "..."}`. Non-EDN placeholder `:str` values now use configured string bounding, including hostile/large `toString` output. Added direct regressions for shared non-cycle identities and bounded non-EDN placeholder strings. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (56 tests, 441 assertions) and `clojure -M:test -m scry.cli` (114 tests, 739 assertions).

- implementation review: added 2 steps to be addressed.

- 2026-07-07 review follow-up slice: addressed 2 immediately preceding implementation-review steps. Moved Throwable bounded-shape assertions back inside `edn-readable-data-bounds-pathological-values-test`; Throwable cycle tracking is now path-scoped via `finally` removal, with regression coverage for repeated shared Throwable identities in separate branches. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (56 tests, 448 assertions).

- implementation review: added 2 steps to be addressed.

- 2026-07-07 review follow-up slice: addressed 2 immediately preceding implementation-review steps. CLI fallback diagnostic root-cause traversal is now cycle-safe and bounded for serialization exceptions and first failing Throwable assertions; top-level diagnostic-error string fields and matching stderr fallback root-cause text are bounded. Added regression coverage for cyclic/deep diagnostic cause chains and hostile long messages. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (57 tests, 457 assertions).

- implementation review: added 1 step to be addressed.

- 2026-07-07 review follow-up slice: addressed 1 immediately preceding implementation-review step. `assertion-cause-text` now bounds map-shaped `:actual` root-cause extraction without unbounded `(last (:via actual))`, tolerates cyclic/infinite or hostile/non-sequential `:via`, and bounds the formatted map-shaped cause text. Added regression coverage where result-file writing fails while the first failing assertion has a pathological map-shaped `:actual`. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (57 tests, 463 assertions).

- implementation review: added 2 steps to be addressed.

- 2026-07-07 review follow-up slice: addressed 2 immediately preceding implementation-review steps. Added a true end-to-end CLI regression that runs `scry.fixtures.pathological` through the real clojure-test runner and verifies cyclic assertion actuals plus cyclic Throwable ex-data survive capture, canonical result construction, CLI classification, and result-file serialization. Adjusted the cyclic ex-data fixture to wrap an `IExceptionInfo` cause so clojure.test does not overflow while printing the raw error before scry sanitization. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (60 tests, 473 assertions) and `clojure -M:test -m scry.cli` (116 tests, 771 assertions).

- test review: added 3 steps to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 3 immediately preceding test-review steps. Added direct sanitizer regressions for configured Throwable cause-depth truncation and suppressed-exception count capping, and strengthened the forced result-file write failure regression to prove the normal stdout summary is already written before result-file serialization is attempted. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (60 tests, 479 assertions).

- test review: added 2 steps to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 2 immediately preceding test-review steps. Added an injectable `:write-result-files` boundary to `scry.cli` and converted diagnostic fallback tests away from global `with-redefs`; added a subprocess regression that invokes the actual `-m scry.cli` entrypoint against `scry.fixtures.pathological`, verifies non-zero exit, normal summary output, result-file placeholders/root message, and no primary `StackOverflowError`. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (61 tests, 485 assertions).
- final core CLI verification passed after follow-ups: `clojure -M:test -m scry.cli` (117 tests, 783 assertions).
- post-format verification passed: focused CLI test command (61 tests, 485 assertions) and `clojure -M:test -m scry.cli` (117 tests, 783 assertions).

- test review: added 1 step to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 1 immediately preceding review step. Moved the map-shaped assertion actual regression into `map-shaped-assertion-actual-via-is-bounded-test` so it runs as an isolated clojure.test test instead of at namespace load time. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (62 tests, 491 assertions).

- test review: added 1 step to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 1 immediately preceding review step. Added focused diagnostic-write-failure coverage for a synthetic load-error outcome, proving the fallback preserves `:scry.cli/load-error`, attaches bounded `:scry.cli/diagnostic-error`, leaves result files empty, and still emits load-error stderr detail plus result-directory pointer semantics. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (63 tests, 507 assertions) and `clojure -M:test -m scry.cli` (119 tests, 805 assertions).

- test review: added 1 step to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 1 immediately preceding review step. Added focused diagnostic-write-failure coverage for a synthetic unknown-result outcome, proving the fallback preserves `:scry.cli/unknown-result`, attaches bounded `:scry.cli/diagnostic-error`, leaves result files empty, and still emits unknown-result stderr label plus result-directory pointer semantics. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (64 tests, 519 assertions) and `clojure -M:test -m scry.cli` (120 tests, 817 assertions).

- test review: added 1 step to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 1 immediately preceding review step. Added focused diagnostic-write-failure coverage for a passing outcome, proving a post-run result-file write failure preserves exit code 0 and `:scry.cli/pass`, attaches bounded top-level `:scry.cli/diagnostic-error`, and leaves result files empty without writing the failure-details pointer. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (66 tests, 533 assertions) and `clojure -M:test -m scry.cli` (121 tests, 831 assertions).

- test review: added 2 steps to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 2 immediately preceding review steps. Added focused diagnostic-write-failure coverage for a zero-tests outcome, proving fallback preserves non-zero `:scry.cli/zero-tests`, attaches bounded diagnostic metadata, leaves result files empty, and omits failure-details pointer text. Added direct sanitizer coverage for Throwable ex-data depth limiting with configured `:max-ex-data-depth`, proving ex-data truncates independently of outer data depth. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (67 tests, 546 assertions) and `clojure -M:test -m scry.cli` (122 tests, 844 assertions).

- test review: added 2 steps to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 2 immediately preceding review steps. Collection sanitizer `:max-seq-length` truncation now emits explicit `{:scry/truncated :max-seq-length}` sentinels for sequential/vector/set/map/Java Map/array/Iterable shapes, with direct coverage for each supported collection family. Added direct Throwable normalization coverage locking the documented frame map shape for `:at` and `:trace`. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (69 tests, 561 assertions) and `clojure -M:test -m scry.cli` (124 tests, 859 assertions).

- test review: added 2 new steps to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 2 immediately preceding review steps. Added direct sanitizer regressions for cyclic Throwable cause chains and collection truncation sentinel collisions for set/Iterable and map shapes containing `{:scry/truncated :max-seq-length}` as user data. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (71 tests, 566 assertions).

- test review: added 1 step to be addressed.

- 2026-07-07 test-review follow-up slice: addressed 1 immediately preceding review step. Strengthened the set truncation sentinel collision regression so the user-supplied sentinel is outside the retained prefix, proving the sanitizer appends its own explicit truncation sentinel for sets. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (71 tests, 567 assertions).

- implementation review: added 1 step to be addressed.

- 2026-07-07 implementation-review follow-up slice: addressed 1 immediately preceding review step. Made CLI diagnostic fallback string coercion hostile-safe by catching throwing `toString` in bounded diagnostic strings and using bounded coercion while composing map-shaped assertion root-cause text/load-error details. Added focused regression where result-file writing fails and the first failing assertion contains message/root-cause values whose `toString` throws, proving the outcome remains test-derived instead of `:scry.cli/runner-error`. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (71 tests, 572 assertions) and `clojure -M:test -m scry.cli` (126 tests, 870 assertions).

- implementation review: no new feedback.

- test review: no new feedback.

- test-shaper review: added 1 step to be addressed.

- 2026-07-07 test-shaper follow-up slice: addressed 1 immediately preceding review step. Added a true persistent-set truncation sentinel collision regression for `edn-readable-data` using a sorted persistent set, while keeping separate generic `Iterable` coverage. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (71 tests, 574 assertions).
- test-shaper review: added 2 steps to be addressed.

- 2026-07-07 test-shaper follow-up slice: addressed 2 immediately preceding review steps. Added hostile Java Map/Iterable sanitizer boundary regressions and made sanitizer collection traversal fall back to bounded non-EDN placeholders when collection iteration/access throws. Added a run-with-boundary/-X-path pathological fixture regression through the real clojure-test runner, proving structured non-zero ex-data preserves `:scry.cli/test-failure` with bounded result files and no diagnostic fallback. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (75 tests, 594 assertions) and `clojure -M:test -m scry.cli` (128 tests, 892 assertions).

- test-shaper review: added 2 steps to be addressed.

- 2026-07-07 test-shaper follow-up slice: addressed 2 immediately preceding review steps. Added hostile Throwable accessor sanitizer regressions and bounded placeholder handling for throwing Throwable accessors. Reworked collection truncation to detect max-seq-length overflow during a single iterator pass and added a one-shot Iterable regression. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (76 tests, 602 assertions) and `clojure -M:test -m scry.cli` (129 tests, 900 assertions).

- test-shaper review: added 1 step to be addressed.

- 2026-07-07 test-shaper follow-up slice: addressed 1 immediately preceding review step. Made CLI diagnostic fallback root-cause/message traversal tolerate hostile Throwable `getCause`/`getMessage` accessors and added focused diagnostic-write-failure coverage proving a hostile Throwable assertion `:actual` preserves `:scry.cli/test-failure` instead of becoming `:scry.cli/runner-error`. Verification passed: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"` (76 tests, 607 assertions) and `clojure -M:test -m scry.cli` (129 tests, 905 assertions).

- test-shaper review: no new feedback.
