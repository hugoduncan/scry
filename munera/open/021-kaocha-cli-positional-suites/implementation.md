# Implementation notes

## Review

- architectural review: no actionable feedback (design fits the parse → collapse
  → normalize → execute pipeline, the frozen `-m`-only scope, the `-X`/adapter
  boundary, and the `:scry.cli/outcome-kind` contract).
- architectural re-review (post follow-up edits): still no actionable feedback;
  the ambiguity/inconsistency design edits were text-only clarifications and did
  not change the architecture or scope, so the sign-off holds for the revised
  design.
- ambiguity review: added 1 design step (resolve the Open Questions interleaving
  rule into a definitive decision + explicit positional-vs-token discrimination
  rule).
- inconsistency review: added 2 design steps (reconcile "trailing" wording in
  Approach step 2 vs the position-agnostic Open Questions default; correct the
  inaccurate "bare positionals previously unreachable in core mode" rationale —
  they hit the parse-time `default` "Unknown option" branch).

### Addressing the design-steps

- All three added design-steps are design.md text edits only (no code change);
  the interleaving decision is the one that must land before implementation so
  the parser rule is unambiguous. Keep the maintainer-confirmed Interpretation A
  scope frozen — these edits clarify/correct wording, they do not re-open scope.
- Relevant non-task files: `src/scry/cli.clj` (`parse-main-args`,
  `main-opts->exec-opts`, `normalize-exec-opts`, `normalize-core-options`,
  `kaocha-only-keys`); CLI tests in `test/scry/cli_test.clj` and Kaocha CLI tests
  `scry.cli-kaocha-test` (`:kaocha` alias); usage text lives in the `usage` def
  at the top of `src/scry/cli.clj`.

### Architecture context for implementation

- Core-mode rejection of positionals (design step 3) is already covered by the
  existing `normalize-core-options` `reject-keys` path: positionals collapse to
  `:suite`/`:suites` in `main-opts->exec-opts`, and `:suite`/`:suites` are in
  `kaocha-only-keys`, so core mode rejects them as `:scry.cli/argument-error`
  ("Kaocha options require :runner :kaocha"). Prefer reusing this pathway over a
  new positional-specific rejection branch to avoid a parallel validation path.
- The parser `default` case in `parse-main-args` is currently "Unknown option".
  The split should be: tokens starting with `-` stay unknown-option errors;
  other tokens become positional suite selectors. This keeps the change local to
  the hand-rolled loop and consistent with the existing architecture.

### Design follow-up pass (batch baseline f4bdfd1)

- Executed all 3 added design-steps; all were design.md text edits, no scope
  change. Interpretation A scope unchanged.
- Decision recorded in design.md: positional suite selectors are accepted
  **position-agnostically** (any non-`-` token, regardless of position relative
  to flags). Discrimination rule now lives in Approach step 2; an explicit
  Constraints bullet restates position-agnostic acceptance. "Open Questions"
  section removed (its sole item resolved).
- Also reconciled a second "trailing" occurrence in the **Goal** (not just
  Approach step 2) to "positional arguments" to avoid leaving an inconsistency
  with the position-agnostic decision.
- Constraints rationale corrected: bare positionals were reachable and rejected
  at parse time (`parse-main-args` `default` "Unknown option" branch), not
  unreachable; conclusion (must remain an error) preserved.
