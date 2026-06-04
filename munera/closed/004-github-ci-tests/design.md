# GitHub CI test workflow

## Goal

Add a GitHub Actions CI workflow that runs the project's tests on pull requests and on pushes to `master`.

The workflow should provide early feedback that the core library tests, optional Kaocha adapter tests, and build checks still pass in a clean GitHub-hosted environment.

## Context

The repository currently has no `.github/workflows/` CI configuration. Local verification is documented in `AGENTS.md`:

- Core scry test run:

  ```sh
  clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"
  ```

- Focused Kaocha adapter tests:

  ```sh
  clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.kaocha-test)"
  ```

- Focused build checks:

  ```sh
  clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"
  ```

- Jar build:

  ```sh
  clojure -T:build jar
  ```

The build version is derived from Git commit count, so CI checkout must provide enough Git history for `git rev-list --count HEAD` to work predictably. Full history is safest unless the implementation deliberately documents shallow-count semantics.

## Required trigger behavior

The workflow must run on:

```yaml
on:
  pull_request:
  push:
    branches: [master]
```

It may also support manual dispatch if useful, but PR and `master` push triggers are required.

## Scope

In scope:

- Add `.github/workflows/ci.yml` or similarly named workflow file.
- Check out the repository.
- Set up a Clojure-capable Java environment.
- Run core tests.
- Run Kaocha adapter tests with the optional `:kaocha` alias.
- Run focused build checks with the `:build` alias.
- Run the jar build to verify the build task in CI.
- Validate the workflow locally with `actionlint` during implementation; `actionlint` is not required to run as a GitHub Actions CI job/step for this task.
- Use enough Git checkout history for Git-derived versioning.
- Update `AGENTS.md` with CI maintenance notes if needed.
- Add a `CHANGELOG.md` Unreleased entry if this is considered developer-visible project behavior.

Out of scope:

- Publishing artifacts.
- Uploading jar artifacts.
- Running release/deploy jobs.
- Adding branch protection rules.
- Adding a broad CI matrix unless the implementation chooses a small, justified matrix.
- Changing source code, result shapes, or build version semantics.

## Design considerations

- Prefer a simple single-job workflow unless there is a clear reason to split jobs.
- Use a stable and conventional Clojure setup action. If using `DeLaGuardo/setup-clojure`, pin a concrete version and install the Clojure CLI. If using another action, keep the setup understandable and documented in the task notes.
- Use a maintained Java distribution such as Temurin and a current LTS JDK. The exact JDK version can be chosen during planning; prefer one compatible with Clojure 1.12 and GitHub Actions defaults.
- Use `actions/checkout` with `fetch-depth: 0` so `git rev-list --count HEAD` matches the repository history rather than a shallow checkout count.
- Keep commands aligned with `AGENTS.md` so local and CI verification do not drift.
- Avoid adding command-line-oriented usage examples to README. CI/maintainer details belong in `AGENTS.md` and task notes.
- Consider dependency caching if it is simple and conventional, but do not let caching complexity obscure the workflow.
- Use `actionlint` as the primary local/static linter for GitHub Actions workflows during implementation. The project has `actionlint` available through `mise`; document and run `mise exec -- actionlint` or the equivalent local `actionlint` command when validating workflow changes. Do not add an `actionlint` CI job/step unless a later task explicitly expands CI scope to include workflow self-linting.

## Acceptance criteria

- A GitHub Actions workflow file exists under `.github/workflows/`.
- The workflow runs on pull requests.
- The workflow runs on pushes to `master`.
- The workflow checks out full Git history or otherwise clearly supports Git-derived build versioning.
- The workflow sets up Java and the Clojure CLI.
- The workflow runs the core scry test command successfully.
- The workflow runs focused Kaocha adapter tests successfully.
- The workflow runs focused build checks successfully.
- The workflow runs `clojure -T:build jar` successfully.
- The workflow passes `actionlint` validation locally.
- Commands in the workflow are consistent with maintainer guidance in `AGENTS.md`, or the guidance is updated to match.
- The workflow does not publish, deploy, or upload artifacts.
