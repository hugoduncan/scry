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
