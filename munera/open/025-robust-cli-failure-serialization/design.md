# 025 — Robust CLI failure serialization (preserve real test signal)

## Problem

When tests produce real errors whose Throwable/data graphs are deep or
cyclic, scry's CLI can lose the actual test failure signal. Observed
failure mode:

1. Tests produced real errors (`UnsatisfiedLinkError`, then
   `NoClassDefFoundError`) — a missing native library
   (`target/native/librocksdb_packed_vals_native.dylib`).
2. The CLI attempted to EDN-normalize and write failure detail files via
   `scry.cli.results/edn-readable-data` / `write-result-files!`.
3. Normalization recursed through the Throwable/data graph until a
   `StackOverflowError`.
4. The whole `run-cli` `catch Throwable` path fired, so the CLI reported a
   runner-level `StackOverflowError` and outcome `:scry.cli/runner-error`
   with **no** test summary — as if no tests had run.

Net effect: a misleading "scry CLI StackOverflowError" instead of the
actionable "these filter-driver tests errored because the native library
is missing."

Root cause: result-file writing is treated as authoritative and shares the
same failure path as the test run, and the EDN sanitizer is neither
cycle-safe nor depth-limited, and walks arbitrary Throwables as ordinary
data.

## Goal

Preserve the real test failure signal and make CLI result serialization
robust against pathological (deep / cyclic / hostile) Throwable and data
graphs. Result-file writing becomes non-authoritative diagnostics: if it
fails, the CLI still prints the test summary, reports the failing vars, and
returns the correct test-outcome kind.

## Scope

Layer 1 — Preserve failure signal:

- Treat `write-result-files!` as non-authoritative. Wrap it in the CLI so a
  serialization failure is caught separately from the test run, a warning is
  written to stderr, and an empty result-file vector is used.
- The run outcome must remain `:scry.cli/test-failure` (or the appropriate
  test-derived outcome), **not** become `:scry.cli/runner-error`.
- Print the minimal test summary **before** detailed serialization work, so
  users always know the run failed because of test errors, not because no
  tests ran.

Layer 2 — Robust EDN sanitizer (`scry.cli.results/edn-readable-data`):

- Make it cycle-safe using tracked object identity
  (`java.util.IdentityHashMap`) and depth-limited.
- Accept limits, e.g. `{:max-depth 20 :max-seq-length 100 :seen <IdHashMap>}`.
- On limit/cycle/non-EDN, emit tagged placeholders:
  - `{:scry/truncated :max-depth}`
  - `{:scry/cycle true :class "java.lang.NoClassDefFoundError"}`
  - `{:scry/non-edn-class "org.rocksdb.RocksDBException" :str "..."}`
- Also cap sequence/collection length via `:max-seq-length`.

Layer 3 — Controlled Throwable normalization:

- Instead of recursively walking arbitrary Throwables as ordinary data,
  convert them through a bounded controlled shape:
  `{:type <class-symbol> :message ... :at [...] :trace [...] :cause {...}
    :suppressed [...]}`.
- Bound each part. Recommended caps:
  - cause chain max depth `8`;
  - stack trace max frames `80`;
  - suppressed exceptions max count `8`;
  - ex-data max depth `8`;
  - string max length `20_000`.
- This preserves the useful root cause message rather than a
  `StackOverflowError`.

Layer 4 — Outcome taxonomy:

- Distinguish "test runner failed" from "diagnostic rendering failed".
  Extend the taxonomy with `:scry.cli/diagnostic-error` alongside existing
  `:scry.cli/test-failure`, `:scry.cli/load-error`, `:scry.cli/runner-error`.
- When the test run succeeded in producing entries but diagnostic
  serialization failed, the outcome should be the test-derived outcome with
  an attached diagnostic-error, e.g.:
  `{:scry.cli/outcome-kind :scry.cli/test-failure
    :scry.cli/diagnostic-error {...}}`
  not `{:scry.cli/outcome-kind :scry.cli/runner-error}`.

Layer 5 — Runner diagnostic fallback ergonomics:

- Keep progress labels. When scry catches a runner-level exception that
  occurred **after** collecting entries, include the first failing var and
  first root cause in the diagnostic, e.g.:
  ```
  Failure diagnostics failed while serializing 17 failing entries.
  First failing var: rocksdb-packed-vals.query.filter-driver-...-test/...
  First root cause: java.lang.UnsatisfiedLinkError: Can't load library: ...
  ```

## Non-goals

- Changing the authoritative machine signals for normal passing/failing
  runs beyond the additions above.
- Changing scoped result formatting or the in-process runner isolation model.
- Supporting arbitrary parallel/async attribution.

## Constraints

- `scry.core` must not require Kaocha; `scry.cli` must not require
  `scry.kaocha` at load time. (Unchanged.)
- Keep public result shapes stable except for the additive
  `:scry.cli/diagnostic-error` key and placeholder tags.
- Follow project test workflow (REPL during dev, command-line verification
  before handoff) and update README/AGENTS/state.md for any user-facing
  behavior change.

## Acceptance criteria

- A run whose tests error with deep/cyclic Throwable or data graphs:
  - reports the tests as failed/errored;
  - CLI exits non-zero;
  - prints the test summary (assertions/tests passed/failed/errored)
    before serialization;
  - produces `.scry-results/*.edn` with truncation/cycle/non-edn
    placeholders (or, if writing itself fails, a stderr warning and empty
    result-file vector);
  - returns `:scry.cli/outcome-kind :scry.cli/test-failure` (never
    `:scry.cli/runner-error`) and, when diagnostics failed, attaches
    `:scry.cli/diagnostic-error`;
  - does **not** surface a `StackOverflowError`.
- `edn-readable-data` is cycle-safe and depth/length-limited with the
  documented placeholders.
- Throwables normalize through the bounded controlled shape with the
  documented caps, preserving the root cause message.
- Regression tests exist (see below) and pass.

## Regression tests

Add tests covering, at minimum:

```clojure
(deftest cyclic-failure-actual-does-not-crash-cli
  (let [x (java.util.HashMap.)]
    (.put x "self" x)
    (is (= {} x))))

(deftest throwable-with-cyclic-ex-data-does-not-crash-cli
  (let [m (java.util.IdentityHashMap.)]
    (.put m :self m)
    (throw (ex-info "boom" {:cyclic m}))))
```

Expected: test reported failed/errored; CLI exits non-zero; summary
printed; failure EDN contains truncation/cycle placeholders; no
`StackOverflowError`. Also add direct unit tests for `edn-readable-data`
limits and Throwable normalization caps.

## Most important concrete improvement

If only one change ships: make failure-detail EDN normalization cycle-safe
and catch diagnostic write failures separately from the test run. That
alone turns a misleading "scry CLI StackOverflowError" into a direct,
actionable "these filter-driver tests errored because the native library is
missing."

## Relevant code

- `src/scry/cli/results.clj` — `edn-readable-data`, `throwable-data`,
  `object-data`, `array-data`, `write-result-files!`.
- `src/scry/cli.clj` — `run-cli` (single `catch Throwable`),
  `error-outcome-kind`, `classify-outcome`, `write-summary!`,
  `write-failure-diagnostic!`, `failure-outcome-kinds`.
- Tests: `test/scry/cli_test.clj` (+ any new fixture namespace).
