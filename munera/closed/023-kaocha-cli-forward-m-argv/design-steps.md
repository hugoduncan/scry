# Design follow-up steps

## Plan-review: ambiguity

- [x] Resolve the ambiguous handling of `--namespace`/`--var` in Kaocha `-m`
      mode. **Decided (a):** in Kaocha mode, `--namespace`/`--ns`, `--var`, and
      `--ns-pattern` stay scry-owned and parsed-then-rejected with the existing
      clean `:scry.cli/argument-error` (`normalize-kaocha-options` →
      `reject-keys core-only-keys`); they are NOT forwarded into `:kaocha-argv`.
      The Kaocha equivalent is `--focus`. Recorded in design.md OQ1 and plan.md
      OQ1; Slice 1 no longer hedges and Slice 4 adds a rejection test.
