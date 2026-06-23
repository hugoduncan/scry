# Implementation notes

- architectural review added 1 new design step (doc-contract alignment for the
  new error-path stdout summary). Design is otherwise a clean architectural fit:
  it mirrors the existing `write-failure-diagnostic!` "supplementary human
  output, authoritative signals unchanged" pattern, stays within `scry.cli`
  (core jar, no new Kaocha load-time coupling), and preserves the successful
  `:summary` shape. No `META.md`/`doc/architecture.md` exist; AGENTS.md is the
  authoritative architecture source.
