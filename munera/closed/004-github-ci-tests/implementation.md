# Implementation notes

Implementation is complete; remaining notes record design, review, implementation, and validation history.

## Design update - 2026-06-02

Added `actionlint` to the task design as the primary static validator for GitHub Actions workflow changes. The project now has `actionlint` available through `mise` (`mise.toml`), so the implementation should run/document `mise exec -- actionlint` or equivalent local `actionlint` validation after adding the workflow.

## Architecture review - 2026-06-02

No new actionable architectural-fit feedback. The design keeps CI as maintainer infrastructure, aligns commands with AGENTS.md, preserves the optional Kaocha alias boundary, uses full Git history for Git-derived build versions, and does not introduce publish/deploy behavior. META.md and doc/architecture.md were absent, so review used AGENTS.md plus the task design.

## Ambiguity review - 2026-06-02

Found one new actionable ambiguity: the design says to lint the workflow with `actionlint`, but it is unclear whether `actionlint` must be part of the GitHub Actions workflow itself or only a local validation command run during implementation.

## Design follow-up - 2026-06-02

Clarified that `actionlint` is required as local/static validation during implementation and is not required as a GitHub Actions workflow job/step for this task. A future task may expand CI scope to include workflow self-linting if desired.

## Inconsistency review - 2026-06-02

No new actionable inconsistency feedback. The design is internally consistent and matches referenced artifacts: AGENTS.md commands align with the required CI commands, deps.edn provides the referenced :test/:kaocha/:build aliases, build.clj uses Git commit count versioning, mise.toml provides actionlint, and no existing .github/workflows configuration contradicts the task context.

## Plan ambiguity review - 2026-06-03

Found one new actionable ambiguity: the plan requires pinned Java and Clojure setup actions but does not specify the acceptable pin granularity or exact action/version choices, leaving implementation to choose between mutable major tags, full version tags, or SHAs.

## Plan inconsistency review - 2026-06-03

Found one new actionable inconsistency: `implementation.md` still opens with "No implementation yet." even though later notes and all steps record the CI implementation and validation as complete.

## Plan follow-up and implementation - 2026-06-03

Resolved the review-added action pinning ambiguity before adding workflow setup steps: this task uses `actions/setup-java@v4` with Temurin JDK 21 as the maintained first-party setup action major tag, and `DeLaGuardo/setup-clojure@13.4` as a concrete released third-party action tag for the Clojure CLI setup.

Added `.github/workflows/ci.yml` with the required `pull_request` trigger and `push` trigger restricted to `master`. The workflow is a single `ubuntu-latest` job, checks out full history with `actions/checkout@v4` and `fetch-depth: 0`, sets up Java and the Clojure CLI, then runs the documented core, Kaocha adapter, focused build check, and jar build commands. Confirmed the workflow contains no publish, deploy, release, artifact upload, or artifact download steps.

Added a short CI maintenance note to `AGENTS.md` to keep workflow commands aligned with local maintainer commands and preserve full Git history for jar versioning. Added a `CHANGELOG.md` Unreleased entry for the new GitHub Actions CI workflow.

Validation:

- `mise exec -- actionlint` — passed, no output.
- `clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"` — passed: 25 tests, 81 pass, 0 fail, 0 error.
- `clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.kaocha-test)"` — passed: 8 tests, 48 assertions, 0 failures, 0 errors.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"` — passed: 3 tests, 25 assertions, 0 failures, 0 errors.
- `clojure -T:build jar` — passed.

## Plan inconsistency follow-up - 2026-06-03

Updated the opening implementation note so it no longer says "No implementation yet." The task's checklist is now fully complete; no source, workflow, or verification behavior changed in this follow-up.

## Implementation review - 2026-06-03

Found one new actionable implementation-quality issue: the CI test steps run `scry/run` / `clojure.test/run-tests` forms but do not convert failing result maps into a non-zero process exit, so GitHub Actions can report success even when core, Kaocha, or focused build tests fail.

## Implementation review follow-up - 2026-06-03

Updated `.github/workflows/ci.yml` so each Clojure test/check step explicitly exits with status 1 when its result reports failures or errors. The core scry test step now binds the `scry/run` result, prints the report, and exits non-zero when `:pass?` is false. The Kaocha adapter and focused build check steps now bind the `clojure.test/run-tests` result and exit non-zero unless `clojure.test/successful?` returns true.

Validation:

- `mise exec -- actionlint` — passed, no output.
- `clojure -M:test -e "(require '[scry.core :as scry]) (let [result (scry/run)] (println (scry/report-string result)) (when-not (:pass? result) (System/exit 1)))"` — passed: 25 tests, 81 pass, 0 fail, 0 error.
- `clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.kaocha-test)] (when-not (ct/successful? result) (System/exit 1)))"` — passed: 8 tests, 48 assertions, 0 failures, 0 errors.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — passed: 3 tests, 25 assertions, 0 failures, 0 errors.
- `clojure -T:build jar` — passed.

## Implementation review - 2026-06-03 (follow-up pass)

No new actionable implementation-quality issues found. The workflow matches the task design and plan: required triggers, full-history checkout, pinned setup actions, core/Kaocha/build/jar verification, non-zero exits for failing Clojure test results, no publish/deploy/artifact behavior, and AGENTS/CHANGELOG updates are present. Validation rerun successfully: `mise exec -- actionlint`, core scry tests, focused Kaocha adapter tests, focused build checks, and `clojure -T:build jar`.

## Test review - 2026-06-03

No new actionable test-quality issues found. The CI verification covers every task-required behavior that can be validated by tests/static checks: workflow syntax via `actionlint`, core tests, Kaocha adapter tests, focused build checks, and jar build; the Clojure test steps fail the job on failing result maps; no mock/stub-based test infra was introduced. Validation rerun successfully: `mise exec -- actionlint`, core scry tests, focused Kaocha adapter tests, and focused build checks.

## Test-shaper review - 2026-06-03

No new actionable test-quality issues found. The CI verification remains simple, deterministic, behavior-focused, and aligned with the task acceptance criteria: `actionlint` covers workflow syntax, the core/Kaocha/build checks fail non-zero on failing result maps, and focused build checks plus jar build cover the Git-derived artifact boundary. Validation rerun successfully: `mise exec -- actionlint`, core scry tests, focused Kaocha adapter tests, and focused build checks.

## Docs review - 2026-06-03

No new actionable documentation issues found. README correctly remains focused on public `scry` API behavior and avoids maintainer CI details; no `doc/` directory is present; `CHANGELOG.md` Unreleased documents the new PR/`master` GitHub Actions workflow and its core/Kaocha/build/jar coverage; `AGENTS.md` documents CI maintenance guidance, full-history checkout, and the local verification/build commands the workflow mirrors.

## Code-shaper review - 2026-06-03

No new actionable code-quality issues found. The workflow is locally comprehensible and simple: a single CI job with required triggers, full-history checkout, explicit setup steps, sequential verification commands, result-aware non-zero exits for Clojure checks, and no unrelated publish/deploy/artifact behavior. `mise exec -- actionlint` passes.
