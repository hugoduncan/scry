# Steps

## Slice 1 — Baseline orientation and characterization

- [ ] Inspect `src/scry/cli.clj` to map the current `run-cli`, `classify-outcome`, summary writing, result-file writing, and outer `catch Throwable` flow.
- [ ] Inspect `src/scry/cli/results.clj` to map current `edn-readable-data`, `throwable-data`, object, array, and file-writing behavior.
- [ ] Inspect existing CLI tests in `test/scry/cli_test.clj` for result-file, stderr/stdout, `-X`, and outcome-kind expectations.
- [ ] Add or identify a fixture/test path that can run a failing assertion with cyclic actual data through the CLI.
- [ ] Add or identify a fixture/test path that can run an erroring test with cyclic `ex-data` through the CLI.

## Slice 2 — Robust EDN sanitizer

- [ ] Define default sanitizer limits for maximum depth, maximum sequence length, Throwable cause depth, stack frames, suppressed exceptions, ex-data depth, and string length.
- [ ] Change `edn-readable-data` to accept and propagate an options map containing limits and an `IdentityHashMap` for seen object identities.
- [ ] Emit `{:scry/truncated :max-depth}` when maximum depth is exceeded.
- [ ] Emit `{:scry/cycle true :class "..."}` when an identity cycle is detected.
- [ ] Emit `{:scry/non-edn-class "..." :str "..."}` for values that cannot be represented directly as EDN.
- [ ] Cap sequential and collection values at `:max-seq-length` while preserving deterministic, readable EDN output.
- [ ] Bound long strings to the configured maximum length with an explicit truncation marker or bounded value.
- [ ] Add direct unit tests for depth truncation, sequence truncation, identity cycles, non-EDN placeholders, and string bounding.

## Slice 3 — Controlled Throwable normalization

- [ ] Replace arbitrary Throwable walking with bounded Throwable normalization returning `:type`, `:message`, `:at`, `:trace`, `:cause`, and `:suppressed` where available.
- [ ] Represent Throwable types as class symbols and keep messages bounded.
- [ ] Limit stack trace frames to the configured maximum.
- [ ] Limit cause-chain traversal to the configured maximum and avoid cause cycles.
- [ ] Limit suppressed exceptions to the configured maximum count.
- [ ] Normalize `ex-data` with a smaller bounded sanitizer depth and identity-cycle protection.
- [ ] Add direct unit tests for cause-depth truncation, stack-frame caps, suppressed caps, cyclic ex-data, and preserved root-cause type/message.

## Slice 4 — Non-authoritative diagnostic writing

- [ ] Refactor `run-cli` so canonical entries and summary are collected before result-file serialization.
- [ ] Compute `:scry.cli/outcome-kind` from entries and summary before result-file serialization.
- [ ] Write the normal CLI summary to stdout immediately after outcome classification and before result-file writing.
- [ ] Wrap `write-result-files!` in a focused diagnostic failure boundary separate from the outer runner-error catch.
- [ ] On result-file-writing failure, return an empty result-file vector and attach top-level `:scry.cli/diagnostic-error` without changing `:scry.cli/outcome-kind`.
- [ ] Build `:scry.cli/diagnostic-error` with required keys `:phase`, `:message`, `:type`, `:root-type`, `:root-message`, `:failed-entry-count`, `:first-failing-var`, and `:first-root-cause` when derivable.
- [ ] Emit concise fallback stderr diagnostics for result-file-writing failures, including failed entry count and first failing var/root cause when derivable.
- [ ] Preserve existing runner-error behavior for exceptions raised before entries and summary are collected.
- [ ] Add focused tests for a forced `write-result-files!` failure verifying test-derived outcome, empty result-file vector, diagnostic-error shape, summary output, and stderr fallback.

## Slice 5 — End-to-end CLI regressions

- [ ] Add an end-to-end CLI regression for cyclic assertion actual data verifying non-zero exit, `:scry.cli/test-failure`, summary output, no primary `StackOverflowError`, and cycle/truncation placeholders in failure EDN or fallback diagnostics.
- [ ] Add an end-to-end CLI regression for cyclic Throwable ex-data verifying non-zero exit, `:scry.cli/test-failure`, summary output, no primary `StackOverflowError`, preserved root-cause message, and cycle/truncation placeholders in failure EDN or fallback diagnostics.
- [ ] Verify `-X` CLI return maps include the same `:summary`, `:result-files`, `:scry.cli/outcome-kind`, and optional `:scry.cli/diagnostic-error` semantics without duplicate summary fields.
- [ ] Verify normal passing and normal failing CLI tests still preserve existing result-file and diagnostic behavior.

## Slice 6 — Documentation and verification

- [ ] Update README.md if the additive `:scry.cli/diagnostic-error` key or sanitizer placeholders need public documentation.
- [ ] Update AGENTS.md only if development workflow, conventions, or required verification commands change.
- [ ] Update `mementum/state.md` only if the project feature/structure snapshot changes materially.
- [ ] Run focused REPL or command-line tests while iterating and record useful discoveries in `implementation.md`.
- [ ] Run final focused CLI verification: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"`.
- [ ] Run final core command-line verification if runner/capture/API behavior changed: `clojure -M:test -m scry.cli`.
- [ ] Record final verification commands and results in `implementation.md`.
