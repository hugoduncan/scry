# Design review follow-ups

## Architecture review

- [x] Specify how generic/arbitrary Kaocha option pass-through preserves the
  CLI's existing architectural invariants: explicit option validation at the
  CLI boundary and clean `:scry.cli/outcome-kind` classification. Today
  `parse-main-args` rejects unknown `-m` flags via `argument-error`
  (`:scry.cli/argument-error`) and `normalize-*-options` reject cross-mode
  keys, so invalid input is classified as an argument error rather than
  reaching the runner. A blanket "collect every unrecognized key into
  `:kaocha-extra` and forward to Kaocha" approach inverts that boundary into
  default-forward: typos/invalid options bypass argument validation and surface
  as `:scry.cli/runner-error`/`:scry.cli/load-error` (or are silently dropped on
  the `-X` map path). The design should state whether pass-through is bounded
  (explicit `--kaocha-opt`/named-flag opt-in that keeps unknown `-m` flags
  rejected) and how/whether `-X` map pass-through stays distinguishable from
  argument errors, so the CLI's structured error-classification contract is not
  eroded. (Mechanism is already an Open Question; this item is about the
  architectural impact on the validation boundary and `outcome-kind` contract,
  not the named-vs-generic choice itself.)
- [x] Resolve the layering tension in where `-m` per-option value coercion
  lives, relative to the core↛Kaocha dependency boundary. Approach step 4 places
  coercion in core `scry.cli` ("coerced to the type Kaocha expects for each
  option *before being placed in* `:kaocha-extra`"), but coercing to the
  Kaocha-expected type is Kaocha-domain knowledge that the architecture keeps in
  `src-kaocha/` (AGENTS.md: `scry.cli` must not require `scry.kaocha` at load
  time; Kaocha support belongs under `src-kaocha/`). This also conflicts with
  Open Question 3, which points at `kaocha.config/apply-cli-args` — a Kaocha API
  resident in `src-kaocha/scry/kaocha.clj` — as the natural interpreter of
  raw CLI option strings. The design should state which side of the boundary
  performs Kaocha-type coercion: keep `:kaocha-extra` as raw forwarded data and
  let `scry.kaocha/run` (src-kaocha, possibly via `apply-cli-args`) interpret
  it, versus embedding per-Kaocha-option type knowledge in core `scry.cli`.
  (Distinct from the resolved value-coercion ambiguity item below, which asks
  *whether/how* values are coerced; this item is about *where* that logic sits
  relative to the architectural boundary.)

## Ambiguity review

- [x] Precisely enumerate the closed "known scry-managed set" excluded from
  pass-through. Approach step 1 describes it loosely as "core-only keys, known
  kaocha keys, and shared keys *like* `:result-format`"; the "like" leaves the
  exact set undefined. The design should list every recognized/excluded key —
  including `:runner`, `:result-format`, `:progress-callback`, and each known
  kaocha key (`:suite`, `:suites`, `:config`, `:dirs`, `:source-paths`,
  `:test-paths`, `:ns-patterns`) — so scry-internal and mode-selector keys are
  never accidentally forwarded into the Kaocha config as `:kaocha-extra`.
- [x] Specify how pass-through option *values* are typed/coerced on the `-m`
  path, where every value arrives as a raw string (unlike typed `-X` EDN values
  such as `:focus "my.ns/test-foo"`). The acceptance requires
  `--focus my.ns/test-foo` to actually focus, which depends on the value
  reaching Kaocha in the expected type. (Distinct from Open Question 3, which
  concerns *key* mapping rather than *value* coercion; this applies to arbitrary
  `-m` pass-through values, not only `:focus`.)

## Inconsistency review

- [x] Correct the Context's claim that unknown options are "silently dropped
  during normalization" with "no effect". That holds only for the `-X` map path
  (`normalize-kaocha-options`'s `cond->`). The `-m` path (`parse-main-args`)
  rejects unknown flags with `argument-error` ("Unknown option: <flag>"), so the
  acceptance command `clojure -M:test:kaocha -m scry.cli ... --focus
  my.ns/test-foo` would currently *error*, not no-op. The design should reflect
  that `-m` pass-through requires explicit flag parsing to clear the
  "Unknown option" rejection, not just config forwarding.
- [x] Reconcile the internal tension between the Constraint that explicit
  `:config` "must still take full precedence" and the offered "merged" option
  for `:config` + pass-through (stated both in Constraints and Open Questions).
  "Full precedence" and "merge pass-through keys into the config" conflict;
  clarify whether `:config` wins only on conflicting keys (merge adds the rest)
  or is wholly authoritative (pass-through rejected when `:config` is supplied).
- [x] Reconcile Acceptance command 1
  (`-m scry.cli --runner kaocha --focus my.ns/test-foo`) with Open Question 1.
  The acceptance criterion hard-codes a named `--focus` flag on the `-m` path,
  but OQ1 leaves the `-m` pass-through surface unresolved between named flags
  and a generic `--kaocha-opt KEY VALUE` mechanism. If OQ1 resolves to
  generic-only, `--focus` as a bare flag would not exist and command 1 would
  have to be e.g. `--kaocha-opt focus my.ns/test-foo`, so the acceptance
  criterion and the open `-m` surface choice currently contradict. The design
  should either commit OQ1 to providing (at least) a named `--focus` flag, or
  restate Acceptance command 1 to track whichever `-m` mechanism OQ1 selects.
  (Acceptance command 2's `-X :focus` is unaffected: top-level `:focus` is
  auto-forwarded as it falls outside the scry-managed key set.)
