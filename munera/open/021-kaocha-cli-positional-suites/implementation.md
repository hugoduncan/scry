# Implementation notes

## Review

### Plan review (plan-review session)

- inconsistency review (plan + steps): added 1 design step — the docs slice
  (plan step 6 / steps Slice 3) says "regenerate `doc/API.md` (`bb api-docs`)"
  to reach the positional form, but that example is curated prose hardcoded in
  `bb/scry/api_docs.clj`; regeneration re-emits it unchanged and the doc gates
  (`bb api-docs --check`, `scry.api-docs-test`) won't flag the stale `--suite`
  text. `bb/scry/api_docs.clj` must be edited. `SKILL.md:159` also has the stale
  form but is outside the design's README/AGENTS/API doc scope.
### Plan-review follow-up pass (batch baseline b8423b7) — complete

- Batch segment: `4864a0c` (plan ambiguity, no feedback) → `cc0efe5` (plan
  inconsistency, +1 item) → `f6bc12c` (note commit). Baseline = parent of oldest
  segment commit = `b8423b7` (the plan.md/steps.md add). `git diff
  b8423b7..HEAD -- steps.md` was empty; the one attributable unchecked item was
  added to `design-steps.md` (where the plan-review profile records follow-ups),
  under "## Plan-review follow-up (inconsistency review)".
- Executed that single item: a planning-artifact correction (no code change yet,
  Interpretation A frozen). Updated plan.md step 6, the Slice-order docs item,
  and the Docs-drift risk to require editing the curated Kaocha `-m` example in
  `bb/scry/api_docs.clj` (`intro` string) to the positional form *before*
  regenerating `doc/API.md`, because regeneration re-emits the curated prose
  unchanged and the doc gates (`bb api-docs --check`, `scry.api-docs-test`) do
  not pin that example. Added a matching steps.md Slice 3 step ahead of the
  regenerate step. `-X` example `:runner :kaocha :suite :unit` left unchanged;
  `SKILL.md:159` flagged for maintainer, not in scope.
- Confirmed against source: `bb/scry/api_docs.clj` line 72 carries the stale
  `-m` form; line 68 the `-X` form. The implementer should edit only line 72.

### Notes for the plan-review follow-up (docs-slice design-step)

- This follow-up is a plan.md/steps.md docs-slice text correction, not a code or
  scope change — keep Interpretation A frozen and do not widen the design's
  README/AGENTS/API doc scope (so leave `SKILL.md` for the maintainer).
- Exact source anchor: `bb/scry/api_docs.clj`, the `intro` string (~line 71-72),
  literal `clojure -M:test:kaocha -m scry.cli --runner kaocha --suite unit`. Edit
  that string to the positional form, then run `bb api-docs` to regenerate
  `doc/API.md`. There is no runtime docstring carrying this example.
- README/AGENTS anchors already enumerated in steps Slice 3 are accurate
  (README lines 79, 143-144, 146; AGENTS line 128); only the API.md source was
  unaccounted for. `-X` example `:runner :kaocha :suite :unit` in the same
  `intro` string stays unchanged (adapter `-X` path is out of scope).

### Notes for the plan-review inconsistency follow-up (`--config`-test design-step)

- This follow-up is a plan.md step-5 wording correction only — no code, test, or
  scope change (Interpretation A frozen). Do not start editing `cli_test.clj`
  here; the actual test edit belongs to the implementation slice per steps.md
  Slice 2.
- steps.md Slice 2 is already correct (it keeps the combined test "only for
  `--config`" and omits `--config` from its unchanged list) and is read-only
  task context — fix plan.md to match steps.md, not the reverse.
- Precise reconciliation: in plan.md step 5, remove `--config` from the "Keep
  ... unchanged" clause; instead state that `--config` *coverage* is preserved
  while the fused `cli_test.clj:232` "accepted Kaocha suites and config EDN
  flags" test is split/edited to drop the removed `--suites` portion. Keep
  `--focus` and `--kaocha-opt` as genuinely-unchanged tests.
- Relevant non-task anchor: `test/scry/cli_test.clj:232` (the combined
  `--suites`/`--config` EDN test); the removed `--suites`/`--suite`/`-s` flag
  tests are at `cli_test.clj:220-236` and the mutual-exclusion test at
  `cli_test.clj:275-276`.

- inconsistency review (plan-review session, post-docs-slice-follow-up 00fffd1):
  added 1 design step — plan.md step 5 lists `--config` among "tests unchanged",
  but the only `--config` parse test (`cli_test.clj:232`) is fused with the
  removed `--suites` flag and steps.md Slice 2 correctly directs editing it;
  classified actionable because a literal reading of plan step 5 would leave the
  broken combined `--suites "[:unit]"` assertion. Plan vs steps otherwise
  consistent (slice order, core-mode argument-error framing, docs-slice
  api_docs.clj mechanism, position-agnostic collapse). The "byte-for-byte what
  the old flags produced" claim appears identically in design.md and plan.md
  (vs the repeatable `--suite`/`-s` string path, not the removed `--suites` EDN
  path) — same framing in both files, so not a cross-file inconsistency.

- ambiguity review (plan-review session, post-docs-slice-follow-up 00fffd1): no
  ambiguity review feedback — re-ran after the docs-slice edits; verified anchors
  (api_docs.clj:72 `-m`/:68 `-X`, README 79/143/144/146, AGENTS 128, root
  SKILL.md:159 out-of-scope) and the discrimination rule/collapse/core-mode
  pathway are all accurate and unambiguous. Updated docs-slice text added no new
  ambiguity.

- ambiguity review (plan-review session, post --config-step follow-up 617b3ee):
  no ambiguity review feedback — re-ran on current plan.md/steps.md after the
  plan step 5 `--config`-wording edit. That edit is a precise clarification (no
  new ambiguity). Re-verified discrimination rule, `:suite-values`
  accumulator/count-based collapse, core-mode `kaocha-only-keys`/`reject-keys`
  rejection, REPL-check return shapes (`:suite`/`:suites` survive
  `normalize-kaocha-options`), test specs, and doc anchors (api_docs.clj:72
  `-m`/:68 `-X`, README 79/143/144/146, AGENTS 128, root SKILL.md:159
  out-of-scope) — all concrete and code-grounded.

- inconsistency review (plan-review session, post --config-step follow-up
  617b3ee): added 1 design step — the 617b3ee plan edit reconciled plan↔steps
  but surfaced a design↔plan divergence: design.md Approach step 6 still says
  keep the `--config` pass-through test "as-is", contradicting plan step 5 /
  steps Slice 2 (the fused `--suites`/`--config` test must be split/edited).
  Classified actionable by the same standard the plan-side `--config` finding
  used (literal reading preserves the broken `--suites "[:unit]"` assertion).
  Scoped to design.md Approach step 6 wording only — Constraints (l.91) and
  Acceptance (l.104) speak of `--config` *behavior* (unchanged) and stay; no
  scope change. Plan/steps otherwise consistent with design (scope, parser rule,
  collapse, core-mode argument-error, docs-slice api_docs.clj mechanism).

### Notes for the inconsistency follow-up (design.md step 6 `--config`-test design-step)

- Design-artifact text edit only: no code/test change in the follow-up pass.
  Interpretation A stays frozen. The actual `--config` test split/edit is
  steps.md Slice 2 implementation work, not this follow-up.
- Do not re-derive the corrected wording: mirror plan.md step 5, which already
  carries the canonical phrasing (`--config` *coverage* preserved; fused
  `--suites`/`--config` test split/edited to drop the `--suites` portion). Edit
  design.md Approach step 6 (lines 65-66) to match; leave `--focus`/`--kaocha-opt`
  as genuinely-unchanged tests.
- Leave design.md Constraints (l.91) and Acceptance (l.104) untouched — they
  speak of `--config` *behavior* (unchanged), which is correct and not the
  inconsistency.

### Plan-review follow-up pass (batch baseline 00fffd1) — complete

- Batch segment: `9c26f06` (plan ambiguity, no feedback) → `84c2d66` (plan
  inconsistency, +1 item) → `03fd13e` (note commit). Baseline = parent of oldest
  segment commit `9c26f06` = `00fffd1` (the prior plan-follow-up completion).
  `git diff 00fffd1..HEAD -- steps.md` was empty; the one attributable unchecked
  item was added to `design-steps.md` (plan-review profile records follow-ups
  there, as in the prior pass), under "## Plan-review follow-up (inconsistency
  review)".
- Executed that single item: a planning-artifact correction only (no code/test
  change; Interpretation A frozen). Reworded plan.md step 5 to stop listing
  `--config` among "tests unchanged" — `--config` *coverage* is preserved, but
  its only parse test is the fused `cli_test.clj` "accepted Kaocha suites and
  config EDN flags" case (also exercises the removed `--suites` flag), so it is
  split/edited to drop the `--suites` portion (matching steps.md Slice 2). Kept
  `--focus`/`--kaocha-opt` as genuinely-unchanged tests.
- steps.md Slice 2 was already correct and consistent (read-only task context) —
  no steps.md edit needed; plan.md was brought into agreement with it.
- Confirmed anchor: `test/scry/cli_test.clj` ~line 232, `(testing "accepted
  Kaocha suites and config EDN flags" ...)` fuses `--suites "[:unit]"` with
  `--config "{:kaocha/tests []}"`. The implementer (Slice 2) must split/keep the
  `--config` assertion while dropping the `--suites` assertion. No new
  ambiguity/inconsistency introduced; all design-steps now checked, plan/steps
  consistent — ready for implementation.

- ambiguity review (plan + steps): no ambiguity review feedback — accumulator
  (`:suite-values`), count-based collapse (single→`:suite`/multi→`:suites`),
  positional-vs-token discrimination rule, dropped-vs-retained mutual-exclusion
  (parse-time checks removed; `normalize-kaocha-options` `:suite`/`:suites`
  check correctly retained for `-X` and unreachable from `-m` collapse), and
  core-mode outcome-kind assertion are all unambiguous and code-grounded.

### Design review (design phase)

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

### Design-review session complete — ready for planning

- Fresh design-review session (architectural + ambiguity + inconsistency) added
  0 new design-steps; all 4 existing design-steps remain checked. No pending
  design edits — planning can proceed directly against the current design.md.
- For the plan/implementation slice that addresses the (already-resolved)
  design-steps: keep Interpretation A frozen (only the `-m` wrapper; `-X` path
  and `scry.kaocha/run` adapter unchanged). Reuse the existing
  `normalize-core-options` `reject-keys`/`kaocha-only-keys` path for core-mode
  positional rejection rather than adding a parallel branch; assert outcome-kind
  `:scry.cli/argument-error` (not the old "Unknown option" text) in any
  core-mode positional test. Relevant files: `src/scry/cli.clj`
  (`parse-main-args`, `main-opts->exec-opts`, `usage`, `normalize-core-options`,
  `kaocha-only-keys`), `test/scry/cli_test.clj`, `test/scry/cli_kaocha_test.clj`
  (`:kaocha` alias), plus `README.md`/`AGENTS.md`/`doc/API.md` for docs sync.
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

### Plan-review follow-up pass (batch baseline 617b3ee) — no in-scope work

- Batch segment: `4228856` (plan ambiguity, no feedback) → `da5f24d` (plan
  inconsistency, +1 item) → `77929c7` (note commit). Baseline = parent of oldest
  segment commit `4228856` = `617b3ee` (the prior plan-follow-up completion).
  `git diff 617b3ee..HEAD -- steps.md` is empty: this batch added no `steps.md`
  checklist lines, so the plan-follow-up candidate work set (steps.md additions)
  is empty.
- ambiguity review (plan-review session, post-baseline 617b3ee batch, HEAD
  8e3962d): no ambiguity review feedback — plan.md/steps.md unchanged since the
  prior no-feedback ambiguity review (4228856); fresh read against
  `src/scry/cli.clj` re-confirms the discrimination rule, `:suite-values`
  accumulator/count-based collapse (lines 286-296), core-mode
  `kaocha-only-keys`/`reject-keys` rejection (line 39), flag value consumption,
  the split `--config` test handling, and doc anchors are all concrete and
  code-grounded. The open design.md step-6 `--config` item is an inconsistency
  for the design profile, not a plan/steps ambiguity.

- inconsistency review (plan-review session, HEAD post-8e3962d): no new
  inconsistency review feedback — plan.md/steps.md unchanged since the prior
  inconsistency review (da5f24d). plan↔steps remain internally consistent (slice
  order, parser rule, `:suite-values` collapse, core-mode argument-error framing,
  docs-slice api_docs.clj mechanism, reconciled `--config` test handling). The
  only residual cross-file divergence (design.md step-6 `--config` "as-is" vs
  plan/steps) is already recorded as an unchecked design-step for the design
  profile; not re-added (rule 3 no-duplicate).

### Plan-review session close (HEAD post-8e3962d)

- This plan-review session (ambiguity + inconsistency turns) added **no new
  design-steps**. The sole outstanding item is the pre-existing unchecked
  design.md step-6 `--config` design-step, which is a **design-profile** concern
  (it needs a design.md Approach step-6 edit; out of plan-profile editable scope).
  Its full follow-up guidance already lives above under "Notes for the
  inconsistency follow-up (design.md step 6 `--config`-test design-step)" — mirror
  plan.md step 5's canonical phrasing; do not touch design.md Constraints
  (l.91)/Acceptance (l.104); keep Interpretation A frozen. No other principle or
  path needs recording for this slice.

- The single batch finding (`da5f24d`) was recorded in `design-steps.md` under
  "## Plan-review follow-up (inconsistency review)" and requires editing
  **design.md** Approach step 6 (lines 65-66): reword "keep `--config`
  pass-through test as-is" to state `--config` *coverage* is preserved while its
  fused `--suites`/`--config` test is split/edited (mirroring plan step 5 / steps
  Slice 2). That is a **design.md** edit, which is read-only for the
  plan-follow-up profile (editable set: plan.md/steps.md/implementation.md/
  code/tests/docs). The prior pass's analogous item was executable only because
  it reconciled within plan.md; this one cannot be resolved by any plan.md/
  steps.md edit (plan/steps are already correct — design.md lags them).
- Action: left the `design-steps.md` item **unchecked** and did not edit
  design.md (out of plan-profile scope). It is a design-review-follow-up
  concern; a design-profile pass should execute it. No plan.md/steps.md change
  was warranted this pass; no new ambiguity/inconsistency introduced.
