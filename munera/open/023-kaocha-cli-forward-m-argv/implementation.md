# Implementation notes

## Reviews

- architectural review: no architectural review feedback. Design respects the
  core/adapter dependency boundary (core forwards opaque `:kaocha-argv` strings;
  parsing stays in `src-kaocha`), localizes Kaocha knowledge in the adapter, and
  reuses existing resolution paths (`apply-cli-args`/`select-suites`/
  `apply-kaocha-extra`). Note: project has no META.md or doc/architecture.md;
  AGENTS.md is the architecture source.
