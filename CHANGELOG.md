# Changelog

## Unreleased

- Changed test result maps to use `:results` as the canonical formatted collection, with detail selected by invocation scope.
- Broad suite and multi-test runs now default to compact failing/erroring entries with assertion summaries and no captured output.
- Single-namespace runs now return entries for every executed test var with full assertion details, including passing assertions, and no captured output by default.
- Single-var runs now return the executed var with full assertion details plus captured `:out` and `:err` by default.
- Added per-scope `:result-format` customization for top-level keys, entry keys, assertion inclusion, and output inclusion.
- Retained `:failures` as a compatibility collection/accessor for failing or erroring entries when included by the selected format.
