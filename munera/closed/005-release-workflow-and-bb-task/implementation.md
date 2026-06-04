# Implementation notes

No implementation yet. The task was created from the maintainer request to add a release GitHub Action and Babashka release task, taking simplified inspiration from `../../psi/psi-main`.

## Design refinement - 2026-06-03

Clarified that dry-run functionality is required, not optional. The design now requires a local `bb release --dry-run`-style entry point that dispatches the GitHub release workflow in non-publishing mode without mutating local or remote release state. The release workflow's `workflow_dispatch` path must run verification/build/version checks while skipping Clojars deploy and GitHub Release creation.

## Architecture review - 2026-06-03

No new actionable architectural-fit feedback. The design preserves the existing core-only jar boundary, keeps release/deploy dependencies in maintainer/release surfaces, retains Git-count versioning, reuses CI verification expectations, and leaves public runner/API behavior unchanged. META.md and doc/architecture.md were absent, so review used AGENTS.md plus the task design.

## Design ambiguity review - 2026-06-03

Found three actionable ambiguities: dry-run ref/version agreement needs exact local/remote commit semantics; changelog stamp/extraction heading syntax should be pinned against the existing bare `## Unreleased`; and publishing tag format should explicitly reject or define non-`v0.1.<git-count>` `v*` tags. Added follow-up items to `design-steps.md`.


## Design ambiguity follow-up execution - 2026-06-03

Completed the three review-added design follow-up items in `design-steps.md`:

- Dry-run ref/version semantics now require resolving the dispatch target to an exact commit, ensuring the selected local commit and remote ref match before dispatch, and computing `0.1.<git rev-list --count COMMIT>` for that exact remote checkout target.
- Changelog stamping and extraction now use a bare fresh `## Unreleased` heading plus bracketed release headings of the form `## [VERSION] - YYYY-MM-DD`; extraction trims the bracketed version section and fails if missing or empty.
- Publishing tag validation now requires tags exactly matching `v0.1.<non-negative-integer>`; nonconforming `v*` tags fail before deploy or GitHub Release creation, and the tag version must match the built jar version.

## Design inconsistency review - 2026-06-03

No new actionable inconsistency feedback. Reviewed `design.md` against existing `build.clj`, `deps.edn`, CI workflow, `CHANGELOG.md`, README/AGENTS guidance, and the referenced `psi` release workflow shape; the design is internally consistent about Git-count release versioning, dry-run non-publication, bracketed changelog release headings, tag validation, core-only jar/deploy boundaries, and CI verification reuse.

## Plan ambiguity review - 2026-06-03

Found three actionable ambiguities: the focused release helper/task test command and classpath/alias are not pinned; the deploy task credential/artifact contract is not specific enough for workflow/docs/tests to align; and dry-run dispatch says ref/SHA but the workflow input/checkout plan does not yet decide whether SHA is a separate input or require verifying the checked-out commit equals the exact SHA resolved locally. Added follow-up items to `steps.md`.

## Plan ambiguity follow-up execution - 2026-06-03

Completed the three review-added unchecked follow-up items in `steps.md` without executing older implementation steps:

- Pinned focused release helper/task tests to `test/scry/release_test.clj`, loaded through a dedicated `:release-test` alias that adds `bb/` to the classpath, with the exact command:
  `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"`.
- Specified the deploy contract as `clojure -T:build:deploy deploy`, using `CLOJARS_USERNAME` and `CLOJARS_PASSWORD`, building the jar first, and deploying the freshly generated jar plus matching generated pom for `org.hugoduncan/scry`.
- Chose separate dry-run workflow dispatch inputs `ref`, `sha`, and `expected_version`; the documented `bb release --dry-run` path must pass an exact SHA, and the workflow must verify the checked-out `HEAD` equals that SHA before tests/builds.

## Plan inconsistency review - 2026-06-03

Found one actionable inconsistency: `plan.md` now specifies separate dry-run workflow dispatch inputs `ref`, `sha`, and `expected_version`, but `steps.md` still asked for only `ref` plus optional `expected_version`, omitting the exact `sha` input. Updated the workflow-input step to require `ref`, exact `sha`, and optional `expected_version`.

## Plan inconsistency follow-up execution - 2026-06-03

Reviewed `steps.md` after the preceding plan inconsistency review. No newly added unchecked follow-up item remained to execute: the review's only actionable change had already been applied directly to the workflow-input step in `steps.md`. Did not execute older unchecked implementation steps.

## Implementation pass - 2026-06-03

Completed Slice 1 release helper design and tests.

- Inspected existing build/deps/CI/changelog files and the relevant `psi` release workflow/Babashka release code for simplified adaptation points.
- Chose `bb/scry/release.clj` as the Babashka-compatible helper namespace and `test/scry/release_test.clj` as the focused test namespace, loaded through the planned `:release-test` alias.
- Added pure/injectable helper logic for Git-count version math, strict `v0.1.N` tag validation/agreement, bare `## Unreleased` changelog stamping, bracketed release-section extraction, clean-master checks, dry-run local/remote ref agreement, GitHub origin parsing, and existing-tag retry planning.
- Added focused state-based tests for version off-by-one behavior, tag validation, changelog stamping/extraction failures, dry-run ref agreement, command-boundary state checks, and existing-tag partial-failure planning.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 7 tests, 31 assertions, 0 failures, 0 errors.

Implementation deviation: the first helper file is under `bb/scry/release.clj` rather than a top-level `bb/release.clj` so the namespace can be required consistently by Clojure focused tests and later by `bb.edn`. The task plan allowed implementation under `bb/`; subsequent Babashka task wiring should either require this namespace directly or add a thin top-level wrapper only if needed.

## Implementation pass - 2026-06-03 (Slice 2)

Completed Slice 2 Babashka task wiring.

- Added `bb.edn` with small `release` and `release:tag` task bodies requiring `scry.release` from the existing `bb/` helper path.
- Implemented `bb release --dry-run [--ref REF]` as a non-mutating GitHub Actions dispatch path. It validates a clean tree, existing release workflow file, available/authenticated `gh`, GitHub `origin`, exact local SHA, remote ref agreement, and computes `expected_version` from `git rev-list --count <sha>` before invoking `gh workflow run .github/workflows/release.yml --ref <ref> -f ref=<ref> -f sha=<sha> -f expected_version=<version>`.
- Implemented `bb release:tag` to require clean `master`, require non-empty `## Unreleased`, compute `0.1.<current-count + 1>`, stamp `CHANGELOG.md`, commit `Release vVERSION`, and create the local tag.
- Implemented `bb release` to reuse/push an existing local release tag at `HEAD` when present, avoid creating duplicate release tags, or create the tag then push `master` and the tag.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 7 tests, 31 assertions, 0 failures, 0 errors.
- Verification: `bb tasks` listed the `release` and `release:tag` tasks, and `bb -e "(require '[scry.release :as r]) (println (r/next-release-version 1))"` printed `0.1.2`.

Implementation note: dry-run dispatch currently requires `.github/workflows/release.yml` to exist, so it will become executable after the workflow slice lands. This is intentional fail-fast behavior for the task requirement that missing workflow files fail before dispatch.

## Implementation pass - 2026-06-03 (Slice 3)

Completed Slice 3 build deploy support.

- Added a maintainer-scoped `:deploy` alias in `deps.edn` with `slipset/deps-deploy`; the core `:deps` remain unchanged.
- Added `build/deploy` for `clojure -T:build:deploy deploy`. It requires non-blank `CLOJARS_USERNAME` and `CLOJARS_PASSWORD`, builds the existing core-only jar first through `jar`, and deploys the freshly generated jar plus generated pom to Clojars through `deps-deploy`.
- Kept deploy testable without a remote publish by adding injectable `:env` and `:deploy-fn` boundaries to the build task options.
- Extended focused build checks to verify credential failure behavior, deploy argument shape, generated pom coordinates, and the core-only artifact boundary excluding `scry/kaocha.clj` and `lambdaisland/kaocha`.
- Verification: `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 4 tests, 34 assertions, 0 failures, 0 errors.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 7 tests, 31 assertions, 0 failures, 0 errors.

## Implementation pass - 2026-06-03 (Slices 4 and 5)

Completed the release workflow dry-run and publishing paths.

- Added `.github/workflows/release.yml` with `push` `v*` tag triggers and dry-run-only `workflow_dispatch` inputs `ref`, `sha`, and `expected_version`.
- The workflow checks out full Git history, verifies the exact checked-out SHA when supplied by `bb release --dry-run`, uses the same Java/Clojure setup policy as CI, and runs core tests, Kaocha adapter tests, focused build checks, focused release checks, and `clojure -T:build jar`.
- Added dry-run validation for expected build version and changelog shape while keeping every `workflow_dispatch` path non-publishing.
- Added tag-push publishing validation for strict `v0.1.<non-negative-integer>` tags and tag/build version agreement before deploy or GitHub Release creation.
- Added publishing steps to extract the bracketed changelog section, deploy through `clojure -T:build:deploy deploy` using `CLOJARS_USERNAME`/`CLOJARS_PASSWORD`, and create a GitHub Release with the built jar attached.
- Verification: `mise exec -- actionlint .github/workflows/release.yml` passed.

Implementation note: the workflow does not install Babashka because it calls Clojure/test/build commands directly and uses the existing Babashka-compatible helper namespace through the `:release-test` classpath alias.

## Implementation pass - 2026-06-03 (Slice 6)

Completed maintainer documentation updates.

- Updated `AGENTS.md` with the local `bb release --dry-run`, `bb release:tag`, and `bb release` commands; dry-run ref/SHA/version behavior; Git-count release tag semantics; Clojars secret names; publishing behavior; and the focused release helper check command.
- Added a `CHANGELOG.md` Unreleased entry for the Babashka release tasks and GitHub Actions release workflow.
- Reviewed README scope and left `README.md` unchanged because the release automation does not change public API or installation examples.

## Implementation pass - 2026-06-03 (Slice 7)

Completed validation and handoff checks.

- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 7 tests, 31 assertions, 0 failures, 0 errors.
- Verification: `mise exec -- actionlint .github/workflows/release.yml` passed.
- Verification: `clojure -M:test -e "(require '[scry.core :as scry]) (let [result (scry/run)] (println (scry/report-string result)) (when-not (:pass? result) (System/exit 1)))"` passed: 33 tests, 113 pass, 0 fail, 0 error.
- Verification: `clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.kaocha-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 8 tests, 48 assertions, 0 failures, 0 errors.
- Verification: `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 4 tests, 34 assertions, 0 failures, 0 errors.
- Verification: `clojure -T:build jar` passed.
- Reviewed `git diff`; changes are limited to release workflow automation, maintainer docs/changelog, test classpath support, and Munera task bookkeeping. No public runner API or core jar source boundary changes were introduced.

Implementation deviation: after adding release helper tests under `test/`, the broad `scry/run` core test command needed the Babashka helper path available. Updated the `:test` alias to include `bb/` as a test-time path; this does not affect runtime deps or the core jar source set.

## Implementation review - 2026-06-03

Found one actionable implementation issue: the documented Babashka tasks are listed by `bb tasks`, but executing `bb release --dry-run` fails during SCI analysis with `Unable to resolve symbol: scry.release/release-command!`, so the local maintainer entry point required by the design is currently unusable. Focused release helper tests, focused build checks, core tests, and `actionlint` passed during review.

## Implementation-review follow-up execution - 2026-06-03

Completed the review-added follow-up item for Babashka task wiring.

- Changed `bb.edn` tasks to require `scry.release` inside the task body and invoke entry points through `resolve`, avoiding SCI analysis failures for qualified task symbols during `bb release --dry-run`.
- Fixed argument forwarding for `bb release --dry-run` so `*command-line-args*` is passed as one args collection to `release-command!`, matching the function's expected contract.
- Non-mutating validation: `bb release --dry-run` now resolves and executes the `scry.release` entry point; in this dirty task worktree it fails at the intended pre-dispatch clean-tree guard with `Working tree is not clean; commit or stash changes before release dry run`, rather than failing during SCI task analysis.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 7 tests, 31 assertions, 0 failures, 0 errors.

## Implementation review - 2026-06-03 (follow-up pass)

Found one new actionable implementation issue: `bb release --dry-run` now executes the task entry point, but when `origin` is missing it surfaces the generic `Git command failed` from `git remote get-url origin` instead of the required clear GitHub-origin guidance. Focused release helper tests and `actionlint` passed during review; the non-mutating dry-run path reached release validation but exposed the unclear-origin failure.

## Implementation-review follow-up execution - 2026-06-03 (origin guidance)

Completed the review-added follow-up item for `bb release --dry-run` origin guidance.

- Added explicit `origin-url` validation before GitHub origin parsing so a missing `origin` remote now fails with: `No GitHub origin remote is configured; add an origin remote pointing at GitHub before release dry-run dispatch`.
- Added `github-origin!` to wrap non-GitHub origin parse failures with maintainer-facing guidance: `origin must be a GitHub remote URL; set origin to git@github.com:OWNER/REPO.git or https://github.com/OWNER/REPO.git before release dry-run dispatch`.
- Updated `release-dry-run-plan` to use the explicit origin checks instead of routing `git remote get-url origin` through the generic Git command failure helper.
- Added focused release helper tests for missing origin, non-GitHub origin, and valid GitHub origin parsing.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 8 tests, 34 assertions, 0 failures, 0 errors.

## Implementation review - 2026-06-03 (release workflow dry-run changelog)

Found one new actionable implementation issue: the workflow dry-run changelog-shape step falls back to `## Unreleased` whenever `expected_version` is supplied but the matching bracketed release section is missing/empty. The design requires ordinary branch/SHA dry runs to accept non-empty Unreleased, but dry runs against already stamped release refs/tags should require the requested bracketed release section by the same publishing extraction rules. Focused release helper tests and `actionlint` passed; `bb release --dry-run` now reaches validation and fails clearly for the missing local GitHub origin.

## Implementation-review follow-up execution - 2026-06-03 (dry-run changelog)

Completed the review-added release workflow dry-run changelog validation item.

- Updated `.github/workflows/release.yml` so workflow-dispatch dry runs identify stamped release refs/tags by the requested `expected_version` tag (`vVERSION` or `refs/tags/vVERSION`) or by a matching tag pointing at the checked-out `HEAD`.
- For stamped release dry runs, the workflow now requires `CHANGELOG.md` to contain the matching bracketed `## [VERSION] - YYYY-MM-DD` section via `scry.release/release-section`; it no longer falls back to `## Unreleased` when that section is missing or empty.
- Ordinary branch/SHA dry runs retain the previous behavior: accept the matching bracketed release section when present, otherwise require a non-empty bare `## Unreleased` section.
- Verification: `mise exec -- actionlint .github/workflows/release.yml` passed.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 8 tests, 34 assertions, 0 failures, 0 errors.

## Implementation review - 2026-06-03 (post-follow-up)

No new actionable implementation-quality issues found. Reviewed the release Babashka task wiring, release helper logic/tests, deploy task boundary, release workflow dry-run/publishing conditions, and maintainer docs against the task design/plan. Focused release helper tests and `actionlint` pass; `bb release --dry-run` resolves and reaches the expected local pre-dispatch GitHub-origin validation in this worktree.

## Test review - 2026-06-03

Found actionable test-quality feedback. Focused release/build checks pass, and the helper tests cover version math, tag validation, changelog stamping/extraction, ref agreement, origin guidance, and deploy boundaries. Gaps remain around higher-level release automation behavior: the dry-run dispatch command/input contract is not covered end-to-end through the injectable command boundary, and the workflow dry-run changelog validation branch that previously needed tightening is embedded in YAML rather than extracted into tested helper logic.

## Test-review follow-up execution - 2026-06-03

Completed both review-added test follow-up items.

- Added `dry-run-dispatch-contract-test`, which exercises `dispatch-dry-run!` through an injectable command boundary, validates the successful non-mutating dry-run plan, and asserts the exact `gh workflow run .github/workflows/release.yml --ref master -f ref=master -f sha=<sha> -f expected_version=0.1.41` dispatch contract.
- Extracted workflow dry-run changelog-shape behavior into `scry.release/dry-run-changelog-section` and updated `.github/workflows/release.yml` to call it. Added focused tests covering stamped refs/tags and tagged-HEAD dry runs requiring the matching bracketed `## [VERSION] - YYYY-MM-DD` section, plus ordinary branch dry runs falling back to non-empty `## Unreleased`.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 10 tests, 42 assertions, 0 failures, 0 errors.
- Verification: `mise exec -- actionlint .github/workflows/release.yml` passed.
## Test review - 2026-06-03 (post-follow-up)

Found one new actionable test-quality gap. Focused release helper tests and `actionlint` pass, and the previous dry-run dispatch/changelog-shape gaps are covered. Remaining gap: the real tagging orchestration (`create-release-tag!` / `bb release:tag`) is only covered indirectly by pure helpers; no focused test exercises the high-level release-tag path in an isolated temp repo/workdir to prove it computes `0.1.<current-count + 1>`, stamps `CHANGELOG.md`, and issues the expected `git add`/`commit`/`tag` sequence without mutating the real repository.

## Test-review follow-up execution - 2026-06-03 (release-tag orchestration)

Completed the review-added high-level release-tag test follow-up.

- Added an isolated `create-release-tag-orchestration-test` that writes `CHANGELOG.md` in a temporary workdir and exercises `create-release-tag!` through an injectable command boundary, without mutating the real repository.
- Added an optional `:changelog-file` injection point to `create-release-tag!` so the high-level path can stamp an isolated changelog while preserving the production default of `CHANGELOG.md` and the production `git add CHANGELOG.md` / `git commit -m Release vVERSION` / `git tag vVERSION` command sequence.
- The test verifies off-by-one versioning from current count `41` to release version `0.1.42`, the stamped bracketed changelog section, preservation of the prior release section, and the expected `git add`/`commit`/`tag` calls.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 11 tests, 51 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03 (push release orchestration)

Found one new actionable test-quality gap. Focused release helper tests pass, but `bb release`/`push-release!` partial-failure recovery is still covered only by the pure `existing-tag-push-plan`; no focused test exercises the high-level push path through the injectable command boundary to prove an existing unpushed release tag at `HEAD` pushes `master` and that tag without creating a second tag.

## Test-review follow-up execution - 2026-06-03 (push release existing-tag recovery)

Completed the review-added high-level `push-release!`/`bb release` existing-tag recovery test follow-up.

- Added `push-release-existing-tag-orchestration-test`, which exercises `push-release!` through an injectable command boundary for the partial-failure state where `HEAD` already has local `v0.1.42` and `origin` does not yet advertise that tag.
- The test verifies `push-release!` pushes `master` and the existing `v0.1.42` tag, returns `{:tag "v0.1.42" :action :push-existing-tag}`, and does not run `git add CHANGELOG.md`, `git commit -m Release v0.1.42`, or `git tag v0.1.42` to create a second release tag.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 12 tests, 54 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03 (workflow presence guard)

Found one new actionable test-quality gap. Focused release tests pass, but `release-dry-run-plan` still checks `.github/workflows/release.yml` through the real filesystem before dispatch; the required clear failure for a missing workflow file is not covered, and that filesystem dependency is not injectable/nullable for focused tests without mutating the repository.

## Test-review follow-up execution - 2026-06-03 (workflow presence guard)

Completed the review-added workflow presence guard test follow-up.

- Made the release workflow presence guard injectable for `release-dry-run-plan` through optional `:workflow-file-present?` input while preserving the production default of checking `.github/workflows/release.yml` on disk.
- Added `dry-run-missing-workflow-guard-test`, a non-mutating focused test proving `release-dry-run-plan` fails clearly with `Release workflow file is missing; add .github/workflows/release.yml before dry-run dispatch` before attempting `gh` dispatch or other remote checks when the workflow is absent.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 13 tests, 56 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03 (dry-run dispatch isolation)

Found one new actionable test-quality gap. Focused release/build checks and `actionlint` pass, but the high-level `dispatch-dry-run!` path still cannot receive the injectable workflow-file presence guard; its successful-dispatch test therefore depends on the real `.github/workflows/release.yml` file, and the missing-workflow failure is only covered at `release-dry-run-plan` rather than the maintainer-facing dispatch entry point.

## Test-review follow-up execution - 2026-06-03 (dry-run dispatch isolation)

Completed the review-added high-level dry-run dispatch isolation follow-up.

- Updated `dispatch-dry-run!` to pass an optional `:workflow-file-present?` guard through to `release-dry-run-plan`, preserving the production default while allowing isolated high-level dispatch tests.
- Updated the successful dry-run dispatch contract test to inject `:workflow-file-present? true`, so it no longer depends on the real `.github/workflows/release.yml` file.
- Added `dry-run-dispatch-missing-workflow-guard-test`, which exercises `dispatch-dry-run!` through an injectable command boundary and proves a missing workflow file fails clearly after only the clean-tree check, before `gh` readiness or workflow dispatch.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 14 tests, 58 assertions, 0 failures, 0 errors.

## Test review - 2026-06-03 (release workflow publishing gates)

Found one new actionable test-quality gap. Focused release helper tests pass, but the release workflow's most safety-critical publishing gates are only validated by `actionlint`/manual inspection: there is no focused test or static check asserting that Clojars deploy, GitHub Release creation, and asset upload are conditioned only on tag-push publishing runs and are unreachable from `workflow_dispatch` dry runs.

## Test-review follow-up execution - 2026-06-03 (release workflow publishing gates)

Completed the review-added static workflow gate coverage follow-up.

- Added `release-workflow-publishing-gates-test`, a focused static check over `.github/workflows/release.yml` that extracts the relevant YAML steps and asserts the safety-critical publishing steps use the same tag-push-only condition as publishing validation: `github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')`.
- The test covers release note extraction, Clojars deploy, and GitHub Release creation/jar attachment, and asserts those steps do not include `workflow_dispatch` or `||` escape conditions.
- The test also asserts those publishing steps appear after `Validate publishing tag and version`, so they remain limited to validated tag-push publishing runs.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 15 tests, 73 assertions, 0 failures, 0 errors.

## Implementation review - 2026-06-03 (post-test-follow-up)

No new actionable implementation-quality issues found. Reviewed the latest release helper/task code, Babashka entry points, deploy boundary, release workflow publishing/dry-run gates, maintainer docs, and review-added tests against the design and plan. Focused release tests and `actionlint` pass; `bb release --dry-run` resolves and reaches the expected clear local GitHub-origin validation in this worktree.

## Test review - 2026-06-03 (final pass)

No new actionable test-quality issues found. Reviewed the task design/plan against the focused release/build tests, release helper/task coverage, deploy boundary checks, workflow dry-run changelog helper coverage, high-level Babashka dry-run/tag/push orchestration tests, and static publishing-gate workflow check. Focused release helper tests pass (15 tests, 73 assertions), and `actionlint` passes for `.github/workflows/release.yml`.

## Test-shaper review - 2026-06-03

No new actionable test-quality issues found. Reviewed release helper/task tests, build/deploy checks, workflow dry-run changelog helper coverage, high-level Babashka orchestration tests, and static publishing-gate checks for clarity, signal, determinism, boundary coverage, and economy. Focused release helper tests pass (15 tests, 73 assertions), and `actionlint` passes for `.github/workflows/release.yml`.

## Docs review - 2026-06-03

No new actionable documentation issues found. Reviewed README, AGENTS.md, CHANGELOG.md, release workflow docs/input text, bb task docs, and build/deploy docs against the implemented release automation. README appropriately remains public API focused; AGENTS.md documents maintainer release commands, dry-run behavior, Git-count tag semantics, Clojars secrets, deploy behavior, and focused release checks; CHANGELOG.md records the user-visible release automation.

## Code-shaper review - 2026-06-03

Found one actionable code-quality issue: release Git state predicates are not fail-closed. `clean-working-tree?` and related branch checks read command output directly without validating process exit, so a failed `git status --porcelain` can be treated as a clean tree and later surface as a less local failure. Focused release helper tests and `actionlint` pass.

## Code-shaper follow-up execution - 2026-06-03

Completed the review-added fail-closed Git state check follow-up.

- Updated `clean-working-tree?` to run `git status --porcelain` through `run-command!` and fail with `Failed to inspect working tree status` before trusting stdout when Git exits non-zero.
- Updated `current-branch` to run `git rev-parse --abbrev-ref HEAD` through `run-command!` and fail with `Failed to resolve current branch` before treating stdout as the current branch.
- Added focused release helper coverage proving failed Git state commands fail closed rather than being interpreted as clean/master state.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 15 tests, 75 assertions, 0 failures, 0 errors.

## Code-shaper review - 2026-06-03 (post-follow-up)

Found one new actionable code-quality issue: release tag-at-HEAD detection is not locally robust when multiple valid `v0.1.N` tags point at `HEAD`. `release-tags-at-head` sorts all valid tags and `current-release-tag-state` silently chooses the first, which can push/report the wrong tag in an anomalous retry state instead of failing clearly. Focused release helper tests pass (15 tests, 75 assertions), and `actionlint` passes for `.github/workflows/release.yml`.

## Code-shaper follow-up execution - 2026-06-03 (multiple release tags at HEAD)

Completed the review-added tag-at-HEAD ambiguity follow-up.

- Added `release-tag-at-head!` so release retry/push state detection fails clearly when more than one valid `v0.1.N` tag points at `HEAD`, rather than silently selecting the first sorted tag.
- Updated `current-release-tag-state` to use the new guard before checking whether the tag has been pushed.
- Added focused high-level `push-release!` coverage for the anomalous state with multiple valid release tags at `HEAD`; the test verifies the task fails before querying remote tag state or pushing.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 16 tests, 77 assertions, 0 failures, 0 errors.

## Code-shaper review - 2026-06-03 (release args)

Found one new actionable code-quality issue: `parse-release-args` accepts `bb release --ref REF` without `--dry-run`, but the normal release path ignores `:ref`, making a maintainer option silently ineffective. Tighten the CLI contract so `--ref` is only accepted with `--dry-run` or fails clearly. Focused release helper tests pass (16 tests, 77 assertions), and `actionlint` passes for `.github/workflows/release.yml`.

## Code-shaper follow-up execution - 2026-06-03 (release args)

Completed the review-added CLI-contract follow-up.

- Tightened `parse-release-args` so `--ref` is accepted only with `--dry-run`; `bb release --ref REF` now fails clearly with `--ref is only supported with --dry-run` instead of silently ignoring the selected ref on the normal release path.
- Added focused release argument parsing coverage for valid `--dry-run --ref REF`, rejected `--ref REF` without `--dry-run`, and missing `--ref` values.
- Verification: `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"` passed: 17 tests, 80 assertions, 0 failures, 0 errors.
- Non-mutating CLI validation: `bb release --ref master` now reaches the argument guard and exits with `--ref is only supported with --dry-run`.

## Code-shaper review - 2026-06-03 (final pass)

No new actionable code-quality issues found. Reviewed release helper/task code, Babashka CLI parsing, deploy boundary, release workflow gates, and focused tests for simplicity, consistency, local comprehensibility, and fail-closed behavior. Focused release helper tests pass (17 tests, 80 assertions).
