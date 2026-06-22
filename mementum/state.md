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

- No open tasks.

## Conventions

- `state.md` is a current-state snapshot, not a log. Task progress lives in Munera task artifacts; durable lessons live in `mementum/memories/` or `mementum/knowledge/`.
- All prior tasks (001–020) are closed in `munera/closed/`.
- CLI failure diagnostic: on a failing outcome `scry.cli` writes a stderr results-dir pointer; for load errors it adds the failing message + root cause inline. The Kaocha adapter fires a progress callback for suite-level load/compile errors (task 020).
