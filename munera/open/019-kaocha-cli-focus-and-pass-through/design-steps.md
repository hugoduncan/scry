# Design review follow-ups

## Architecture review

- [ ] Specify how generic/arbitrary Kaocha option pass-through preserves the
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
