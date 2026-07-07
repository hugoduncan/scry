# Steps

## Slice 1 — Baseline orientation and characterization

- [x] Inspect `src/scry/cli.clj` to map the current `run-cli`, `classify-outcome`, summary writing, result-file writing, and outer `catch Throwable` flow.
- [x] Inspect `src/scry/cli/results.clj` to map current `edn-readable-data`, `throwable-data`, object, array, and file-writing behavior.
- [x] Inspect existing CLI tests in `test/scry/cli_test.clj` for result-file, stderr/stdout, `-X`, and outcome-kind expectations.
- [x] Add or identify a fixture/test path that can run a failing assertion with cyclic actual data through the CLI.
- [x] Add or identify a fixture/test path that can run an erroring test with cyclic `ex-data` through the CLI.

## Slice 2 — Robust EDN sanitizer

- [x] Define default sanitizer limits for maximum depth, maximum sequence length, Throwable cause depth, stack frames, suppressed exceptions, ex-data depth, and string length.
- [x] Change `edn-readable-data` to accept and propagate an options map containing limits and an `IdentityHashMap` for seen object identities.
- [x] Emit `{:scry/truncated :max-depth}` when maximum depth is exceeded.
- [x] Emit `{:scry/cycle true :class "..."}` when an identity cycle is detected.
- [x] Emit `{:scry/non-edn-class "..." :str "..."}` for values that cannot be represented directly as EDN.
- [x] Cap sequential and collection values at `:max-seq-length` while preserving deterministic, readable EDN output.
- [x] Bound long strings to the configured maximum length with an explicit truncation marker or bounded value.
- [x] Add direct unit tests for depth truncation, sequence truncation, identity cycles, non-EDN placeholders, and string bounding.

## Slice 3 — Controlled Throwable normalization

- [x] Replace arbitrary Throwable walking with bounded Throwable normalization returning `:type`, `:message`, `:at`, `:trace`, `:cause`, and `:suppressed` where available.
- [x] Represent Throwable types as class symbols and keep messages bounded.
- [x] Limit stack trace frames to the configured maximum.
- [x] Limit cause-chain traversal to the configured maximum and avoid cause cycles.
- [x] Limit suppressed exceptions to the configured maximum count.
- [x] Normalize `ex-data` with a smaller bounded sanitizer depth and identity-cycle protection.
- [x] Add direct unit tests for cause-depth truncation, stack-frame caps, suppressed caps, cyclic ex-data, and preserved root-cause type/message.

## Slice 4 — Non-authoritative diagnostic writing

- [x] Refactor `run-cli` so canonical entries and summary are collected before result-file serialization.
- [x] Compute `:scry.cli/outcome-kind` from entries and summary before result-file serialization.
- [x] Write the normal CLI summary to stdout immediately after outcome classification and before result-file writing.
- [x] Wrap `write-result-files!` in a focused diagnostic failure boundary separate from the outer runner-error catch.
- [x] On result-file-writing failure, return an empty result-file vector and attach top-level `:scry.cli/diagnostic-error` without changing `:scry.cli/outcome-kind`.
- [x] Build `:scry.cli/diagnostic-error` with required keys `:phase`, `:message`, `:type`, `:root-type`, `:root-message`, `:failed-entry-count`, `:first-failing-var`, and `:first-root-cause` when derivable.
- [x] Emit concise fallback stderr diagnostics for result-file-writing failures, including failed entry count and first failing var/root cause when derivable.
- [x] Preserve existing runner-error behavior for exceptions raised before entries and summary are collected.
- [x] Add focused tests for a forced `write-result-files!` failure verifying test-derived outcome, empty result-file vector, diagnostic-error shape, summary output, and stderr fallback.

## Slice 5 — End-to-end CLI regressions

- [x] Add an end-to-end CLI regression for cyclic assertion actual data verifying non-zero exit, `:scry.cli/test-failure`, summary output, no primary `StackOverflowError`, and cycle/truncation placeholders in failure EDN or fallback diagnostics.
- [x] Add an end-to-end CLI regression for cyclic Throwable ex-data verifying non-zero exit, `:scry.cli/test-failure`, summary output, no primary `StackOverflowError`, preserved root-cause message, and cycle/truncation placeholders in failure EDN or fallback diagnostics.
- [x] Verify `-X` CLI return maps include the same `:summary`, `:result-files`, `:scry.cli/outcome-kind`, and optional `:scry.cli/diagnostic-error` semantics without duplicate summary fields.
- [x] Verify normal passing and normal failing CLI tests still preserve existing result-file and diagnostic behavior.

## Slice 6 — Documentation and verification

- [x] Update README.md if the additive `:scry.cli/diagnostic-error` key or sanitizer placeholders need public documentation.
- [x] Update AGENTS.md only if development workflow, conventions, or required verification commands change.
  - No workflow/convention changes were needed.
- [x] Update `mementum/state.md` only if the project feature/structure snapshot changes materially.
  - No feature/structure snapshot update was needed for this task-local CLI behavior refinement.
- [x] Run focused REPL or command-line tests while iterating and record useful discoveries in `implementation.md`.
- [x] Run final focused CLI verification: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"`.
- [x] Run final core command-line verification if runner/capture/API behavior changed: `clojure -M:test -m scry.cli`.
- [x] Record final verification commands and results in `implementation.md`.

## Implementation review follow-ups

- [x] Change sanitizer cycle detection to be path-scoped, not globally sticky, so repeated shared object identities in separate branches are serialized normally while true recursive cycles still emit `{:scry/cycle true :class "..."}`.
- [x] Apply configured string bounding to `:scry/non-edn-class` placeholder `:str` values, including hostile or very large `toString` output.
- [x] Add regression tests for repeated shared object identities that are not cycles and for bounded non-EDN placeholder strings.
- [x] Move the Throwable bounded-shape assertions in `test/scry/cli_test.clj` back inside `edn-readable-data-bounds-pathological-values-test` (or another `deftest`) so they run under `clojure.test` instead of as top-level load-time assertions.
- [x] Make Throwable cycle tracking path-scoped like the general sanitizer identity tracking, and add a regression for repeated shared Throwable identities in separate branches so non-recursive sharing is not serialized as a cycle.
- [x] Make CLI diagnostic fallback root-cause traversal cycle-safe/bounded (`root-cause-throwable`, `throwable-cause-text`, and `assertion-cause-text`) so a diagnostic-write failure cannot hang or overflow when the serialization exception or first failing Throwable has a cyclic/deep cause chain.
- [x] Bound all string fields in top-level `:scry.cli/diagnostic-error` and the matching stderr fallback (`:message`, `:root-message`, and `:first-root-cause`) so hostile/large exception messages or assertion actuals cannot produce unbounded structured outcomes or fallback output.
- [x] Make `assertion-cause-text` bounded for map-shaped `:actual` values: avoid unbounded `(last (:via actual))` traversal, tolerate cyclic/hostile/non-sequential `:via`, and add a regression where result-file writing fails while the first failing assertion has a pathological map-shaped `:actual`.
- [x] Add a true end-to-end CLI regression that runs the pathological fixture namespace/vars through the real clojure-test runner instead of a synthetic `runner-result`, so cyclic assertion actuals and cyclic `ex-data` are proven safe across capture, canonical result construction, CLI classification, and result-file serialization together.
- [x] Re-run the final core CLI verification after the latest diagnostic-bound changes (`clojure -M:test -m scry.cli`) and record the result in `implementation.md`.

## Test review follow-ups

- [x] Add direct unit tests for Throwable cause-depth truncation using a configured small `:max-throwable-depth`, asserting the bounded sentinel shape instead of only incidental root-message preservation.
- [x] Add direct unit tests for Throwable suppressed-exception count capping using a configured small `:max-suppressed`, asserting excess suppressed exceptions are omitted or otherwise bounded as intended.
- [x] Add a focused CLI regression proving the normal summary is written before result-file serialization is attempted, e.g. by observing the stdout writer inside a forced `write-result-files!` failure boundary.
- [x] Replace or augment `with-redefs`-based result-file writer failure tests with an injectable diagnostic writer/file-writing failure boundary, so the diagnostic fallback tests do not depend on global stubbing.
- [x] Add a subprocess-level CLI regression for the pathological fixture namespace/vars that verifies the actual `-m scry.cli` entrypoint exits non-zero, prints the normal summary, preserves `:scry.cli/test-failure` in structured output/files where observable, and does not surface `StackOverflowError` as the primary failure.
- [x] Move the map-shaped assertion actual regression in `test/scry/cli_test.clj` inside a `deftest`; it is currently a top-level `(testing ...)` form, so its assertions run at namespace load time rather than as an isolated clojure.test test.
- [x] Add focused diagnostic-write-failure coverage for at least one non-`:scry.cli/test-failure` test-derived outcome, especially a synthetic load-error entry, proving the fallback preserves `:scry.cli/load-error`, attaches bounded `:scry.cli/diagnostic-error`, and still emits the load-error stderr detail/pointer semantics where applicable.
- [x] Add focused diagnostic-write-failure coverage for `:scry.cli/unknown-result`, proving the fallback preserves the unknown-result outcome, attaches bounded `:scry.cli/diagnostic-error`, leaves result files empty, and still emits the existing unknown-result stderr pointer semantics.
- [x] Add focused diagnostic-write-failure coverage for a passing `:scry.cli/pass` outcome, proving post-run diagnostic write failure preserves exit code `0`/`:scry.cli/pass` while attaching bounded `:scry.cli/diagnostic-error` and empty result files.
- [x] Add focused diagnostic-write-failure coverage for a `:scry.cli/zero-tests` outcome, proving the fallback preserves non-zero exit/`:scry.cli/zero-tests`, attaches bounded `:scry.cli/diagnostic-error`, leaves result files empty, and does not emit failure-details pointer text.
- [x] Add a direct sanitizer test for Throwable `ex-data` depth limiting using a configured small `:max-ex-data-depth`, asserting nested ex-data is truncated independently of the outer data depth.
- [x] Add direct sanitizer tests proving `:max-seq-length` truncation emits an explicit bounded/truncation sentinel for each supported collection shape (sequential, vector, set, map/Java Map, array, Iterable), not only silently dropping excess values.
- [x] Add direct Throwable normalization tests that assert the documented frame field shape for `:at` and `:trace` entries, so result-file EDN locks the intended bounded frame representation rather than an accidental map/vector mismatch.
- [x] Add a direct sanitizer regression for a cyclic Throwable cause chain, proving controlled Throwable normalization emits a cycle placeholder instead of recursing or truncating only by incidental depth.
- [x] Add sanitizer coverage for collection truncation sentinel collisions, especially maps/sets that already contain `{:scry/truncated :max-seq-length}`, so tests lock whether the explicit sentinel remains observable when user data equals the sentinel value.
- [x] Strengthen the set truncation sentinel collision regression so the user-supplied sentinel value is outside the retained prefix; the current set case would still pass if truncation failed to append its own sentinel because the user sentinel is already retained.
- [ ] Make CLI diagnostic fallback string coercion itself hostile-safe: `bounded-diagnostic-string` (and callers such as `diagnostic-error` / stderr fallback) should catch throwing `toString` or non-string assertion message/root-cause values so a post-run result-file write failure cannot re-enter the outer `:scry.cli/runner-error` path while building fallback diagnostics. Add a focused regression with a failing entry whose first assertion message or map-shaped `:actual` message throws from `toString` while injected result-file writing fails.
