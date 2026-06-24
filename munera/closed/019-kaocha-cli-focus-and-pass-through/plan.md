# 019: Plan — Kaocha CLI --focus and pass-through options

## Status of design

`design.md` is stable and converged: all design-review follow-ups in
`design-steps.md` are checked, and the shared design-review sessions recorded in
`implementation.md` added zero new steps. The remaining decisions are the three
Open Questions, which `design.md` itself defers to plan/implementation. This
plan resolves them concretely (see "Open Question resolutions"), so no design
edits are pending and implementation can proceed.

## Open Question resolutions

These were left open in `design.md` by intent. This plan commits them:

- **OQ1 (`-m` pass-through surface).** Provide *both* a named `--focus` flag
  (the discoverable headline; satisfies Acceptance command 1) *and* a generic
  `--kaocha-opt KEY VALUE` mechanism for arbitrary bounded opt-in pass-through.
  Both route into `:kaocha-extra`. Unknown bare `-m` flags continue to be
  rejected with `:scry.cli/argument-error` (the validation boundary is
  preserved). `--focus-only`/`--exclude` named flags are *not* added now; users
  reach them via `--kaocha-opt`. Rationale: `--focus` is the motivating feature
  and stays discoverable, while `--kaocha-opt` keeps the surface bounded without
  per-option maintenance.

- **OQ2 (`:config` + pass-through).** **Merge**, with `:config` authoritative on
  conflict. Pass-through fills only keys absent from `:config`; `:config` keys
  win on any overlap. Concretely, `:kaocha-extra` merges into the resolved
  config's `:kaocha/cli-options` such that values already present from `:config`
  are never overridden. This satisfies the "`:config` is authoritative"
  constraint while keeping the convenience path. Pass-through is **not** rejected
  when `:config` is present.

- **OQ3 (`:focus` interpretation / value coercion).** Route `:kaocha-extra` into
  the resolved config's `:kaocha/cli-options` map and let Kaocha's own filter
  plugin `:kaocha.hooks/config` step (run inside `kaocha.api/run`) translate
  `:focus` → `:kaocha.filter/focus`. The filter plugin is a Kaocha default
  plugin and reads `:kaocha/cli-options`. Per-option value coercion lives in
  `scry.kaocha/run` (src-kaocha), keeping core↛Kaocha value-type knowledge out
  of `scry.cli`: coerce `:focus` raw values (string / symbol / keyword, scalar
  or collection) into a vector of keywords — the same shape the filter plugin's
  `--focus` `:parse-fn`/`:assoc-fn` produce. This makes `-m`
  `--focus my.ns/test-foo` and `-X` `:focus "my.ns/test-foo"` both reach Kaocha
  as `:kaocha.filter/focus [:my.ns/test-foo]`. Unknown `:kaocha-extra` keys are
  forwarded into `:kaocha/cli-options` as-is (raw), accepting the `-X`
  mistyped-key trade-off documented in `design.md`.

## Approach

Implement bounded, asymmetric pass-through in three layers, preserving the
core↛Kaocha load-time boundary and the `:scry.cli/outcome-kind` contract.

1. **Core collection (`scry.cli`, both entry paths).** Define the closed
   scry-managed key set and collect everything else into a normalized
   `:kaocha-extra` map. `:kaocha-extra` is *raw forwarded data*: `scry.cli`
   performs only key-level routing (its existing behaviour), never per-Kaocha
   option value-type coercion.
   - `-X` map path (`normalize-kaocha-options`): top-level keys outside the
     scry-managed set become `:kaocha-extra` entries (typed EDN values kept
     as-is) instead of being dropped, merged into any pre-existing
     `:kaocha-extra` map (see "Plan-review resolutions" for exclusion + merge).
   - `-m` flag path (`parse-main-args`): add a named `--focus` flag and a
     generic `--kaocha-opt KEY VALUE` flag, both accumulating into a raw
     `:kaocha-extra` map carried through `main-opts->exec-opts`. Unknown flags
     still hit the `argument-error` default branch. In `:clojure-test` (core)
     mode these Kaocha-only flags are rejected via the `:kaocha-extra` addition
     to the core-mode reject set (see "Plan-review resolutions").

2. **Adapter merge + coercion (`scry.kaocha/run`, src-kaocha).** Merge
   `:kaocha-extra` into the resolved config's `:kaocha/cli-options` with
   `:config` authoritative on conflict (OQ2), coercing known raw values (OQ3:
   `:focus` → vector of keywords) before merge. Document `:kaocha-extra` in the
   `run` docstring.

3. **Docs.** Regenerate `doc/API.md` if the `scry.kaocha/run` public surface
   (docstring/options) changes; update `README.md` for the new CLI options and
   the `-X` mistyped-key trade-off.

### Scry-managed key set (never forwarded)

`:runner`, `core-only-keys` (`:namespaces`, `:vars`, `:ns-pattern`,
`:namespace-pattern`, `:namespace-regex`), `:result-format`,
`:progress-callback`, and the explicitly handled Kaocha keys `:suite`,
`:suites`, `:config`, `:dirs`, `:source-paths`, `:test-paths`, `:ns-patterns`.
Implement as a derived set so it stays in sync with the existing
`core-only-keys`/`kaocha-only-keys`/`kaocha-fallback-keys` definitions in
`scry.cli`.

`:kaocha-extra` is **also** added to this derived set, but for a different
reason than the keys above: it is not scry-internal data to drop, it is the
forwarded payload container itself. On the `-m` path `parse-main-args`
pre-builds a `:kaocha-extra` map that arrives in `normalize-kaocha-options` as a
top-level opts key; including it in the excluded set prevents the `-X`
collection step from re-collecting it into a nested `:kaocha-extra`. See
"Plan-review resolutions" for the exclusion + merge rule.

### Plan-review resolutions (`:kaocha-extra` exclusion, merge, core-mode disposition)

Two plan-review ambiguities about the shared `normalize-kaocha-options` and
core-mode reject paths are resolved here; Slice 1 and Slice 2 implement them.

- **`:kaocha-extra` exclusion + merge rule (`-X` collection).** Add
  `:kaocha-extra` to the derived `scry-managed-keys` set (above) so the `-X`
  collection step never re-collects an already-present `:kaocha-extra` map into a
  nested `:kaocha-extra`. The collection step builds a map from the remaining
  non-scry-managed top-level keys, `merge`s it into any pre-existing
  `:kaocha-extra` map, and `assoc`s the combined `:kaocha-extra` onto
  `normalized` only when non-empty. Collected top-level extras win on key
  conflict. In practice the two sources never conflict: `-m` and `-X` are
  distinct, non-overlapping invocation paths — the `-m` path supplies only a
  pre-built `:kaocha-extra` map with no scattered top-level extras, and the
  documented `-X` surface is scattered top-level keys (e.g.
  `:focus "my.ns/test-foo"`) with no explicit `:kaocha-extra` map. An explicit
  `-X` `:kaocha-extra` map is undocumented but tolerated by this merge.

- **Core-mode (`:clojure-test`) disposition — reject.** Core mode *rejects*
  `:kaocha-extra`, matching the `:scry.cli/outcome-kind` contract and the "core
  mode unaffected" constraint. Add `:kaocha-extra` to the existing
  `normalize-core-options` reject set (`reject-keys` over
  `kaocha-only-keys ∪ kaocha-fallback-keys`, `src/scry/cli.clj:201`) rather than
  a bespoke check, so `--runner clojure-test --focus …` (or `--kaocha-opt …`)
  surfaces as `:scry.cli/argument-error` ("Kaocha options require :runner
  :kaocha") instead of being silently ignored. The `-m` flags populate
  `:kaocha-extra` before `normalize-exec-opts` dispatches on `:runner`, so a
  Kaocha-only flag can reach the core branch; this reject is what makes Slice 2's
  "rejected in core mode" claim concrete.

## Key file locations (from entity-resolution + design-review notes)

- `scry.cli` recognized key sets — `src/scry/cli.clj:34-37`.
- `normalize-kaocha-options` — `src/scry/cli.clj:209`.
- `parse-main-args` (unknown-flag default branch) — `src/scry/cli.clj:279`+.
- `main-opts->exec-opts` — `src/scry/cli.clj` (just above `parse-main-args`).
- `classify-outcome` / `error-outcome-kind` — `src/scry/cli.clj:442` / `:467`.
- `scry.kaocha/run` config pipeline — `src-kaocha/scry/kaocha.clj:270`
  (`resolve-config` → `select-suites` → `apply-runtime-defaults` →
  `apply-progress-reporter` → `kaocha.api/run`). `:kaocha-extra` merge belongs in
  this pipeline (after `resolve-config`, alongside/after `apply-runtime-defaults`).
- Tests: `test/scry/cli_test.clj` (core CLI), `test/scry/cli_kaocha_test.clj`
  and `test/scry/kaocha_test.clj` (require `:kaocha` alias).

## Risks

- **Filter plugin presence.** The `:kaocha/cli-options` → `:kaocha.filter/focus`
  translation depends on Kaocha's default `:kaocha.plugin/filter` plugin being
  in the plugin chain. The synthetic fallback config built by `build-fallback-config`
  does not list plugins explicitly; verify the filter plugin is active for the
  tests.edn path, the synthetic-fallback path, and the explicit `:config` path.
  If absent on any path, add it the way `apply-runtime-defaults` already ensures
  `:kaocha.plugin/capture-output`.
- **Coercion shape drift.** `:kaocha.filter/focus` must be a collection of
  keywords matching testable ids. If the coercion target shape is wrong, focus
  silently matches nothing (Kaocha warns ":focus … did not match any tests").
  Tests must assert tests are *actually* filtered, not just that the key is set.
- **Boundary regression.** Adding `:kaocha-extra` collection must not let
  scry-managed/mode keys leak; assert the closed set is excluded. Keep all
  value-type coercion in src-kaocha so `scry.cli` retains no load-time Kaocha
  dependency.
- **Outcome-kind regression.** Unknown `-m` flags must still classify as
  `:scry.cli/argument-error`; `-X` mistyped keys are an accepted trade-off and
  must be documented, not "fixed".
- **API-doc gate.** Changing `scry.kaocha/run`'s docstring/options triggers the
  `bb api-docs --check` + api-docs regression test gate.

## Slice order

1. **Core `-X` pass-through collection** — scry-managed key set +
   `:kaocha-extra` in `normalize-kaocha-options`; unit tests on normalized opts.
2. **Core `-m` opt-in flags** — `--focus` and `--kaocha-opt KEY VALUE`,
   carried through `main-opts->exec-opts`; unknown-flag rejection preserved;
   unit tests.
3. **Adapter merge + `:focus` coercion** — `scry.kaocha/run` merges
   `:kaocha-extra` into `:kaocha/cli-options` (config-authoritative), coerces
   `:focus`; verify filter plugin presence across config paths; adapter tests
   asserting real filtering.
4. **CLI Kaocha integration + acceptance** — `cli_kaocha_test` coverage and the
   two acceptance commands (`-m` and `-X`); confirm existing `--suite`/
   `--config`/`--dirs` still work and core mode is unaffected.
5. **Docs** — regenerate `doc/API.md`, run `bb api-docs --check` + api-docs
   regression test; update `README.md` (new CLI options, `-X` trade-off).
6. **Final verification** — run focused core CLI, Kaocha adapter, and Kaocha CLI
   command-line checks; record commands/results in `implementation.md`.

## Open ambiguities (noted, non-blocking)

- The precise coercion contract for non-`:focus` `:kaocha-extra` keys is
  intentionally "forward raw into `:kaocha/cli-options`"; only `:focus` (and,
  cheaply, the sibling `:skip`/`:focus-meta`/`:skip-meta`, if free) gets typed
  coercion. Other Kaocha options that need non-keyword coercion are out of scope
  for this task and would surface as runner/load errors on misuse — the accepted
  `-X` trade-off, also applicable to generic `--kaocha-opt` values.
