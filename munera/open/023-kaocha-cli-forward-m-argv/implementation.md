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
