# Implementation notes

## Slice 0 — spike: Kaocha argv parse entry point (OQ2)

Confirmed the reusable parse path mirrors `kaocha.runner/-main*`:

- Base option spec is the private var `kaocha.runner/cli-options`
  (`@(requiring-resolve 'kaocha.runner/cli-options)`; `requiring-resolve`
  resolves private vars by fully-qualified symbol).
- Plugins extend it via the `:kaocha.hooks/cli-options` hook:
  `(kaocha.plugin/run-hook* (kaocha.plugin/load-all (:kaocha/plugins cfg))
   :kaocha.hooks/cli-options base-spec)`. The `:kaocha.plugin/filter` plugin
  adds `--focus` (parsed to a vector of keywords), `:kaocha.plugin/randomize`
  adds `--[no-]randomize`.
- `clojure.tools.cli/parse-opts argv full-spec` →
  `{:options ... :arguments ... :errors ...}`. `:arguments` are the positional
  suite-selector strings; convert with `kaocha.runner/parse-kw` to keywords and
  route through the adapter's existing `select-suites`.
- Verified: `["--focus" "my.ns/test-foo" "unit" "--no-randomize"]` →
  `{:options {:config-file "tests.edn" :focus [:my.ns/test-foo]} :arguments
  ["unit"] :errors nil}` once the randomize plugin is in the chain (errors when
  it is absent, e.g. the synthetic fallback config — acceptable per scope).
- Decisions: drop the always-present `:config-file` default from forwarded
  cli-options; merge the remaining parsed options through the existing
  `apply-kaocha-extra` (config cli-options authoritative on conflict). Forwarded
  parse uses the plugin chain from the resolved base config, so the standard
  Kaocha flag surface is available when a tests.edn/default config is loaded.

## Slice 1 — core `scry.cli` runner-aware `-m` forwarding

- `argv-runner` pre-pass scans for `--runner`/`-r` (lenient `help-runner`) so the
  loop knows the runner before per-token parsing.
- In Kaocha mode, the `parse-main-args` default branch collects every non-scry
  token (unknown `--flags`, their values, positional suite names) in order into
  `:kaocha-argv`. Core mode default branch unchanged (positional→`:suite-values`,
  unknown `--flag`→argument-error).
- Removed `--focus` and `--kaocha-opt` branches. Core-only selectors
  (`--namespace`/`--ns`/`-n`, `--var`/`-v`, `--ns-pattern…`) keep their branches
  and stay parsed-then-rejected by `normalize-kaocha-options`.
- `:kaocha-argv` added to `scry-managed-keys` so the `-X` extra-collection step
  never scoops it; `normalize-kaocha-options` threads it through explicitly.

## Slice 2 — adapter `:kaocha-argv` parsing

- `parse-kaocha-argv` builds the option spec via `kaocha-cli-option-spec`
  (`@(requiring-resolve 'kaocha.runner/cli-options)` + plugins'
  `:kaocha.hooks/cli-options`) and `clojure.tools.cli/parse-opts`. Returns
  `{:cli-options (dissoc options :config-file) :suites <parse-kw'd positionals>}`.
- The spec plugin chain is the union of the resolved config's plugins **and**
  Kaocha's default plugins (`default-cli-spec-plugins`: randomize, filter,
  capture-output) so the standard flag surface is always parseable even for the
  synthetic fallback config (which carries no plugins). A flag still only takes
  effect if its plugin is active during the run.
- `run` threading: `base-cfg = (resolve-config opts)`; parse argv against
  base-cfg; `selectors = (concat (suite-selectors opts) (:suites parsed))` →
  `select-suites`; forwarded `:cli-options` merged through the existing
  `apply-kaocha-extra` (config cli-options authoritative on conflict, matching
  the documented `:config`-wins rule). `:focus` already keyword-coerced by
  Kaocha's parse; `apply-kaocha-extra`'s `coerce-focus` is idempotent on it.
- Malformed forwarded option → `parse-kaocha-argv` throws `ex-info`, surfaced by
  the CLI as `:scry.cli/runner-error` (the accepted reclassification).

## Slice 3/4/5 — docs, tests, verification

- Help (`usage-for :kaocha` and general), README, AGENTS.md, CHANGELOG, and
  `doc/API.md` (`bb api-docs`, `--check` clean) updated for forwarding.
- Tests: rewrote `parse-main-args-test` Kaocha subtests for `:kaocha-argv`;
  replaced the obsolete `--kaocha-opt` CLI test with
  `kaocha-cli-forwarded-option-reaches-kaocha-test` (`--no-randomize` suppresses
  seed + forwarded `--focus` filters), added
  `kaocha-cli-malformed-option-is-runner-error-test` and
  `kaocha-cli-core-only-selectors-rejected-test`; added adapter
  `kaocha-argv-parse-test` and `kaocha-argv-forwarded-focus-filters-execution-test`.
- Verification (all green): `scry.cli-test` (45/384), core slice
  (capture/clojure-test/cli 51/556), `scry.kaocha-test` + `scry.cli-kaocha-test`
  (30/160), `bb clj-kondo:lint` (0/0), `bb clj-fmt:check` (clean after fix),
  `bb api-docs --check` (clean). Command-line acceptance against a temp tests.edn
  project: `--focus` (exit 0, 1 var), positional `unit`, `--bogus`
  (runner-error), and `--runner kaocha --help` (forwarding help text).

## Reviews

- architectural review: no architectural review feedback. Design respects the
  core/adapter dependency boundary (core forwards opaque `:kaocha-argv` strings;
  parsing stays in `src-kaocha`), localizes Kaocha knowledge in the adapter, and
  reuses existing resolution paths (`apply-cli-args`/`select-suites`/
  `apply-kaocha-extra`). Note: project has no META.md or doc/architecture.md;
  AGENTS.md is the architecture source.
- ambiguity review: no ambiguity review feedback. The design's genuine unknowns
  (scry-owned flag set incl. `--config`/`--dir`, reusable Kaocha argv parse entry
  point, `-m` positional suite-selection semantics) are already explicitly
  captured as Open Questions 1-3 with leanings/resolution paths; no unintended
  ambiguity found.
- inconsistency review: no inconsistency review feedback. Considered the step-2
  owned-flag set ("everything else forwards") vs OQ1's lean to keep
  `--config`/`--dir` owned — acknowledged deferral via explicit "(see Open
  Question 1)" cross-reference, not an unresolved contradiction. `:kaocha-argv`
  as a new `-m`-only `run` option is consistent with Scope-out (which names only
  `:suite`/`:suites`/`:config`) and Constraints (doc/API.md update).

## Plan reviews

- ambiguity review: added 1 new design step. plan.md OQ1's scry-owned
  Kaocha-mode flag set omits `--namespace`/`--var`, but steps.md Slice 1 hedges
  ("if still core-relevant"); whether they are scry-rejected (current clear
  `argument-error` via `normalize-kaocha-options` `core-only-keys`) or forwarded
  into `:kaocha-argv` (Kaocha runner/load-error) is unresolved and affects
  outcome-kind classification and a Slice 4 test.

- ambiguity review (2nd pass): no new ambiguity review feedback. Prior
  namespace/var ambiguity is resolved in plan.md OQ1 and design-steps.md; OQ2
  (argv parse entry point) remains an explicitly-captured spike-deferred known
  unknown, and OQ3 (positional→`select-suites`) is resolved — no unintended
  ambiguity.

- inconsistency review: no new feedback. The plan/steps `--namespace`/`--var`
  owned-flag divergence is already captured by the ambiguity-review design step;
  other resolved points (owned set, positional→`select-suites` routing,
  `:kaocha-argv` `-m`-only, slice numbering) are cross-file consistent.

- inconsistency review (2nd pass): no new inconsistency review feedback. Owned
  flag set, core-only-selector rejection, OQ3 positional→`select-suites`,
  `:kaocha-argv` `-m`-only, and Slice 4 tests↔acceptance all agree across
  plan.md/steps.md/design-steps.md. design.md OQ1's leaning is resolved by
  plan.md OQ1 (plan supersedes a captured open question — not a contradiction).

## For the namespace/var design-step

- Current rejection lives in `src/scry/cli.clj` `normalize-kaocha-options`
  (`reject-keys opts core-only-keys ...`); `core-only-keys` =
  `#{:namespaces :vars}` ∪ `ns-pattern-keys`. `--namespace`/`--var` are still
  parsed by `parse-main-args` into `:namespaces`/`:vars`, then rejected there —
  so today Kaocha-mode `--namespace` gives an `argument-error`, not a forward.
- Principle: whichever option is chosen, keep the core/adapter boundary intact
  (core forwards opaque strings; no Kaocha require at load time) and preserve the
  `:scry.cli/outcome-kind` contract — only the unknown-flag→runner/load-error
  reclassification is an accepted change; deciding namespace/var should be a
  deliberate, tested classification, not an accidental side effect of forwarding.

## Context for downstream slices

- design-review session (architecture, ambiguity, inconsistency) added no
  design-steps; design is ready for planning. OQ1-3 remain for planning to
  resolve, not review findings.
- Relevant source files: `src/scry/cli.clj` (`-m` parser
  `parse-main-args`/`normalize-exec-opts`/`main-opts->exec-opts`, `--focus`/
  `--kaocha-opt`/`:suite-values` branches, `usage-for`, `scry-managed-keys`);
  `src-kaocha/scry/kaocha.clj` (`run`, `apply-kaocha-extra`, `select-suites`,
  `config/apply-cli-args`, coercion). Adapter argv parsing (OQ2) belongs here.
- Principle to hold: keep the core/adapter dependency boundary — `scry.cli` must
  not require `scry.kaocha` at load time; core forwards `:kaocha-argv` as opaque
  strings, all Kaocha parsing stays in `src-kaocha`.
- No META.md or doc/architecture.md exist; AGENTS.md is the architecture source.

## Implementation review

- added 2 follow-up steps: stale doc/API.md (`bb api-docs --check` fails on
  committed source line range) and missing Slice 2–5 / final-verification notes.
