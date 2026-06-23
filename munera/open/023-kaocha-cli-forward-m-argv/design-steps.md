# Design follow-up steps

## Plan-review: ambiguity

- [ ] Resolve the ambiguous handling of `--namespace`/`--var` in Kaocha `-m`
      mode. plan.md OQ1 lists the scry-owned Kaocha-mode flag set without
      `--namespace`/`--var` (implying they forward), but steps.md Slice 1 hedges
      with "`--namespace`/`--var` if still core-relevant". Decide and record in
      plan.md whether, in Kaocha mode, `--namespace`/`--var` are (a) still
      consumed by scry and rejected with the existing clear `argument-error`
      ("Core namespace, var ... not supported by Kaocha mode" via
      `normalize-kaocha-options` `reject-keys core-only-keys`), or (b) forwarded
      verbatim into `:kaocha-argv` and allowed to surface as a Kaocha
      `runner-error`/`load-error`. State whether the existing `core-only-keys`
      rejection is preserved or removed, and add a corresponding test
      expectation in Slice 4.
