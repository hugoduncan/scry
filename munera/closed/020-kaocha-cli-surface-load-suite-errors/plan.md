# Plan

## Approach

1. **Docs/spec first.** Update `AGENTS.md` and `README.md` CLI sections to document the
   new stderr failure diagnostic (pointer at results dir; load-error cause inline).

2. **Tests.**
   - `scry.cli-test`: add a focused test driving a synthetic `:scry.cli/load-error`
     entry (with `:assertions[0] :actual` a live `Throwable` carrying a cause) through
     `run-cli`. Assert the stderr boundary receives the pointer + root-cause text and
     that stdout receives only the summary. Assert exit code / outcome-kind / written
     file unchanged.
   - Update existing `run-cli-synthetic-nil-var-results-test` for the new stderr
     diagnostic (its outcome is load-error).
   - `scry.cli-kaocha-test`: extend with a real load-error project (broken test ns)
     asserting stderr contains the cause + results-dir hint, exit 1, and
     `suite-error-1.edn` written.

3. **Code — `src/scry/cli.clj`.**
   - Add helpers to extract root-cause text from a load-error assertion's `:actual`
     (live Throwable via cause chain, or `Throwable->map` shape via `:via`/`:cause`).
   - Add `write-failure-diagnostic!` routed through `boundary :err`, fired in
     `run-cli` after `write-summary!` for failure outcome kinds.

4. **Code — `src-kaocha/scry/kaocha.clj`.**
   - In `progress-reporter`, fire the callback for a suite-level `:error` event (no
     `current-var`) with a synthetic `{:var nil :ns nil :status :error
     :assertion-summary {:pass 0 :fail 0 :error 1}}` entry.

5. **Verify** with the documented core + Kaocha CLI command-line checks and a manual
   load-error run.

## Decisions

- Diagnostic text is human-oriented and supplementary; the authoritative machine
  signal remains `:scry.cli/outcome-kind` + `.scry-results/*.edn` (unchanged).
- Reuse `load-error-entry?` for selecting the entry to detail.

## Risks

- Existing tests asserting exact stderr strings must be updated (only the
  synthetic-nil-var test). Use `str/includes?`/`starts-with?` to stay robust.
