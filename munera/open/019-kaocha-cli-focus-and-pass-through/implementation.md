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
