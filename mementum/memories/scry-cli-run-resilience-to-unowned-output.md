🔁 scry CLI must never let a contained in-test condition abort the whole run.

`scry.cli/run-cli` wraps the runner in `catch Throwable` → `:scry.cli/runner-error`.
So any Throwable escaping `scry.clojure-test/run` collapses the ENTIRE run (no
summary, no `.scry-results`), while `scry.core/run` (no such catch) survives —
making the two paths diverge on the same namespaces.

Hardening (keep these invariants):
- `scry.capture/route-text!` catches Throwable and falls back; output from
  non-owned/native/JNI/background threads must never propagate.
- `append-to-state!` resolves the buffer via a pure swap, then appends under
  `(locking buffer ...)` — never mutate StringBuilders inside a `swap!` fn
  (retries + concurrent writers corrupt them, often throwing null-message errors).
- `test-vars-with-output-owners` wraps each var in try/catch → on escape calls
  `capture/record-uncaught-error!` (synthetic :error event) so the run still
  completes; progress `var-result` is also guarded.
- `run-cli` runner-error diagnostic must be non-empty: `error-diagnostic-message`
  falls back to exception class + root cause when `getMessage` is blank.

Regression: `scry.fixtures.background-output` + cli-test
`run-cli-background-output-does-not-abort-run-test` /
`run-cli-runner-error-diagnostic-is-non-empty-test`.
