# Mementum State

Project: scry — in-process Clojure test runner for AI agents and REPL-driven development.

## Features

- Scoped result formatting by invocation scope (suite / namespace / var).
- Nested in-process runner isolation via dynamically replaceable/disabled capture contexts.
- CLI (`scry.cli`) with live progress, `.scry-results/` EDN files, structured `:scry.cli/outcome-kind` classification, and non-zero exit codes.
- Optional Kaocha adapter (`scry.kaocha`) loaded dynamically; separate `org.hugoduncan/scry-kaocha` jar.
- Quickdoc-generated `doc/API.md` with curated public surface; docs-only tooling dependency.
- `tools.build` jar workflow: core-only jar + optional Kaocha adapter jar, versioned `0.1.<git-revcount>`.
- Release automation: Babashka `bb release` / `bb release:tag` / `bb release --dry-run`, GitHub Actions release workflow, Clojars deploy.
- GitHub Actions CI on PRs and pushes to `master`: core tests, Kaocha adapter tests, build checks, jar build, API-doc checks, lint, format.
- EPL-2.0 license.

## Structure

- `src/` — core library (`org.hugoduncan/scry`).
- `src-kaocha/` — optional Kaocha adapter (`org.hugoduncan/scry-kaocha`), available via `:kaocha` alias.
- `bb/` — Babashka tasks (release, api-docs).
- `test/` — core tests; `test-quickdoc/` — API-doc regression.
- `munera/` — task management (open/closed).
- `mementum/` — project memory (state, memories, knowledge).

## Active focus

- Task `019-kaocha-cli-focus-and-pass-through` — pass `--focus` and other unrecognized Kaocha options through to the underlying Kaocha runner from the scry CLI.

## Conventions

- `state.md` is a current-state snapshot, not a log. Task progress lives in Munera task artifacts; durable lessons live in `mementum/memories/` or `mementum/knowledge/`.
- All prior tasks (001–018) are closed in `munera/closed/`.
