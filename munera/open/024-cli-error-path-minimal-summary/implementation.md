# Implementation notes

- architectural review added 1 new design step (doc-contract alignment for the
  new error-path stdout summary). Design is otherwise a clean architectural fit:
  it mirrors the existing `write-failure-diagnostic!` "supplementary human
  output, authoritative signals unchanged" pattern, stays within `scry.cli`
  (core jar, no new Kaocha load-time coupling), and preserves the successful
  `:summary` shape. No `META.md`/`doc/architecture.md` exist; AGENTS.md is the
  authoritative architecture source.
- ambiguity review added 2 new design steps (the "usage paths" vs `--help`
  success-path scope, and stdout-text-only vs returned `:summary` map key).
  Code check confirmed `--help`/usage is a separate exit-0 path that already
  prints usage, distinct from the `:summary nil` error outcomes.
- inconsistency review added 1 new design step. Verified in cli.clj that the
  catch-path `error-outcome-kind` only yields argument-error/runner-error;
  `:scry.cli/load-error` comes only from `classify-outcome` (normal return
  path) which already calls `write-summary!`. So the design's claim that
  load-error is a silent thrown `:summary nil` outcome is factually wrong —
  only runner-error (and argument-error) actually hit the silent path.
