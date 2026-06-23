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
