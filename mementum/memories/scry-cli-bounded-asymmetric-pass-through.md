🎯 When passing options through scry's CLI to an underlying runner, keep pass-through bounded and asymmetric to preserve the `:scry.cli/outcome-kind` contract:

- `-m` path: opt-in only. Add explicit named flags (e.g. `--focus`) plus a generic `--kaocha-opt KEY VALUE` escape hatch; both accumulate into a `:kaocha-extra` map. Unknown bare flags still hit `parse-main-args`' default branch → `:scry.cli/argument-error`. Never default-forward arbitrary unknown flags.
- `-X` map path: forward top-level keys outside the closed scry-managed key set. There is no unknown-key rejection here, so a mistyped key is forwarded and surfaces as `:scry.cli/runner-error` / `:scry.cli/load-error` — an accepted, documented trade-off, not a bug to "fix".

Define the scry-managed exclusion set as a *derived* set (union of `core-only-keys`/`kaocha-only-keys`/`kaocha-fallback-keys` plus `:runner`, `:result-format`, `:progress-callback`, `:kaocha-extra`) so it stays in sync. Add the pass-through container key itself to that set so `-X` collection never re-nests it, and reject it in core (`:clojure-test`) mode via the existing `normalize-core-options` reject set.

(task 019)
