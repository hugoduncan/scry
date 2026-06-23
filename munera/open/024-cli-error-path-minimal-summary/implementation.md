# Implementation notes

- architectural review added 1 new design step (doc-contract alignment for the
  new error-path stdout summary). Design is otherwise a clean architectural fit:
  it mirrors the existing `write-failure-diagnostic!` "supplementary human
  output, authoritative signals unchanged" pattern, stays within `scry.cli`
  (core jar, no new Kaocha load-time coupling), and preserves the successful
  `:summary` shape. No `META.md`/`doc/architecture.md` exist; AGENTS.md is the
  authoritative architecture source.
- ambiguity review added 2 new design steps (the "usage paths" vs `--help`
  success-path scope, and stdout-text-only vs returned `:summary` map key).
  Code check confirmed `--help`/usage is a separate exit-0 path that already
  prints usage, distinct from the `:summary nil` error outcomes.
- inconsistency review added 1 new design step. Verified in cli.clj that the
  catch-path `error-outcome-kind` only yields argument-error/runner-error;
  `:scry.cli/load-error` comes only from `classify-outcome` (normal return
  path) which already calls `write-summary!`. So the design's claim that
  load-error is a silent thrown `:summary nil` outcome is factually wrong —
  only runner-error (and argument-error) actually hit the silent path.

## design-review session (architecture turn)

- no new architectural review feedback. Re-reviewed against AGENTS.md (no
  META.md/doc/architecture.md exist): change stays in `scry.cli` (core jar,
  no new Kaocha load-time coupling), mirrors the supplementary
  `write-failure-diagnostic!` pattern with authoritative signals unchanged,
  keeps `:summary` nil and successful summary text byte-stable, and its
  doc-contract alignment is already covered by the existing architectural
  design-step and the design Acceptance.

## design-review session (ambiguity turn)

- no new ambiguity review feedback. Prior ambiguity findings (usage/`--help`
  scope, stdout-only vs returned `:summary` key) are already resolved in
  design.md. Remaining latitude (exact minimal-summary wording) is adequately
  bounded by Acceptance ("clearly-labelled", "not look like a 0/0 green run").

## design-review session (inconsistency turn)

- no new inconsistency review feedback. Prior inconsistency finding (load-error
  miscast as a silent thrown `:summary nil` outcome) is already corrected
  throughout design.md. Re-verified internal consistency: in-scope error
  outcomes, `:summary` stays `nil`, `--help`/usage exclusion, and the
  README/AGENTS doc-update requirement are all stated consistently.

## design-review session outcome (net)

- This design-review session (architecture + ambiguity + inconsistency turns)
  added zero new design-steps. All existing design-steps are already resolved;
  design.md is review-clean and ready for implementation. Principles, key code
  paths, and project files for the implementation slice are already recorded in
  the "Notes for the design-step follow-up task" section below — no new
  follow-up work is outstanding from this review.

## plan-review session (ambiguity turn)

- no ambiguity review feedback. Plan/steps map cleanly to `cli.clj`: runner-error
  via `run-cli` catch, argument-error via `main-outcome` (-m) and
  `argument-error-outcome`/`run-with-boundary` (-X); traced flows confirm one
  summary-writing site per invocation. Remaining latitude (exact wording; whether
  to plumb `boundary` into `argument-error-outcome` vs write in
  `run-with-boundary`) is adequately bounded by Acceptance and the steps' slash
  notation. Prior design-review ambiguity findings remain resolved.

## plan-review session (inconsistency turn)

- no inconsistency review feedback. design.md, plan.md, and steps.md are mutually
  consistent on scope (runner-error + argument-error; load-error and `--help`
  excluded; `:summary` stays nil; README/AGENTS doc updates). The completed `[x]`
  architectural design-step's parenthetical still lists load-error/usage, but
  it is stale historical text overridden by the corrected authoritative scope in
  design.md Acceptance and plan/steps slice 4 — not actionable.

## Notes for the design-step follow-up task

- Principle: error-path stdout output is supplementary human output only; keep
  authoritative signals (`:scry.cli/outcome-kind`, exit code,
  `.scry-results/*.edn`) and successful pass/fail summary text byte-stable.
  Mirror the existing `write-failure-diagnostic!` pattern.
- Key code: `src/scry/cli.clj` — silent paths are the `run-cli` `catch`
  (runner-error) and `main-outcome`/`argument-error-outcome` (argument-error)
  only. `write-summary!` (~line 522), `error-outcome-kind` (~654),
  `classify-outcome` (~610), `run-cli` (~727), `main-outcome` (~804).
- load-error already gets a stdout summary via the normal return path; do not
  add duplicate output there (see inconsistency design-step).
- Tests: `test/scry/cli_test.clj` (core) and `test/scry/cli_kaocha_test.clj`
  (Kaocha-mode runner-error). Docs to update: `README.md` and `AGENTS.md`
  CLI output-contract sections.

## Design-step follow-up resolution (all 4 batch items executed)

Resolved the design's scope to be unambiguous and self-consistent after the
architecture/ambiguity/inconsistency review batch:

- In scope: minimal stdout summary for `:scry.cli/runner-error` (run-cli catch
  path) and `:scry.cli/argument-error` (argument-error path) only.
- Out of scope, corrected throughout design.md (Intent, Goal "Concretely",
  Context, Acceptance): `:scry.cli/load-error` already emits a stdout summary
  via the normal return path (`classify-outcome` → `write-summary!`); the catch
  path never produces load-error. Do NOT add output for it — would duplicate.
- Out of scope: `--help`/usage success path (exit 0, already prints usage); it
  is not a `:summary nil` error outcome and must not emit the error-style line.
- Deliverable is stdout text only: returned outcome map `:summary` stays `nil`.
- Acceptance now requires README.md + AGENTS.md CLI output-contract doc updates.

## Implementation pass (all slices)

- Added `write-error-summary!` in `src/scry/cli.clj` (next to `write-summary!`).
  Wording: `No tests run — scry CLI error outcome: <outcome-kind>\n` to
  `(:out boundary)`. Deliberately not a 0/0 green run.
- Call sites (each invocation flows through exactly one site):
  - `run-cli` catch → `write-error-summary!` with computed `error-outcome-kind`
    (covers `:scry.cli/runner-error`; argument errors are raised before
    `run-cli` so this branch is runner-error in practice).
  - `run-with-boundary` argument-error catch (`-X`) → emits before throwing
    `non-zero-exception`.
  - `main-outcome` argument-error catch (`-m`).
- `:summary` stays `nil` in all error outcome maps (stdout-text-only change).
- Tests updated: existing runner-error tests previously asserting empty stdout
  (`scry.cli-test`) now assert the minimal summary line; `main-outcome`
  argument-error test asserts the line and `--help` now asserts the line is
  absent. New `-X` argument-error test (single line + `:summary` nil). New
  Kaocha-mode runner-error assertions in `scry.cli-kaocha-test`.
- Docs: README.md and AGENTS.md CLI output-contract sections updated.

### Verification (command line)
- `clojure -M:test ... (ct/run-tests 'scry.cli-test)` → 45 tests, 394
  assertions, 0 failures, 0 errors.
- `clojure -M:test:kaocha ... (ct/run-tests 'scry.cli-kaocha-test)` → 11 tests,
  74 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` → all formatted; `bb clj-kondo:lint` → 0 errors/warnings.

## implementation-review session

- reviewed implementation against design/plan: faithful, tests green (cli-test
  394 assertions, cli-kaocha-test 74 assertions), docs updated. No actionable
  findings; 0 follow-up steps added.
