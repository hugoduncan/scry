# Implementation notes

## Implementation pass - 2026-06-02

Implemented the tools.build jar build. `deps.edn` now has a `:build` alias using `io.github.clojure/tools.build` with `:ns-default build`; `build.clj` defines `clean`, `jar`, `version`, and related helpers for coordinate `org.hugoduncan/scry`, major/minor `0.1`, and patch from `git rev-list --count HEAD`. Git version failures throw `ex-info` rather than using a fallback.

Packaging decision: the initial jar is core-only and copies only `src` into `target/classes`; `src-kaocha` remains excluded to preserve the optional Kaocha boundary and avoid a hard Kaocha runtime dependency. Local Maven `install` remains deferred as planned. `.gitignore` now ignores `target/`.

Added focused build checks in `test/scry/build_test.clj`. The tests load `build.clj` only when `:build` is on the classpath, so ordinary `clojure -M:test` discovery can still run without tools.build. The focused build test verifies version computation, jar filename/location, pom coordinate/version, included core namespaces, and exclusion of test/Munera/Mementum/.psi/.cpcache/Kaocha files.

Updated `AGENTS.md` with maintainer build commands, version source/failure behavior, focused build-check command, install deferral, and the core-only optional-adapter packaging decision.

Verification completed:

- `clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"` → 24 tests, 80 pass, 0 fail, 0 error.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"` → 2 tests, 16 assertions, 0 failures/errors.
- `clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.kaocha-test)"` → 8 tests, 48 assertions, 0 failures/errors.
- `clojure -T:build jar` succeeded and produced `target/scry-0.1.69.jar` at the then-current commit count.
- Inspected jar entries and pom: jar contains only `scry/capture.clj`, `scry/clojure_test.clj`, `scry/core.clj`, and Maven metadata; pom has group `org.hugoduncan`, artifact `scry`, version `0.1.69`, and no Kaocha dependency.

## Architecture review - 2026-06-02

Reviewed design.md for architectural fit against available architecture guidance in AGENTS.md; META.md and doc/architecture.md are absent in this checkout. No new actionable architectural misfits found. The design preserves the core/Kaocha boundary by requiring an explicit packaging decision and preferring a core-only jar if optional adapter packaging would create a hard Kaocha dependency; it keeps build mechanics in deps/build.clj without changing public APIs or result shapes, and places maintainer build docs in AGENTS.md as project guidance expects.

## Ambiguity review - 2026-06-02

Reviewed design.md for actionable ambiguities against AGENTS.md, README.md, deps.edn, and current source/test layout. No new actionable ambiguity feedback found; the design intentionally leaves only acceptable implementation choices, chiefly whether to package core-only or safely include the optional Kaocha adapter while preserving the dependency boundary.

## Inconsistency review - 2026-06-02

Reviewed design.md for internal inconsistencies and against referenced artifacts AGENTS.md, README.md, deps.edn, source/test layout, CHANGELOG.md, SKILL.md, and .gitignore. No new actionable inconsistency feedback found. The requested coordinate/version/build workflow, docs placement, repository-file exclusion, and core/Kaocha dependency-boundary requirements are consistent with the current project state and referenced guidance.

## Plan ambiguity review - 2026-06-02

Reviewed plan.md and steps.md against design.md, AGENTS.md, deps.edn, .gitignore, and current source/test layout. Found new actionable ambiguities: focused build checks do not yet identify their home/invocation, and the optional `install` task is represented as a checklist item without a clear include/defer decision or verification expectation.

## Plan ambiguity follow-up execution - 2026-06-02

Completed the newly added plan-review follow-up items. `plan.md` now specifies that focused build checks will live in `test/scry/build_test.clj` and gives the exact command to run them: `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"`. `steps.md` mirrors that command on the Slice 3 check items and Slice 5 verification item.

Decided to defer the optional local Maven `install` task for this task. The initial scope is now explicitly `clean` and `jar`; `install` can be added later with explicit local-repository verification if needed. The former ambiguous install checklist item is replaced with a completed deferral item.

## Plan inconsistency review - 2026-06-02

Reviewed plan.md and steps.md against design.md, implementation.md, deps.edn, AGENTS.md, .gitignore, and current source/test layout. Found one new actionable inconsistency: plan.md first says the build alias should let maintainers run `clean`, `jar`, and optionally `install`, but the same plan and steps.md explicitly defer `install` and scope this task to `clean`/`jar` only.

## Plan inconsistency follow-up execution - 2026-06-02

Completed the newly added plan inconsistency follow-up. Updated `plan.md` so the build-alias decision now lists only the in-scope `clean` and `jar` tasks and explicitly says local Maven `install` remains deferred to a later task if needed. Marked the follow-up item complete in `steps.md`.

## Implementation review - 2026-06-02

Reviewed implementation against design.md, plan.md, steps.md, deps.edn, build.clj, test/scry/build_test.clj, AGENTS.md, .gitignore, and core source layout. No new actionable implementation-quality issues found. The build remains simple and conventional, preserves the core/Kaocha boundary by packaging only `src`, computes versions from Git with clear failure behavior, keeps repository-only files out by copying selected source dirs, and documents maintainer workflow in AGENTS.md. Verification rerun: core tests pass, focused build checks pass, and `clojure -T:build jar` succeeds.

## Test review - 2026-06-02

Reviewed test coverage against design.md, plan.md, steps.md, build.clj, deps.edn, AGENTS.md, and test/scry/build_test.clj. Found one new actionable test-quality issue: the focused build checks cover successful Git-derived version computation but do not exercise the required clear failure behavior for Git command failures or invalid non-numeric counts; the current direct process dependency also makes those cases hard to test without stubbing.

## Test review follow-up execution - 2026-06-02

Completed the newly added test-review follow-up. `build/git-rev-count` now accepts an optional nullable `process-fn` boundary, defaulting to `clojure.tools.build.api/process` when omitted or nil, so production behavior stays unchanged while focused tests can drive process outcomes directly without mocks/stubs. Added `scry.build-test/git-rev-count-failure-behavior-test` covering non-zero Git process exits and invalid non-numeric counts; both assert the clear `ex-info` messages and data. Marked the follow-up item complete in `steps.md`.

Verification completed:

- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"` → 3 tests, 22 assertions, 0 failures/errors.
- `clojure -T:build jar` succeeded.
- `clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"` → 25 tests, 81 pass, 0 fail, 0 error.

## Test review - 2026-06-02

Reviewed test coverage after the prior test-review follow-up against design.md, plan.md, steps.md, build.clj, deps.edn, AGENTS.md, and test/scry/build_test.clj. No new actionable test-quality issues found. Focused build checks now cover successful Git-derived versioning, clear `ex-info` failures for non-zero Git exits and invalid counts through an injectable/nullable process boundary, jar path/pom coordinate/version, core source inclusion, and exclusion of test/repository/Kaocha paths. Verification rerun: focused build checks pass, core tests pass, and `clojure -T:build jar` succeeds.

## Test-shaper review - 2026-06-02

Reviewed focused build tests against test-shaper criteria and the task's core/Kaocha boundary acceptance criteria. Found one new actionable test-quality issue: the jar test asserts `scry/kaocha.clj` is excluded from jar entries, but it does not assert the generated pom excludes the alias-only Kaocha dependency; because the task explicitly protects against making Kaocha a hard runtime dependency, the pom dependency boundary should be covered with a meaningful assertion.

## Test-shaper follow-up execution - 2026-06-02

Completed the newly added test-shaper follow-up. `test/scry/build_test.clj` now asserts that the generated pom excludes the optional Kaocha dependency boundary by checking that it does not contain `lambdaisland` group metadata, `kaocha` artifact metadata, or the `lambdaisland/kaocha` coordinate string. Marked the follow-up item complete in `steps.md`.

Verification completed:

- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"` → 3 tests, 25 assertions, 0 failures/errors.
- `clojure -T:build jar` succeeded.
- `clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"` → 25 tests, 81 pass, 0 fail, 0 error.

## Test-shaper review - 2026-06-02

Reviewed focused build tests after the prior test-shaper follow-up against test-shaper criteria, design.md, plan.md, steps.md, build.clj, deps.edn, AGENTS.md, and test/scry/build_test.clj. No new actionable test-quality issues found. The focused build tests remain simple and behavior-focused, cover Git version success and clear failure boundaries, verify jar path/pom coordinate/version, assert core source inclusion, and enforce both jar-entry and pom dependency exclusion for the optional Kaocha boundary. Verification rerun: focused build checks pass.

## Docs review - 2026-06-02

Reviewed user-facing docs against design.md, plan.md, steps.md, build.clj, deps.edn, README.md, AGENTS.md, CHANGELOG.md, and doc/ (absent). Found one new actionable docs issue: CHANGELOG.md Unreleased does not mention the new user/developer-visible tools.build jar workflow/artifact, even though AGENTS.md documents the commands accurately and README.md appropriately remains unchanged because no artifact-consumption guidance was added.

## Docs review follow-up execution - 2026-06-02

Completed the newly added docs-review follow-up. `CHANGELOG.md` Unreleased now documents the new `tools.build` jar workflow with the `clojure -T:build jar` command, the `org.hugoduncan/scry` coordinate, Git-derived `0.1.<git-revcount>` versioning, and the core-only packaging decision that excludes the optional Kaocha adapter. Marked the follow-up item complete in `steps.md`.

## Docs review - 2026-06-02

Reviewed user-facing docs after the prior docs-review follow-up against design.md, plan.md, steps.md, build.clj, deps.edn, README.md, AGENTS.md, CHANGELOG.md, and doc/ (absent). No new actionable docs issues found. AGENTS.md accurately documents maintainer build commands, Git-derived versioning/failure behavior, install deferral, and core-only optional Kaocha exclusion; CHANGELOG.md Unreleased now covers the user/developer-visible jar workflow/artifact; README.md appropriately remains unchanged because no artifact-consumption guidance was added.

## Code-shaper review - 2026-06-02

Reviewed build.clj, deps.edn, test/scry/build_test.clj, AGENTS.md, CHANGELOG.md, and task artifacts using code-shaper criteria: simplicity, consistency, local comprehensibility, and robustness. No new actionable code-quality issues found. The build script stays small and conventional, separates version/process handling from jar assembly, copies only explicit source dirs to preserve artifact boundaries, reports Git-version failures clearly, and the focused tests exercise both success and failure behavior. Verification rerun: focused build checks pass and core tests pass.
