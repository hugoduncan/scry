# Implementation notes

## Review

- architectural review: no actionable feedback (design fits the parse → collapse
  → normalize → execute pipeline, the frozen `-m`-only scope, the `-X`/adapter
  boundary, and the `:scry.cli/outcome-kind` contract).

### Architecture context for implementation

- Core-mode rejection of positionals (design step 3) is already covered by the
  existing `normalize-core-options` `reject-keys` path: positionals collapse to
  `:suite`/`:suites` in `main-opts->exec-opts`, and `:suite`/`:suites` are in
  `kaocha-only-keys`, so core mode rejects them as `:scry.cli/argument-error`
  ("Kaocha options require :runner :kaocha"). Prefer reusing this pathway over a
  new positional-specific rejection branch to avoid a parallel validation path.
- The parser `default` case in `parse-main-args` is currently "Unknown option".
  The split should be: tokens starting with `-` stay unknown-option errors;
  other tokens become positional suite selectors. This keeps the change local to
  the hand-rolled loop and consistent with the existing architecture.
