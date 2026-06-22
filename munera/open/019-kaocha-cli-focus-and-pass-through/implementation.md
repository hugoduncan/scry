# Implementation notes

## Entity resolution (2026-06-21)

Resolved alias-shorthand var refs in `design.md` to fully-qualified names:

- `api/run` → `kaocha.api/run` — `api` is `[kaocha.api :as api]` in
  `src-kaocha/scry/kaocha.clj` (`:require`); call site at line ~309.
- `config/apply-cli-args` → `kaocha.config/apply-cli-args` — `config` is
  `[kaocha.config :as config]`; call site at line ~260.

Concrete locations for later planning/implementation:

- `normalize-kaocha-options` — `src/scry/cli.clj:209` (private). Known
  scry-managed Kaocha keys it copies: `:suite`, `:suites`, `:config`,
  `:dirs`→`:test-paths`, `:source-paths`, `:test-paths`, `:ns-patterns`.
  Rejects `core-only-keys`; rejection sets `kaocha-only-keys` /
  `kaocha-fallback-keys` also live in this file.
- `scry.kaocha/run` — `src-kaocha/scry/kaocha.clj:270`. Pipeline:
  `resolve-config` → `select-suites` → `apply-runtime-defaults` →
  `apply-progress-reporter` → `kaocha.api/run`. `:kaocha-extra` merge point
  would be in/around this config pipeline.
- `scry.cli/run` — `src/scry/cli.clj:605` (the `-X` entry referenced in
  acceptance commands).

The three Open Questions in `design.md` are genuine design choices (named vs
generic flag; `:config` precedence vs merge; `:focus` config mapping), not
entity ambiguities — left for design/plan resolution, not forced here.

## Design review (2026-06-21)

- Architecture review added 1 new design step. No META.md or
  doc/architecture.md exist; AGENTS.md is the architecture authority. The
  dependency boundary (core ↛ Kaocha at load time) is respected by the design
  (`:kaocha-extra` collected in `scry.cli`, merged in `scry.kaocha/run`). The
  filed step concerns the CLI's explicit-validation boundary and
  `:scry.cli/outcome-kind` classification: blanket pass-through of arbitrary
  unrecognized keys risks eroding the deliberate "reject unknown options"
  architecture (`parse-main-args` argument-error path), distinct from the
  named-vs-generic Open Question.
- Ambiguity review added 2 new design steps: (1) imprecise closed exclusion set
  for pass-through ("known scry-managed set ... like :result-format") risks
  leaking scry-internal/mode keys (`:runner`, `:progress-callback`) into Kaocha
  config; (2) unspecified value coercion for `-m` raw-string pass-through values
  vs typed `-X` EDN values. The three existing Open Questions were treated as
  already-surfaced ambiguities and not re-filed.
- Inconsistency review added 2 new design steps: (1) Context's "silently
  dropped" claim is false for the `-m` path (`parse-main-args` throws
  "Unknown option" for unknown flags); only `-X` map normalization drops them;
  (2) internal tension between Constraint ":config must take full precedence"
  and the offered "merged" resolution for :config + pass-through.

## Design review — architecture turn (2026-06-21, shared design-review session)

- Architecture review added 1 new design step. Prior architecture follow-up
  (validation boundary / outcome-kind for blanket pass-through) is already
  executed and respected: `:kaocha-extra` is raw data collected in core
  `scry.cli` (no load-time Kaocha require), merged in `scry.kaocha/run`
  (src-kaocha); bounded/asymmetric pass-through preserves the outcome-kind
  contract. New finding is a layering misfit: Approach step 4 places `-m`
  per-option value coercion in core `scry.cli` ("before being placed in
  `:kaocha-extra`"), but Kaocha-type coercion is Kaocha-domain knowledge the
  architecture keeps in src-kaocha, and OQ3 itself points at
  `kaocha.config/apply-cli-args` (src-kaocha) as interpreter. Filed as
  actionable because it is a boundary/placement question distinct from the
  already-resolved value-coercion *ambiguity* step (whether/how vs where).

## Design review — ambiguity turn (2026-06-21, shared design-review session)

- No new ambiguity review feedback. Design is precise modulo its 3 explicit
  open questions; residual vagueness (`:focus` key mapping = OQ3; named-vs-
  generic `-m` surface = OQ1) is already surfaced, and exclusion-set /
  coercion ambiguities are covered by prior ambiguity steps + this session's
  architecture coercion-placement step.

## Design review — inconsistency turn (2026-06-21, shared design-review session)

- Inconsistency review added 1 new design step: Acceptance command 1's named
  `--focus` flag contradicts OQ1's still-open named-vs-generic `-m` surface
  choice. Promoted from the prior session's planner-glance note (it was never a
  filed design-step; the contradiction persists in design.md). Considered but
  not filed: Step 2's declarative ":focus → :focus config, processed by
  kaocha.api/run" vs OQ3 — treated as already-surfaced by co-located OQ3,
  consistent with the ambiguity turn.

## For the slice addressing the design-review (2026-06-21) steps

The two new steps from this session are each coupled to an existing Open
Question; address them together rather than in isolation:

- Architecture coercion-placement step ⇄ OQ3. Resolving where `-m` value
  coercion lives effectively answers OQ3. Preferred direction (keeps the
  core↛Kaocha boundary): forward `:kaocha-extra` as raw data from core
  `scry.cli` and let `scry.kaocha/run` interpret it in src-kaocha (e.g. via
  `kaocha.config/apply-cli-args`). Note the boundary concern is specifically
  about *value-type* knowledge: core already does Kaocha *key* routing
  (`:dirs`→`:test-paths`), so that level of key awareness in core is acceptable;
  embedding per-option value-type coercion in core is the part that misfits.
- Inconsistency step (Acceptance cmd1 `--focus`) ⇄ OQ1. The `-X` path already
  satisfies Acceptance command 2 via top-level-key auto-forward, so the fix is
  narrowly about the `-m` flag surface: either OQ1 commits to a named `--focus`
  flag (then cmd1 stands) or picks generic `--kaocha-opt`, in which case cmd1
  must be restated. Do not resolve by widening/narrowing scope.

## For the slice addressing these design-steps

Principles to maintain:
- Keep the dependency boundary: `:kaocha-extra` collection stays in core
  `scry.cli` (must not require `scry.kaocha` at load time); the config merge
  stays in `scry.kaocha/run` (src-kaocha). AGENTS.md is the architecture
  authority (no META.md / doc/architecture.md exist).
- Preserve the CLI's explicit-validation + `:scry.cli/outcome-kind` contract;
  prefer bounded/opt-in pass-through over default-forward of arbitrary keys.
- If `scry.kaocha/run`'s public option surface changes (e.g. documenting
  `:kaocha-extra`), regenerate `doc/API.md` via `bb api-docs` and re-run
  `bb api-docs --check` + the api-docs regression test.

## Design follow-up execution (2026-06-21)

Executed all 5 design-review follow-ups (architecture x1, ambiguity x2,
inconsistency x2) by updating `design.md`. Decisions made while resolving them
(for next reviewer/implementer):

- Pass-through is now **asymmetric and bounded** to preserve the
  `:scry.cli/outcome-kind` contract: `-m` is opt-in (named flags or
  `--kaocha-opt`; unknown flags still `argument-error`), while `-X` forwards
  top-level keys outside the scry-managed set. The `-X` "mistyped key surfaces
  as runner/load-error" trade-off is accepted and documented in Constraints.
  This was required because Acceptance command 2 forwards top-level `:focus` on
  `-X` (so requiring an explicit `:kaocha-extra` key on `-X` would break
  acceptance); only `-m` can stay strictly opt-in.
- Open Questions intentionally left open (not preempted): OQ1 named-vs-generic
  `-m` surface; OQ2 reject-vs-merge for `:config` + pass-through; OQ3 `:focus`
  key mapping. The `:config` Constraint now only fixes that `:config` is
  authoritative on conflict, which is consistent with either OQ2 resolution.
- Latent tension (not introduced here, worth a planner glance): Acceptance
  command 1 writes `--focus my.ns/test-foo` literally, which presumes a *named*
  `--focus` flag — i.e. it leans toward one OQ1 answer. If OQ1 picks the
  generic `--kaocha-opt` mechanism only, that acceptance command would need
  updating.

Relevant non-task file locations (in addition to entity-resolution notes above):
- `parse-main-args` — `src/scry/cli.clj:279`; default branch throws the
  "Unknown option" `argument-error` (basis for inconsistency finding 1).
- Recognized key sets — `src/scry/cli.clj:34-37`: `ns-pattern-keys`,
  `core-only-keys`, `kaocha-only-keys`, `kaocha-fallback-keys` (basis for the
  enumeration ambiguity step; `:runner`/`:progress-callback`/`:result-format`
  are not in these sets).
- Outcome classification — `classify-outcome` (`src/scry/cli.clj:442`),
  `error-outcome-kind` (`:467`), `exit-code`/`run` wiring (~`:548-575`).
- Tests to extend: `test/scry/cli_test.clj` (core CLI),
  `test/scry/cli_kaocha_test.clj` and `test/scry/kaocha_test.clj` (require the
  `:kaocha` alias).

## Design follow-up execution — shared design-review batch (2026-06-21)

Batch baseline: parent of architecture turn `018967f` is `119754f` (previous
follow-up completion). Candidate set from `git diff 119754f..HEAD -- design-steps.md`
= exactly the 2 added unchecked items below; both still unchecked at start, both
executed:

- Architecture coercion-placement → fixed by editing Approach **step 4**:
  per-option *value-type* coercion now lives in `scry.kaocha/run` (src-kaocha),
  `:kaocha-extra` stays raw forwarded data in core `scry.cli`. Core keeps only
  *key* routing (`:dirs`→`:test-paths`). Preserves core↛Kaocha load boundary.
  OQ3's *mechanism* (`:focus` direct key vs `kaocha.config/apply-cli-args`)
  deliberately left open — this item resolved *where*, not *how*.
- Inconsistency (Acceptance cmd1 `--focus` vs OQ1) → fixed by committing OQ1 to
  **at least a named `--focus` flag** (Approach step 3 + OQ1 reworded in
  lockstep). Acceptance command 1 now stands unchanged. Chose the
  "commit OQ1 to named `--focus`" branch over restating cmd1 because `--focus`
  is the motivating headline feature; this is a `-m` surface/mechanism decision,
  not a scope change. Remaining OQ1 question narrowed to: also add extra named
  flags / generic `--kaocha-opt`?

Implementer note (new, from step-4 boundary placement): `scry.kaocha/run` must
coerce raw-string `:kaocha-extra` values that originate from the `-m` path, while
`-X` `:kaocha-extra` values arrive already typed (EDN). `kaocha.config/apply-cli-args`
is built for raw CLI strings and is the natural coercion path; settling that is
OQ3 and belongs to plan/implementation.

## Design review — architecture turn (2026-06-21, new shared design-review session, first turn)

- No new architectural review feedback. Reviewed current (updated) design.md
  against AGENTS.md (the architecture authority; no META.md / doc/architecture.md
  exist). The two prior architecture follow-ups are already executed and reflected:
  (1) core↛Kaocha load boundary — `:kaocha-extra` is raw data collected in core
  `scry.cli` (key routing only), value-type coercion deferred to `scry.kaocha/run`
  in src-kaocha (Approach step 4); (2) outcome-kind contract — asymmetric/bounded
  pass-through (`-m` opt-in, unknown flags still `:scry.cli/argument-error`; `-X`
  documented mistyped-key trade-off). Closed scry-managed exclusion set prevents
  internal/mode-key leakage; API-doc regeneration constraint noted for any
  `scry.kaocha/run` surface change. Design is architecturally coherent; nothing
  new to file.

## Design review — ambiguity turn (2026-06-21, new shared design-review session, second turn)

- No new ambiguity review feedback. Design is precise modulo its 3 explicit
  Open Questions (OQ1 named-vs-generic `-m` surface; OQ2 reject-vs-merge for
  `:config` + pass-through; OQ3 `:focus` key mapping), which are surfaced as
  open choices rather than latent ambiguities. Prior ambiguity steps
  (exclusion-set enumeration, value-coercion specification) and this lifecycle's
  architecture coercion-placement step are already executed and reflected in the
  updated design; nothing new to file.

## Design review — inconsistency turn (2026-06-21, new shared design-review session, third turn)

- No new inconsistency review feedback. The 3 prior inconsistency steps
  (Context "silently dropped" claim; `:config` precedence-vs-merge wording;
  Acceptance cmd1 `--focus` vs OQ1) are executed and reflected in the updated
  design. Re-evaluated step 2's declarative ":focus → :focus config processed
  by kaocha.api/run" vs OQ3's open interpretation-mechanism question: step 4
  self-qualifies step 2 as OQ3, design text unchanged, and the prior session
  already considered-but-did-not-file this as co-located with OQ3. Not re-filed.

## Design-review session outcome — for the slice addressing the design-steps (2026-06-21)

- This shared design-review session (architecture + ambiguity + inconsistency)
  added **zero** new design-steps. The design has converged: all 8 prior
  (Note: a further shared design-review session ran later — see the
  architecture turn note below — and also added zero new design-steps.)
  design-steps are checked/executed, and design.md is stable. No design edits
  are pending — the next lifecycle step can proceed to plan/implementation.
- All actionable principles and concrete file paths for implementation are
  already captured above — see "For the slice addressing these design-steps"
  (boundary, outcome-kind, API-docs principles) and the entity-resolution /
  follow-up-execution notes (cli.clj + src-kaocha/kaocha.clj line refs, test
  files). Do not re-derive; start there.
- The 3 Open Questions (OQ1 extra `-m` flags / `--kaocha-opt`; OQ2 `:config`
  reject-vs-merge; OQ3 `:focus` direct-key vs `apply-cli-args`) are the real
  remaining decisions and belong to plan/implementation, not further design
  review.

## Design review — architecture turn (2026-06-21, another shared design-review session, first turn)

- No architectural review feedback. design.md unchanged since `9e5dec6`
  (batch follow-up execution); reviewed against AGENTS.md (sole architecture
  authority — no META.md / doc/architecture.md). All prior architecture
  follow-ups remain executed and coherent: (1) core↛Kaocha load boundary —
  `:kaocha-extra` raw data + key routing only in core `scry.cli`, value-type
  coercion in `scry.kaocha/run` (src-kaocha); (2) `:scry.cli/outcome-kind`
  contract — asymmetric/bounded pass-through (`-m` opt-in, unknown flags still
  `:scry.cli/argument-error`; `-X` documented trade-off); closed scry-managed
  exclusion set prevents mode/internal-key leakage. The 3 Open Questions remain
  plan/implementation decisions, not architectural misfits. Nothing new to file.

## Design review — ambiguity turn (2026-06-21, another shared design-review session, second turn)

- No ambiguity review feedback. design.md unchanged; reviewed using
  already-loaded context. Design is precise modulo its 3 explicit Open Questions
  (OQ1/OQ2/OQ3), surfaced as open choices not latent ambiguities. Prior
  ambiguity steps (exclusion-set enumeration; `-m` value-coercion spec) and the
  architecture coercion-placement step are already executed/reflected. Nothing
  new to file.

## Design review — inconsistency turn (2026-06-21, another shared design-review session, third turn)

- No inconsistency review feedback. design.md unchanged; reviewed using
  already-loaded context. The 3 prior inconsistency steps (Context "silently
  dropped" `-m`-vs-`-X` distinction; `:config` precedence-vs-merge wording;
  Acceptance cmd1 `--focus` vs OQ1) are executed/reflected. Step 2's
  declarative `:focus` config claim is self-qualified by step 4 as OQ3 —
  co-located, not a latent contradiction; not refiled. Nothing new to file.
  This shared design-review session (all three turns) added zero new
  design-steps; design remains converged and stable.

## For the slice addressing the design-steps (2026-06-21, after second shared design-review session)

- This second full shared design-review session (architecture + ambiguity +
  inconsistency) added **zero** new design-steps. design.md is unchanged since
  `9e5dec6` and remains converged; all 8 design-steps stay checked. No new
  design edits are pending — the next lifecycle step can proceed to
  plan/implementation.
- Do not re-derive guidance: the actionable principles, decisions, and concrete
  non-task file paths are already captured above. Start from:
  - "For the slice addressing these design-steps" (boundary, outcome-kind,
    API-docs principles).
  - "Design follow-up execution" + "shared design-review batch" notes
    (cli.clj + src-kaocha/scry/kaocha.clj line refs, test files, the
    raw-`-m`-string vs typed-`-X`-EDN coercion implementer note).
  - "Design-review session outcome" note (the 3 Open Questions OQ1/OQ2/OQ3 are
    the real remaining decisions, belonging to plan/implementation).

## Plan review — ambiguity turn (2026-06-21, shared plan-review session, first turn)

- Ambiguity review added 2 new design steps, both about the normalized
  `:kaocha-extra` key being unaccounted for in the existing key sets: (1) the
  `-m`-built `:kaocha-extra` map collides with the `-X` collection step in the
  shared `normalize-kaocha-options` (re-collection/nesting), and the closed
  scry-managed key set omits `:kaocha-extra`; (2) Slice 2's "reject
  `--focus`/`--kaocha-opt` in core mode" has no enforcement point —
  `normalize-core-options`'s reject set (`src/scry/cli.clj:199`) excludes
  `:kaocha-extra`, so core-mode pass-through would be silently ignored, not
  `:scry.cli/argument-error`. Both are plan/steps-level emergent ambiguities,
  not duplicates of the design-level exclusion-set step (which enumerated the
  *input* keys, not the produced `:kaocha-extra` key). OQ1/OQ2/OQ3 resolutions
  in plan.md were not re-litigated.

## Plan review — inconsistency turn (2026-06-21, shared plan-review session, second turn)

- No inconsistency review feedback. plan.md and steps.md are mutually
  consistent (matching 6-slice order, merge-point, flag surface, outcome-kind
  preservation) and align with the converged design; OQ1/OQ2/OQ3 resolutions in
  plan.md are legitimate refinements of design's deferred questions, not
  contradictions. Considered but not filed: design Approach step 2's loose
  "`:focus` ends up as `:focus` in the Kaocha config" reads top-level, while
  plan OQ3 routes it into `:kaocha/cli-options {:focus ...}` (filter plugin
  translates to `:kaocha.filter/focus`). Now that plan resolves OQ3, design
  step 2 is stale-but-compatible (cli-options *is* "in the config", still
  processed by `kaocha.api/run`); prior design-review turns already
  considered-and-did-not-file this as co-located with OQ3. Implementer: follow
  plan OQ3 (cli-options), do not place `:focus` at config top-level.

## For the slice addressing the plan-review design-steps (2026-06-21)

Both new plan-review design-steps share one root cause: `:kaocha-extra` is a
scry-*produced* normalized key, but the key sets only account for user *input*
keys. Address them together, in one place, so `-X` collection and core-mode
disposition cannot diverge.

Principles to maintain:
- Resolve purely in core `src/scry/cli.clj` — these are key-set/routing
  concerns, not value coercion. Keep the core↛Kaocha load boundary intact (no
  src-kaocha change needed for these two steps).
- Prefer reusing existing mechanisms over new branches: `reject-keys`
  (`src/scry/cli.clj:177`) is the established argument-error path; core-mode
  rejection of `:kaocha-extra` should extend the `normalize-core-options`
  reject set (`:199`) rather than add a bespoke check, preserving the
  `:scry.cli/argument-error` outcome-kind contract.
- Treat `:kaocha-extra` as a closed scry-managed key: exclude it from the `-X`
  collection set in `normalize-kaocha-options` so an already-collected (`-m`)
  `:kaocha-extra` is not re-collected/nested; define the `-m`+`-X` merge rule
  if both can coexist.

Task info useful for the fix:
- The `-m` flags (Slice 2) populate `:kaocha-extra` in raw opts *before*
  `normalize-exec-opts` dispatches on `:runner`, which is exactly why core-mode
  disposition must be decided explicitly (a Kaocha-only flag can reach the core
  branch).
- Both steps are exercisable in `test/scry/cli_test.clj` without the `:kaocha`
  alias (core normalization + core-mode rejection are pure core paths).

## Plan-review follow-ups executed (2026-06-21)

Resolved both plan-review ambiguity design-steps (`design-steps.md` → "Plan
review — ambiguity review", now checked) in `plan.md` + `steps.md`:

- `:kaocha-extra` is added to the derived `scry-managed-keys` set so the `-X`
  collection step never re-collects/nests it; the collection step `merge`s
  collected top-level extras into any pre-existing `:kaocha-extra` (collected
  wins on conflict), assoc only when non-empty. `-m` and `-X` are disjoint
  invocation paths so the conflict case is only an undocumented-but-tolerated
  explicit `-X` `:kaocha-extra`. Codified in plan "Plan-review resolutions" +
  Slice 1 steps (new merge test added).
- Core (`:clojure-test`) mode *rejects* `:kaocha-extra` by adding it to the
  `normalize-core-options` reject set (`src/scry/cli.clj:201`), yielding
  `:scry.cli/argument-error`. Codified in plan "Plan-review resolutions" +
  Slice 2 reject step (now names the concrete enforcement point + test).

For the implementer: `:kaocha-extra` does not yet exist in `src/scry/cli.clj`
(it is introduced by Slice 1). The reject-set line is currently
`(into kaocha-only-keys kaocha-fallback-keys)` at `src/scry/cli.clj:201`; the
message "Kaocha options require :runner :kaocha" already fits a Kaocha-only flag
reaching core mode, so reuse it rather than adding a new message.

## Plan review — ambiguity turn (2026-06-21, another shared plan-review session, first turn)

- No new ambiguity review feedback. Reviewed plan.md + steps.md (steps.md
  read-only) against `src/scry/cli.clj` (key sets, `normalize-core-options`/
  `normalize-kaocha-options`, `reject-keys`, `parse-main-args`) and
  `src-kaocha/scry/kaocha.clj` `run` pipeline. The 3 Open Questions are
  concretely resolved in plan "Open Question resolutions" (decisions, not
  ambiguities), and the prior plan-review ambiguity steps (`:kaocha-extra`
  exclusion+merge in the shared `normalize-kaocha-options`; core-mode reject via
  the `normalize-core-options` reject set) are executed/reflected in plan +
  steps. Considered-but-not-filed minor residuals: `-m` `--focus` vector-shape
  vs `-X` `:focus` scalar-shape divergence (deliberately absorbed by Slice 3
  "scalar or collection" coercion); `-X` stray top-level `:focus` in core mode
  silently ignored (matches unchanged baseline, "core mode unaffected"); and
  `--focus`/`--kaocha-opt focus` key collision (out-of-scope edge case). None
  block/mislead an implementer. Nothing new to file.

## Plan review — inconsistency turn (2026-06-21, another shared plan-review session, second turn)

- No new inconsistency review feedback. Used the loaded plan.md/steps.md context
  plus a targeted re-read confirming the `normalize-core-options` reject set is
  at `src/scry/cli.clj:201` (plan + steps both cite `:201` correctly; the
  checked design-steps ambiguity item's `:199` is a stale done-pointer, not an
  actionable cross-file inconsistency). plan.md and steps.md are mutually
  consistent: matching 6-slice order/content, OQ1/OQ2/OQ3 resolutions faithfully
  reflected in steps, consistent `:kaocha-extra` exclusion+merge and core-mode
  reject enforcement points, and a designed (not contradictory) `-X` scalar vs
  `-m` vector `:focus` shape asymmetry absorbed by Slice 3 coercion.
  Considered-but-not-filed: design Approach step 2's top-level `:focus` wording
  vs plan OQ3's `:kaocha/cli-options` routing — already considered-and-not-filed
  in a prior plan-review inconsistency turn with an implementer note (follow plan
  OQ3), so re-filing would duplicate. Nothing new to file. This new shared
  plan-review session (ambiguity + inconsistency turns) added zero new
  design-steps.

## After the second shared plan-review session (2026-06-21) — for the next lifecycle step

- This shared plan-review session (ambiguity + inconsistency turns) added
  **zero** new design-steps; plan.md and steps.md are converged and verified
  against current `src/scry/cli.clj` and `src-kaocha/scry/kaocha.clj`. No plan
  edits are pending — proceed to implementation starting at **Slice 1**.
- Do not re-derive guidance. The actionable principles and concrete file/line
  pointers are already captured in the earlier "For the slice addressing the
  plan-review design-steps" and "Plan-review follow-ups executed" notes; the two
  prior-session plan-review ambiguity steps are checked and codified in plan
  "Plan-review resolutions" + Slices 1–2.
- Implementer reminders confirmed this session (do not relitigate):
  `normalize-core-options` reject set is at `src/scry/cli.clj:201`; route
  `:focus` into `:kaocha/cli-options` (plan OQ3), not config top-level; keep the
  `-X` scalar vs `-m` vector `:focus` asymmetry — Slice 3 coercion normalizes
  both ("scalar or collection" → vector of keywords).

## Design review — architecture turn (2026-06-21, new shared design-review session, first turn)

- No new architectural review feedback. design.md unchanged since `9e5dec6`;
  reviewed against AGENTS.md (sole architecture authority — no META.md /
  doc/architecture.md). Architecturally coherent: (1) core↛Kaocha load boundary
  intact — `:kaocha-extra` raw data + key routing only in core `scry.cli`,
  value-type coercion in `scry.kaocha/run` (src-kaocha); (2) `:scry.cli/outcome-kind`
  contract preserved — asymmetric/bounded pass-through (`-m` opt-in, unknown
  flags still `:scry.cli/argument-error`; `-X` documented trade-off; core mode
  rejects `:kaocha-extra`); (3) Kaocha merge logic kept in src-kaocha under the
  optional alias; (4) API-doc regen constraint noted. The 3 Open Questions
  (OQ1/OQ2/OQ3) are plan/implementation decisions, not architectural misfits.
  Nothing new to file. (Plan/steps not reviewed — design-only turn.)

## Design review — ambiguity turn (2026-06-21, new shared design-review session, second turn)

- No new ambiguity review feedback. Used already-loaded design.md (unchanged
  since `9e5dec6`) + architecture-turn context from this session. Design is
  precise modulo its 3 explicit Open Questions (OQ1 named-vs-generic `-m`
  surface; OQ2 `:config` reject-vs-merge; OQ3 `:focus` key mapping), surfaced as
  open choices not latent ambiguities. Prior ambiguity steps (exclusion-set
  enumeration; `-m` value-coercion spec) and the architecture coercion-placement
  step are executed/reflected. Nothing new to file. (Plan/steps not reviewed —
  design-only turn.)

## Design review — inconsistency turn (2026-06-21, new shared design-review session, third turn)

- No new inconsistency review feedback. Used already-loaded design.md (unchanged
  since `9e5dec6`) + this session's architecture/ambiguity-turn context. The 3
  prior inconsistency steps (Context `-X` "silently dropped" vs `-m`
  "Unknown option"; `:config` authoritative-vs-merge wording; Acceptance cmd1
  `--focus` vs OQ1) are executed/reflected. Approach step 2's declarative
  `:focus`-config wording is self-qualified by step 4 as OQ3 — co-located, not a
  latent contradiction; already considered-and-not-filed in prior turns, so not
  re-filed. Nothing new to file. (Plan/steps not reviewed — design-only turn.)
  This shared design-review session (all three turns) added zero new design-steps;
  design remains converged and stable.

## After the new shared design-review session (2026-06-21) — for the next lifecycle step

- This shared design-review session (architecture + ambiguity + inconsistency)
  added **zero** new design-steps. No new follow-ups exist to address. design.md
  is unchanged since `9e5dec6` and remains converged; all design-steps stay
  checked. Plan/steps already passed two shared plan-review sessions
  (also zero new steps) — proceed to implementation starting at **Slice 1**.
- Do not re-derive guidance. Actionable principles + concrete file/line pointers
  are already captured above; start from these notes (do not re-read from
  scratch):
  - "For the slice addressing these design-steps" (boundary, outcome-kind,
    API-docs principles).
  - "Plan-review follow-ups executed" + "For the slice addressing the
    plan-review design-steps" (`:kaocha-extra` exclusion/merge; core-mode reject
    at `src/scry/cli.clj:201`; cli.clj + src-kaocha/scry/kaocha.clj line refs).
  - "After the second shared plan-review session" (route `:focus` into
    `:kaocha/cli-options`, not config top-level; keep `-X` scalar vs `-m` vector
    `:focus` asymmetry, normalized by Slice 3 coercion).
- The 3 Open Questions are already resolved in plan.md "Open Question
  resolutions" (OQ1 named `--focus`; OQ2 `:config` precedence; OQ3
  `:kaocha/cli-options`) — implementation follows plan.md, not further design review.

## Plan review — ambiguity turn (2026-06-21, third shared plan-review session, first turn)

- No new ambiguity review feedback. Verified plan.md/steps.md against current
  `src/scry/cli.clj` (key sets `:36-38`, `reject-keys` `:177`,
  `normalize-core-options` reject set `:201`, `normalize-kaocha-options` `:209`,
  `parse-main-args` flag clauses + unknown-flag `argument-error` default) and
  `src-kaocha/scry/kaocha.clj` `run` pipeline (`resolve-config` → `select-suites`
  → `apply-runtime-defaults` → `apply-progress-reporter` → `api/run`); all line
  refs accurate. The two prior plan-review ambiguity steps (`:kaocha-extra`
  exclusion/merge; core-mode reject) are executed/codified in plan "Plan-review
  resolutions" + Slices 1–2; OQ1/OQ2/OQ3 are resolved decisions, not ambiguities.
  Weighed-but-not-filed (already-considered or non-actionable): designed `-X`
  scalar vs `-m` vector `:focus` shape asymmetry (Slice 3 "scalar or collection"
  coercion); `--focus`/`--kaocha-opt focus` key collision (out-of-scope edge);
  `:config`-authoritative conflict scoped uniformly at `:kaocha/cli-options`
  across all three resolve-config paths; merge slot order-independent vs
  `apply-runtime-defaults` (disjoint config keys). Nothing blocks/misleads an
  implementer. Nothing new to file.

## Plan review — inconsistency turn (2026-06-21, third shared plan-review session, second turn)

- No new inconsistency review feedback. Used loaded plan.md/steps.md context from
  this session's ambiguity turn. plan.md and steps.md are mutually consistent:
  matching 6-slice order/content, consistent `:kaocha-extra` exclusion+merge
  ("assoc only when non-empty") and core-mode reject point (`:201`), and OQ1/OQ2/OQ3
  resolutions faithfully reflected in steps. The `-X` scalar vs `-m` vector `:focus`
  shape asymmetry is reconciled by Slice 3 coercion (designed, not contradictory).
  Considered-but-not-filed (duplicates of prior turns): design-steps `:199` stale
  done-pointer vs `:201` (non-actionable), design Approach step 2 top-level `:focus`
  vs plan OQ3 `:kaocha/cli-options` (self-qualified by step 4; implementer note
  already recorded). This third shared plan-review session (ambiguity + inconsistency
  turns) added zero new design-steps; plan/steps remain converged — proceed to
  implementation at Slice 1.

## Implementation — Slices 1–3 (2026-06-21)

Implemented core pass-through collection, `-m` flags, and adapter merge/coercion.

- **`scry.cli` (`src/scry/cli.clj`).**
  - Added derived `scry-managed-keys` set: `#{:runner :result-format
    :progress-callback :kaocha-extra :dirs}` ∪ `core-only-keys` ∪
    `kaocha-only-keys` ∪ `kaocha-fallback-keys`.
  - `normalize-kaocha-options` now wraps the existing `cond->` in a `let`,
    collects non-scry-managed top-level keys via
    `(remove #(contains? scry-managed-keys (key %)) opts)`, merges them over any
    pre-existing `:kaocha-extra` (collected wins), and assocs `:kaocha-extra`
    only when non-empty.
  - `normalize-core-options` reject set now includes `:kaocha-extra` (via
    `conj`), so Kaocha-only flags in core mode raise `:scry.cli/argument-error`.
  - `parse-main-args`: added `--focus` (accumulates a vector under
    `:kaocha-extra :focus`, repeatable) and `--kaocha-opt KEY VALUE` (assoc-in
    `(keyword KEY) -> raw VALUE`). Unknown flags still hit the argument-error
    default. `:kaocha-extra` survives `main-opts->exec-opts` (not in its dissoc).
  - usage text documents `--focus SYM` and `--kaocha-opt KEY VALUE`.

- **`scry.kaocha` (`src-kaocha/scry/kaocha.clj`).**
  - Added `clojure.string` require.
  - Added `->focus-keyword` / `coerce-focus` (raw string/symbol/keyword, scalar
    or collection → vector of keywords; strips a leading `:` on strings to
    mirror the filter plugin's `--focus` `:parse-fn`).
  - Added `coerce-kaocha-extra` (coerces `:focus`; forwards other keys raw) and
    `apply-kaocha-extra` (merges coerced extra into `:kaocha/cli-options` with
    existing config-authoritative: `(merge coerced existing)`).
  - Wired `apply-kaocha-extra` into the `run` pipeline after
    `apply-runtime-defaults`, before `apply-progress-reporter`.
  - **Discovery / deviation:** the synthetic fallback config and bare explicit
    `:config` maps do NOT carry Kaocha's default plugin chain (no
    `:kaocha.plugin/filter`), so `:focus` had no effect. Generalized the
    capture-output ensure helper into `ensure-plugin` + `ensure-runtime-plugins`
    (ensures BOTH `:kaocha.plugin/capture-output` and `:kaocha.plugin/filter`).
    This addresses the plan's "Filter plugin presence" risk. Existing
    `runtime-defaults-test` / `full-config-selection-and-preservation-test`
    plugin-list assertions updated to include the filter plugin.
  - `run` docstring documents `:kaocha-extra`.

- **Verification:** focused `scry.cli-test` (44 tests, 323 assertions, green);
  focused `scry.kaocha-test` (14 tests, 71 assertions, green). Confirmed real
  filtering: focusing `:scry.fixtures.mixed/pass-then-fail` reduces executed
  vars from 2 to 1.

Next slice (4): CLI integration tests in `scry.cli-kaocha-test` exercising `-m`
and `-X` acceptance end-to-end; then docs (Slice 5) + final verification (6).

## Implementation — Slices 4–6 (2026-06-21)

- **Slice 4 (CLI integration).** Added `kaocha-cli-focus-pass-through-test` in
  `test/scry/cli_kaocha_test.clj`: a tests.edn project with two vars
  (`keep-test` passing, `drop-test` failing); unfocused run exits 1, while both
  `-m --focus <var>` and `-X :focus "<var>"` exit 0 and execute only the focused
  var (`:canonical-results` = `[keep-var]`). Existing
  `kaocha-cli-suite-run-test` / `-explicit-config-run-test` / `-fallback-dirs-test`
  still cover `--suite`/`--config`/`--dirs`; core-mode reject covered in cli-test.

- **Slice 5 (docs).** Regenerated `doc/API.md` (`scry.kaocha/run` now documents
  `:kaocha-extra`); `bb api-docs --check` clean; `scry.api-docs-test` green
  (58 assertions). Updated `README.md` Kaocha CLI section with `--focus`,
  `--kaocha-opt`, the `-X` top-level pass-through, the `-X` mistyped-key
  trade-off, and config-authoritative merge + `:focus` coercion note.

- **Slice 6 (final verification), all green:**
  - `scry.cli-test` — 44 tests / 323 assertions.
  - `scry.kaocha-test` + `scry.cli-kaocha-test` — 19 tests / 107 assertions.
  - core slice (`scry.capture-test scry.clojure-test-test scry.cli-test`) via
    `scry/run` — 50 vars / 495 assertions, `:pass? true`.
  - `scry.api-docs-test` — 1 test / 58 assertions.
  - `bb clj-fmt:check` — all formatted; `bb clj-kondo:lint` — 0 errors/warnings.
  - Real acceptance commands in the scry project (no tests.edn → synthetic
    fallback): `clojure -M:test:kaocha -m scry.cli --runner kaocha --focus
    scry.cli-test/parse-main-args-test` and the `-X` equivalent both run only the
    focused var (Tests: 1 passed) and exit 0.

Task complete: all six slices implemented and verified.

## Implementation review (2026-06-21)

- Reviewed code against design/plan: matches OQ1/OQ2/OQ3 resolutions, respects
  the core↛Kaocha load boundary (raw collection in `scry.cli`, coercion/merge in
  `scry.kaocha/run`), preserves the `:scry.cli/outcome-kind` contract, reuses
  existing patterns (`reject-keys`/`add-repeat`/`ensure-plugin`), no unnecessary
  abstractions. Tests assert real filtering, config-authoritative merge,
  core-mode reject, and `-m`/`-X` acceptance end-to-end.
- Independently re-verified green: `scry.cli-test`/`scry.kaocha-test`/
  `scry.cli-kaocha-test` (63 tests, 430 assertions), `bb clj-fmt:check`,
  `bb clj-kondo:lint` (0/0), `bb api-docs --check`.
- No actionable issues found; added 0 follow-up steps.

## Test review (2026-06-21)

- Added 1 follow-up step: synthetic-fallback path focus filtering is verified
  only by a manual acceptance command, not an automated test.

## Test review follow-up execution (2026-06-21)

- Addressed 1 test-review follow-up step: added
  `no-tests-edn-fallback-focus-filters-execution-test` in
  `test/scry/kaocha_test.clj`, locking in synthetic-fallback focus filtering
  (no tests.edn, no explicit `:config`, caller `:test-paths`/`:ns-patterns`,
  `:kaocha-extra {:focus [...]}`). Added `write-mixed-test-ns` (two failing
  vars so suite-scope `:results` reflects the executed var set) + `ns->test-path`
  helper. Asserts executed `:var` set and `:summary :var-count` reduce from 2→1
  under focus, exercising the `ensure-runtime-plugins` filter-plugin-ensure on
  the fallback path.
- Verified: focused `scry.kaocha-test` green (15 tests / 75 assertions);
  `bb clj-fmt:check` clean; `bb clj-kondo:lint` 0/0.

## Implementation review (independent, 2026-06-21)

- Re-verified green: `scry.cli-test`/`scry.kaocha-test`/`scry.cli-kaocha-test`
  (64 tests / 434 assertions), `bb clj-fmt:check` clean, `bb clj-kondo:lint`
  0/0. Code matches design/plan (OQ1/OQ2/OQ3), respects the core↛Kaocha load
  boundary, preserves the `:scry.cli/outcome-kind` contract, reuses existing
  patterns, no unnecessary abstractions. Added 0 follow-up steps.

## Test review (2026-06-21, second pass)

- Added 2 follow-up steps: the committed OQ1 generic `--kaocha-opt` mechanism is
  only tested at the parse level for a single invocation (no multi-flag
  accumulation test; no end-to-end coverage reaching the Kaocha runner, unlike
  `--focus`).

## Test review second-pass follow-up execution (2026-06-21)

- Addressed 2 review follow-up steps (generic `--kaocha-opt` coverage gaps).
- Added `repeated --kaocha-opt accumulates distinct keys` parse test in
  `test/scry/cli_test.clj` (asserts `--kaocha-opt a 1 --kaocha-opt b 2` →
  `:kaocha-extra {:a "1" :b "2"}`), locking in the `assoc-in` accumulation path.
- Added `kaocha-cli-kaocha-opt-generic-pass-through-test` in
  `test/scry/cli_kaocha_test.clj`: `-m --runner kaocha --kaocha-opt focus <var>`
  filters execution to the focused var end-to-end, covering raw-string `:focus`
  coercion arriving via the generic flag.
- Verified: `scry.cli-test` (44 tests, 324 assertions, 0 fail/err),
  `scry.cli-kaocha-test` (6 tests, 39 assertions, 0 fail/err) with `:kaocha`,
  `bb clj-fmt:check` clean, `bb clj-kondo:lint` 0 errors/warnings.

## Test review (2026-06-21, third pass)

- Added 1 follow-up step: the boundary no-leak test only spot-checks 4 of the
  scry-managed keys; the closed set (notably the function-valued
  `:progress-callback`) is not fully asserted excluded from `:kaocha-extra`.

## Test review third-pass follow-up execution (2026-06-21)

- Addressed 1 review follow-up step (broaden boundary no-leak assertion to the
  full `scry-managed-keys` closed set).
- Extended `normalize-exec-opts-kaocha-pass-through-test` in
  `test/scry/cli_test.clj` with a "full scry-managed closed set never leaks"
  case passing `:result-format`, `:progress-callback` (fn), `:source-paths`,
  `:ns-patterns`, `:config`, `:suites`, and one unknown key. Asserts only the
  unknown key lands in `:kaocha-extra`, each managed key routes to its
  normalized destination, and `:progress-callback` is excluded (added later in
  the run pipeline, never by normalization). Excluded core-only selectors
  (rejected earlier) and `:dirs` (conflicts with explicit `:config`).
- Verified: `scry.cli-test` (44 tests, 332 assertions, 0 fail/err),
  `bb clj-fmt:check` clean, `bb clj-kondo:lint` 0 errors/warnings.

## Test review (2026-06-21, fourth pass)

- Added 1 follow-up step: the api-docs content contract (`scry.api-docs-test`)
  does not lock the new `:kaocha-extra` `scry.kaocha/run` documentation, unlike
  the detailed `scry.cli/run` section assertions.

## Test review fourth-pass follow-up execution (2026-06-21)

- Addressed 1 review follow-up step: locked the `:kaocha-extra` public surface
  in the api-docs content contract. Added `kaocha-run-section`
  (`var-section markdown "scry.kaocha" "run"`, mirroring `cli-run-section`) and
  `assert-includes` fragments in the optional `scry.kaocha` surface testing
  block (`:kaocha-extra`, "raw Kaocha cli-options forwarded", `:kaocha/cli-options`,
  "resolved :config authoritative on conflict", ":focus coercion → vector of
  keywords", and the mistyped-key runner/load-error trade-off).
- Verified: `scry.api-docs-test` green (1 test / 65 assertions, was 58);
  `bb clj-fmt:check` clean; `bb clj-kondo:lint` 0/0.

## Test review (2026-06-21, fifth pass)

- Added 0 follow-up steps. All design behaviours have automated coverage
  (`-m`/`-X` focus + generic `--kaocha-opt` E2E, multi-flag accumulation,
  coercion shapes, config-authoritative merge, focus filtering across tests.edn
  / explicit `:config` / synthetic-fallback paths, core-mode reject, full
  closed-set boundary no-leak, existing `--suite`/`--config`/`--dirs`, api-docs
  content contract); tests use boundary injection + real Kaocha, no mocks/stubs.
  The only residual candidate (`--kaocha-opt` core-mode reject) shares the exact
  `:kaocha-extra` `reject-keys` enforcement point already tested via `--focus`,
  so it is redundant rather than new signal.

## Test review (2026-06-21, sixth pass)

- Added 0 follow-up steps. Tests across cli/cli-kaocha/kaocha namespaces are
  single-concern, behavior-focused (assert real focus filtering, not key
  presence), use boundary injection + real Kaocha (no mocks), and cover the
  distinct tests.edn / explicit-`:config` / synthetic-fallback paths plus
  coercion shapes, config-authoritative merge, full closed-set boundary
  no-leak, `-m`/`-X` + generic `--kaocha-opt` E2E, and the api-docs content
  contract. The lone residual (mistyped `-X` key → runner/load-error) is a
  documented Kaocha behavior trade-off, not a scry contract worth locking.
