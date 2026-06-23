# Implementation notes

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

- inconsistency review: no new feedback. The plan/steps `--namespace`/`--var`
  owned-flag divergence is already captured by the ambiguity-review design step;
  other resolved points (owned set, positional→`select-suites` routing,
  `:kaocha-argv` `-m`-only, slice numbering) are cross-file consistent.

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
