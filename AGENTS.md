# AGENTS.md

Guidance for AI agents working in this repository.

## Project purpose

`scry` is an in-process Clojure test runner for AI agents and REPL-driven development. Its main value is returning structured, inspectable test results instead of requiring agents to scrape terminal output.

The public API is centered on `scry.core/run`, which runs tests and returns scoped structured results:

```clojure
{:summary ...
 :pass? ...
 :results ...   ;; canonical formatted collection
 :failures ...} ;; compatibility failing/erroring subset when included
```

## Orientation

At the start of a session, read:

1. `mementum/state.md` — current project memory.
2. `munera/plan.md` — open task ordering, if any.
3. This file.
4. `README.md` for user-facing behavior.

If working a Munera task, keep task notes in that task's `implementation.md` and update `steps.md` as work progresses.

## Preferred test workflow: project REPL + scry

Prefer running tests through a live project REPL with `scry`, not through one-off command-line test invocations. The REPL workflow keeps code loaded, preserves `scry.core/last-result` for follow-up inspection, and returns structured data without scraping terminal output.

In a project REPL, run the current test suite:

```clojure
(require '[scry.core :as scry])

(scry/run)
(println (scry/report-string (scry/last-result)))
```

Inspect the raw result map:

```clojure
(scry/last-result)
(:summary (scry/last-result))
(:results (scry/last-result))
(scry/failures)
```

Run targeted tests from the REPL while iterating:

```clojure
(scry/run {:namespaces ['my.project-test]})
(scry/run {:vars [#'my.project-test/specific-test]})
```

Run the Kaocha adapter from the REPL when needed. It loads `tests.edn` from the current project when present and supports REPL suite selection:

```clojure
(require '[scry.kaocha :as k])

(k/run)
(k/run {:suites [:unit :integration]})
(k/run {:suite :unit})
(k/run {:config full-kaocha-config})
```

Use `:suite` for one selector; plural `:suites` must be a non-empty collection. Suite selectors match exact configured suite ids first, then unique string/name fallback. Supplying both `:suite` and `:suites`, unknown selectors, and ambiguous fallback selectors throw `ex-info`.

Start nREPL if no project REPL is available:

```sh
clojure -M:nrepl
```

Use command-line forms only as a fallback when a REPL is unavailable or unsuitable. Prefer the dedicated CLI for shell/CI-style runs because it returns process status, prints live per-var progress, and writes structured failure EDN under `.scry-results/`:

```sh
clojure -M:test -m scry.cli
clojure -M:test -m scry.cli --var my.project-test/specific-test
clojure -X:test scry.cli/run :vars '[my.project-test/specific-test]'
```

The CLI clears and recreates `.scry-results/` at run start, prints `.` to stdout for passing vars, prints failing/erroring unqualified var names to stderr, writes namespace-prefixed `.edn` files for failing/erroring vars, and exits non-zero for failures, errors, unknown status, argument/runner errors, or zero executable tests. Kaocha CLI mode requires the optional adapter classpath:

```sh
clojure -M:test:kaocha -m scry.cli --runner kaocha --suite unit
clojure -X:test:kaocha scry.cli/run :runner :kaocha :suite :unit
```

If you need raw API inspection from a one-off command, call `scry.core/run` explicitly:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"
clojure -M:test -e "(require '[scry.core :as scry]) (clojure.pprint/pprint (scry/run))"
```

## Result inspection guidance

Default result detail depends on invocation scope:

- Broad/discovered runs, multiple namespaces, and multiple vars use suite scope: compact failing/erroring entries only, no assertion detail or output.
- Exactly one explicit namespace uses namespace scope: all executed vars, all assertions including passes, no output keys.
- Exactly one explicit executable var uses var scope: one entry, all assertions, and captured `:out`/`:err`.

Use `:results` as the canonical result collection. Use `scry/failures` or `:failures` when you only need failing/erroring entries. Helpers tolerate custom result formats but cannot inspect collections omitted by `:top-level-keys`.

## Development practices

- Prefer small, focused changes.
- Keep public result shapes stable unless a task explicitly changes them.
- Add or update tests for behavior changes.
- Use `scry` itself to inspect test failures when possible.
- Avoid parsing human-oriented terminal output when a structured result is available.
- Keep README examples synchronized with the actual API.

## Maintainer CI workflow

GitHub Actions CI is configured in `.github/workflows/ci.yml` for pull requests and pushes to `master`. Keep its verification commands aligned with the local commands documented below, and use `actions/checkout` with full Git history because the jar version is derived from Git commit count.

## Maintainer build workflow

Build tasks use `tools.build` through the `:build` alias:

```sh
clojure -T:build clean
clojure -T:build jar         # core artifact only
clojure -T:build kaocha-jar  # optional Kaocha adapter artifact only
clojure -T:build jars        # release build: core + adapter artifacts
```

`jar` writes a core library artifact under `target/` named like `scry-0.1.<patch>.jar`. The coordinate is `org.hugoduncan/scry`, and the version is generated as `0.1.<git rev-list --count HEAD>`. Builds fail if Git metadata is unavailable or the Git command does not return a numeric commit count; do not substitute an ambiguous fallback version.

The core jar packages only `src`. It intentionally excludes `src-kaocha` so the optional Kaocha adapter does not become a hard runtime dependency of the core artifact. `kaocha-jar` writes `target/scry-kaocha-0.1.<patch>.jar` for coordinate `org.hugoduncan/scry-kaocha`; it packages `src-kaocha`, depends on same-version `org.hugoduncan/scry`, and reads the `lambdaisland/kaocha` dependency version from the `:kaocha` alias in `deps.edn`. Standalone `kaocha-jar` deletes only adapter-specific outputs and preserves any existing core jar. `jars` cleans `target/` once and builds both release artifacts with isolated class/pom output directories. Local Maven `install` is deferred until a later explicit task adds it with verification.

Run focused build checks with:

```sh
clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"
```

## Maintainer release workflow

Release automation uses Babashka locally and `.github/workflows/release.yml` in GitHub Actions.

Local commands:

```sh
bb release --dry-run        # dispatch a non-publishing workflow_dispatch dry run
bb release --dry-run --ref master
bb release:tag              # stamp CHANGELOG.md, commit, and create a local vVERSION tag
bb release                  # create/reuse the local tag, then push master and the tag
```

Dry runs are safe: they require a clean tree, a GitHub `origin`, an available/authenticated `gh` CLI, and a selected local commit that matches a pushed `origin` ref. The task passes `ref`, exact `sha`, and `expected_version` to the workflow; the workflow checks out full history, verifies the SHA when supplied, runs tests/build checks, builds both release jars with `clojure -T:build jars`, checks the shared expected version against both artifact filenames, and never deploys or creates a GitHub Release on `workflow_dispatch`.

Real releases must be cut from clean `master`. Because versions are `0.1.<git rev-list --count HEAD>`, `bb release:tag` computes the tag version as the current commit count plus one before creating the changelog release commit. It stamps `CHANGELOG.md` with a fresh bare `## Unreleased` section plus a bracketed `## [VERSION] - YYYY-MM-DD` release section, commits `Release vVERSION`, and tags that commit. Pushed publishing tags must exactly match `v0.1.<non-negative-integer>` and must agree with the shared core and Kaocha adapter jar version built at the checked-out tag commit.

Publishing runs use `clojure -T:build:deploy deploy-all` and require GitHub repository secrets `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` (a Clojars deploy token/password). The workflow deploys the core artifact first, then the same-version Kaocha adapter artifact, extracts the matching bracketed changelog section for the GitHub Release body, and attaches both built jars to the GitHub Release. The core-only `clojure -T:build:deploy deploy` command remains available for focused maintainer use.

Run focused release helper checks with:

```sh
clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"
```

## Architecture notes

Important namespaces:

- `scry.core` — public REPL/API entry point and convenience inspection helpers.
- `scry.cli` — command-line entry points and CLI-specific progress, result-file, summary, and exit-code behavior; it dynamically loads Kaocha support only when requested.
- `scry.capture` — low-level capture state, `clojure.test/report` hook, output routing, result construction, and result formatting.
- `scry.clojure-test` — in-process `clojure.test` runner and invocation scope classification.
- `scry.kaocha` — optional Kaocha adapter.

Dependency boundary:

- `scry.core` must not require Kaocha.
- `scry.cli` is part of the core jar, but must not require `scry.kaocha` at namespace load time; Kaocha CLI mode uses dynamic loading and requires the optional adapter jar/alias.
- Kaocha support belongs under `src-kaocha/` and is available only with the `:kaocha` alias.

## Testing expectations

For changes to the `clojure.test` runner or capture machinery, verify at least:

- Passing runs return `:pass? true` and no failures.
- Suite-scope broad runs are compact and omit output.
- Single namespace runs include passing vars and passing assertion detail.
- Single var runs include assertion detail and captured stdout/stderr.
- Failing assertions include expected/actual/message/file/line/testing contexts.
- Errors include stack traces.
- `:once` and `:each` fixtures still behave as normal `clojure.test` fixtures.

For changes to the CLI, run focused core and optional Kaocha CLI checks:

```sh
clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
```

For changes to the Kaocha adapter, verify it still returns the same scoped result model as `scry.core/run`; note that Kaocha currently defaults to suite scope and merges stdout/stderr. Focused Kaocha adapter tests require the optional alias:

```sh
clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.kaocha-test)"
```

## Documentation expectations

Update `README.md` when changing user-facing behavior:

- Public API names.
- Result map shape.
- Supported runner options.

Keep development instructions, test workflow guidance, agent workflow, repo conventions, and architectural constraints in `AGENTS.md`, not `README.md`.

Update this file when changing agent workflow, repo conventions, important architectural constraints, or development/test commands.
