# Implementation notes

## Review

- architectural review (final design, post-inconsistency edits 4b197c2): no
  actionable feedback — `-m`-only scope, `-X`/adapter and dynamic-load
  boundaries, shared `normalize-exec-opts` funnel, and `:scry.cli/outcome-kind`
  contract all preserved.
- ambiguity review (final design, post-inconsistency edits 4b197c2): no
  actionable feedback — discrimination rule, collapse semantics, flag value
  consumption, and core-mode argument-error delta are all unambiguous; the
  4b197c2 reframing introduced no new ambiguity.
- inconsistency review (final design, post-4b197c2): no actionable feedback —
  Goal/Approach/Constraints/Acceptance agree on position-agnostic selectors,
  collapse semantics, core-mode argument-error outcome (Approach states outcome,
  Constraints states mechanism — no contradiction), and `-X`/adapter/docs-sync
  claims. 4b197c2 was the self-consistent inconsistency-driven reframing.
- architectural review: no actionable feedback (design fits the parse → collapse
  → normalize → execute pipeline, the frozen `-m`-only scope, the `-X`/adapter
  boundary, and the `:scry.cli/outcome-kind` contract).
- architectural re-review (post follow-up edits): still no actionable feedback;
  the ambiguity/inconsistency design edits were text-only clarifications and did
  not change the architecture or scope, so the sign-off holds for the revised
  design.
- ambiguity re-review (post follow-up edits): no actionable feedback; the
  interleaving/positioning ambiguity was resolved by the prior follow-up (Open
  Questions removed, position-agnostic discrimination rule now explicit in
  Approach step 2 and Constraints).
- ambiguity review: added 1 design step (resolve the Open Questions interleaving
  rule into a definitive decision + explicit positional-vs-token discrimination
  rule).
- inconsistency re-review (post follow-up edits): added 1 design step — the
  first Constraints bullet's "must not change except that ... are now an argument
  error" clause contradicts its own corrected rationale (positionals were already
  an argument error); the real delta is the rejection message/mechanism, not the
  `:scry.cli/argument-error` outcome-kind.
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

### Notes for addressing the new inconsistency design-step (re-review baseline b1be97e)

- The remaining open design-step is a design.md text edit only: no code change,
  no scope change (Interpretation A frozen). Do not let the wording fix reopen
  whether core-mode positionals should error — they must remain an error.
- Principle to preserve when rewording: state the delta at the contract level.
  Core-mode positionals are `:scry.cli/argument-error` before and after; only the
  rejection message/path moves (parse-time "Unknown option" → normalize-time
  "Kaocha options require :runner :kaocha" via `normalize-core-options`'s
  `reject-keys` against `kaocha-only-keys`, because positionals collapse to
  `:suite`/`:suites` in `main-opts->exec-opts`). Keep the Acceptance line
  ("produces `:scry.cli/argument-error`") consistent with that framing.
- When implementation lands, the new core-mode error message for a stray
  positional will be the `kaocha-only-keys` message, not "Unknown option:";
  any core-mode positional-rejection test should assert outcome-kind
  `:scry.cli/argument-error` rather than pinning the old "Unknown option" text.

### Design follow-up pass (re-review batch baseline 9d97241)

- Executed the single inconsistency-re-review design-step (added in batch
  8ea2529→64204da→b1be97e; `d38cba1` was a note commit). design.md text edit
  only, no code/scope change (Interpretation A frozen).
- Rewrote the first Constraints bullet to state the delta at the contract level:
  core-mode positionals are `:scry.cli/argument-error` before and after; only the
  rejection mechanism/message moves (parse-time "Unknown option" → normalize-time
  `kaocha-only-keys`/`reject-keys` "Kaocha options require :runner :kaocha", since
  positionals now collapse to `:suite`/`:suites`). Consistent with the Acceptance
  line. No new ambiguity/inconsistency introduced.
- All design-steps are now checked; design.md should be stable for plan creation.
