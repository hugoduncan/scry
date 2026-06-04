# Changelog

## Unreleased

- Added a `tools.build` jar workflow with `clojure -T:build jar` for the `org.hugoduncan/scry` artifact, using Git-derived `0.1.<git-revcount>` versions and core-only packaging that excludes the optional Kaocha adapter.
- Updated `scry.kaocha/run` to load project `tests.edn` suites by default when present, falling back to the synthetic `:unit` suite only when no Kaocha config file exists.
- Added REPL suite selection for the Kaocha adapter via `:suite` and `:suites`, including exact suite-id matching, unique string/name fallback matching, and clear `ex-info` errors for conflicting, invalid, unknown, or ambiguous selectors.
- Preserved supplied full Kaocha configs passed with `:config` while still supporting suite selection and quiet structured scry output defaults.
- Changed test result maps to use `:results` as the canonical formatted collection, with detail selected by invocation scope.
- Broad suite and multi-test runs now default to compact failing/erroring entries with assertion summaries and no captured output.
- Single-namespace runs now return entries for every executed test var with full assertion details, including passing assertions, and no captured output by default.
- Single-var runs now return the executed var with full assertion details plus captured `:out` and `:err` by default.
- Added per-scope `:result-format` customization for top-level keys, entry keys, assertion inclusion, and output inclusion.
- Retained `:failures` as a compatibility collection/accessor for failing or erroring entries when included by the selected format.
