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
