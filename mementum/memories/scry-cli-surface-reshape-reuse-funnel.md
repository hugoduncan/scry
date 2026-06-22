🔁 When reshaping a scry `-m` CLI surface (e.g. named flags → positional args), route the new tokens onto the *existing* accumulator/keys so all downstream behavior is inherited for free — do NOT add a parallel validation branch.

Task 021 turned `--suite`/`-s`/`--suites` flags into positional suite selectors by collecting any non-`-` token into the same `:suite-values` accumulator that `main-opts->exec-opts` already collapses (1→`:suite`, many→`:suites`). Because `:suite`/`:suites` are in `kaocha-only-keys`, core-mode rejection comes free via the existing `normalize-core-options` `reject-keys` path. Tokens starting with `-` still hit the `parse-main-args` default → "Unknown option" `:scry.cli/argument-error`.

Key invariant: the `:scry.cli/outcome-kind` contract is preserved across the reshape — core-mode positionals stay `:scry.cli/argument-error` before and after; only the rejection *message/path* moves (parse-time "Unknown option" → normalize-time "Kaocha options require :runner :kaocha").

Testing corollary: assert `argument-error?` / outcome-kind, NOT the error message text, since the message legitimately changes while the outcome is invariant. This contract-vs-mechanism distinction took several design/plan inconsistency-review turns to pin down.

(task 021)
