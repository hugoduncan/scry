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

## Ambiguity review

- [ ] Precisely enumerate the closed "known scry-managed set" excluded from
  pass-through. Approach step 1 describes it loosely as "core-only keys, known
  kaocha keys, and shared keys *like* `:result-format`"; the "like" leaves the
  exact set undefined. The design should list every recognized/excluded key —
  including `:runner`, `:result-format`, `:progress-callback`, and each known
  kaocha key (`:suite`, `:suites`, `:config`, `:dirs`, `:source-paths`,
  `:test-paths`, `:ns-patterns`) — so scry-internal and mode-selector keys are
  never accidentally forwarded into the Kaocha config as `:kaocha-extra`.
- [ ] Specify how pass-through option *values* are typed/coerced on the `-m`
  path, where every value arrives as a raw string (unlike typed `-X` EDN values
  such as `:focus "my.ns/test-foo"`). The acceptance requires
  `--focus my.ns/test-foo` to actually focus, which depends on the value
  reaching Kaocha in the expected type. (Distinct from Open Question 3, which
  concerns *key* mapping rather than *value* coercion; this applies to arbitrary
  `-m` pass-through values, not only `:focus`.)
