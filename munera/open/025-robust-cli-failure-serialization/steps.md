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
- [ ] Verify `-X` CLI return maps include the same `:summary`, `:result-files`, `:scry.cli/outcome-kind`, and optional `:scry.cli/diagnostic-error` semantics without duplicate summary fields.
- [x] Verify normal passing and normal failing CLI tests still preserve existing result-file and diagnostic behavior.

## Slice 6 — Documentation and verification

- [ ] Update README.md if the additive `:scry.cli/diagnostic-error` key or sanitizer placeholders need public documentation.
- [ ] Update AGENTS.md only if development workflow, conventions, or required verification commands change.
- [ ] Update `mementum/state.md` only if the project feature/structure snapshot changes materially.
- [ ] Run focused REPL or command-line tests while iterating and record useful discoveries in `implementation.md`.
- [ ] Run final focused CLI verification: `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"`.
- [ ] Run final core command-line verification if runner/capture/API behavior changed: `clojure -M:test -m scry.cli`.
- [ ] Record final verification commands and results in `implementation.md`.
