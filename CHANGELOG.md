# Changelog

## Unreleased

- Changed the Kaocha `-m` CLI to take suite selectors as trailing positional arguments (`--runner kaocha unit [integration ...]`), collapsing a single selector to `:suite` and multiple selectors to `:suites`. The `--suite`/`-s`/`--suites` flags are removed with no alias; this is a breaking change to the `-m` Kaocha command-line surface. The `-X` `:suite`/`:suites` map keys and the `scry.kaocha/run` adapter options/validation are unchanged.
- Added Kaocha CLI option pass-through: the `-m` `--focus SYM` flag (repeatable) forwards Kaocha's focus selector, the generic `-m` `--kaocha-opt KEY VALUE` flag forwards any other raw Kaocha cli-option, and any `-X` top-level key outside scry's own option set is forwarded as pass-through. Forwarded options are merged into the resolved Kaocha config's `:kaocha/cli-options` with an explicit `:config` authoritative on conflict, and `:focus` values are coerced to the keyword shape Kaocha's filter plugin expects. These flags are rejected in core (`--runner clojure-test`) mode; because `-X` has no unknown-key rejection, a mistyped `-X` key surfaces as a Kaocha runner/load error rather than an argument error.
- Added a generated quickdoc API reference at `doc/API.md`, plus `bb api-docs` / `bb api-docs --check` maintainer commands for reproducible regeneration.

## [0.1.28] - 2026-06-04

- Added machine-readable CLI outcome classification via `:scry.cli/outcome-kind` on `run-cli`/`clojure -X` outcomes and non-zero ex-data, distinguishing pass, argument error, runner error, synthetic load error, test failure, unknown result, and zero-test outcomes without parsing stderr.
- Fixed CLI progress and `.scry-results/` handling for synthetic suite-level failing/erroring entries without concrete vars, writing deterministic files such as `suite-error-1.edn` instead of crashing on nil `:var` values.

## [0.1.26] - 2026-06-04

- Fixed nested/reentrant in-process capture so nested `scry`, optional Kaocha, and raw `clojure.test` runs no longer leak inner events or output into an enclosing `scry` result, while preserving inner raw `clojure.test` assertion counters.
- Added `scry.cli` command-line entry points for `clojure -M -m` and `clojure -X` runs, with live per-test-var progress, stdout summaries, non-zero exits for failures/errors/no executable tests, and detailed `.scry-results/*.edn` files for failed or erroring vars.
- Added command-line selector support for core `clojure.test` dirs, namespaces, vars, and namespace patterns, plus optional Kaocha CLI mode for suite/config/fallback options when the Kaocha adapter is on the classpath.
- Added an optional `org.hugoduncan/scry-kaocha` adapter artifact built from `src-kaocha`, released at the same version as `org.hugoduncan/scry`, with release builds/deploys/GitHub Releases now carrying both jars while keeping the core jar free of Kaocha code and dependencies.
- Added public Maven POM metadata for both `org.hugoduncan/scry` and `org.hugoduncan/scry-kaocha`, including project descriptions, project URL, EPL-2.0 license metadata, SCM metadata, and maintainer/developer metadata for Clojars consumers.
- Added Babashka release tasks and a GitHub Actions release workflow for safe dry-run verification, strict `v0.1.<git-count>` tag publishing, Clojars deploy, and GitHub Release creation.
- Added a GitHub Actions CI workflow for pull requests and pushes to `master` that runs formatting checks, Clojure linting, core tests, optional Kaocha adapter tests, focused build checks, and the jar build.
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
