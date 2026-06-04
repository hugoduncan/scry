# Plan

## Approach

Add release automation in small, testable layers: first isolate release helper behavior in Babashka/Clojure-friendly functions, then wire local `bb` tasks, then add the GitHub Actions release workflow, deploy support, documentation, and validation. Keep the release path conservative and aligned with the existing build boundary: the published artifact is the same core-only jar produced by `clojure -T:build jar`, versioned as `0.1.<git commit count>` from full Git history.

Key decisions:

- Add a minimal `bb.edn` with maintainer-facing tasks using these names unless implementation discovers a strong reason to adjust them:
  - `bb release --dry-run` dispatches the non-publishing release workflow for an exact remote-backed ref.
  - `bb release:tag` validates local state, stamps `CHANGELOG.md`, commits, and creates a local `vVERSION` tag.
  - `bb release` ensures/creates the local release tag and pushes `master` plus tags.
- Put release implementation in `bb/release.clj` so `bb.edn` stays small and helper functions can be tested directly.
- Make dry-run dispatch non-mutating. It resolves the exact commit to be checked out remotely, computes the expected version for that exact commit with Git-count semantics, validates local/remote agreement, and passes both ref/SHA and expected version to `.github/workflows/release.yml`.
- Make real tagging explicit about the off-by-one count: before creating the changelog release commit, compute the tag version as `0.1.<current commit count + 1>`, then verify or structure the commit/tag path so the tag commit builds `target/scry-VERSION.jar`.
- Stamp changelog releases only with bracketed headings: keep a fresh empty `## Unreleased` section and move the previous non-empty body under `## [VERSION] - YYYY-MM-DD`.
- Add release workflow inputs for dry-run `workflow_dispatch`: `ref` (branch/tag/SHA expression to check out) and `sha` (the exact 40-character commit SHA resolved by `bb release --dry-run`), plus `expected_version`. The dry-run task passes both `ref` and `sha`; the workflow checks out `ref` with full history and immediately verifies `git rev-parse HEAD` equals `inputs.sha` before running tests/builds. If a maintainer dispatches manually without `sha`, the workflow may skip only the exact-SHA equality check, but the documented `bb release --dry-run` path must always supply it. Publishing happens only on pushed tags matching `v0.1.<non-negative-integer>`.
- Reuse the CI verification commands and setup policy from `.github/workflows/ci.yml`: full-history checkout, `actions/setup-java@v4` with Temurin JDK 21, and `DeLaGuardo/setup-clojure@13.4` unless implementation finds a documented incompatibility.
- Add deploy support through a maintainer/release-scoped `:deploy` alias using `deps-deploy`; do not add deploy dependencies to normal runtime or change the core-only jar contents. The deploy task contract is:
  - Command: `clojure -T:build:deploy deploy`.
  - Credentials: read `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` from the environment; fail clearly before deploy if either is missing or blank. `CLOJARS_PASSWORD` may be a Clojars deploy token/password according to maintainer secret setup.
  - Artifact inputs: deploy builds the jar first by invoking the existing `jar` task, then deploys that freshly built `:jar-file` plus the matching generated `target/classes/META-INF/maven/org.hugoduncan/scry/scry/pom.xml`; callers do not pass jar/pom paths in normal use.
  - Coordinates: deploy `org.hugoduncan/scry` at the freshly built Git-count version, using the same core-only source set and pom dependency boundary as `clojure -T:build jar`.
  - Workflow/docs/focused checks should use this command and contract consistently.
- Add focused tests/checks around pure or injectable release helpers, especially changelog extraction/stamping, version/tag validation, and Git-count version calculation. Put these checks in `test/scry/release_test.clj`, load release helper code from `bb/` via a dedicated `:release-test` alias with `:extra-paths ["test" "bb"]`, and run them with:

  ```sh
  clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"
  ```

- Validate workflow YAML locally with `actionlint`, and keep README changes out of scope unless implementation changes public installation/API information.

## Risks

- Git commit-count versioning makes release tagging sensitive to off-by-one errors; tests and helper names should make “current count” versus “release commit count” explicit.
- Dry-run dispatch can be misleading if the selected local commit and remote ref differ; the task must fail before dispatching unless they resolve to the same commit.
- Partial failures after a local release tag is created can lead to duplicate-release hazards; `bb release` must detect matching existing tags and either push the existing unpushed tag or fail with clear instructions rather than creating a second release.
- GitHub Actions dry-run behavior must never deploy or create a GitHub Release; publishing conditions should be simple and tag-event-only.
- Clojars deploy details depend on secret naming and `deps-deploy` conventions; document the chosen secrets and keep credentials unavailable/unused on dry runs.
- Changelog parsing can accidentally extract the wrong section if release headings are loose; use bracketed heading rules exactly as specified.
- Workflow checkout/ref handling for `workflow_dispatch` can be subtle; validate that dry-run checks out the selected ref/SHA and compares the built version to the expected version.

## Slice order

1. **Release helper design and tests** — add testable helper functions for version math, tag validation, changelog stamping/extraction, command boundaries, and temporary-repo Git behavior.
2. **Babashka task wiring** — add `bb.edn` and `bb/release.clj` tasks for dry-run dispatch, local release tagging, existing-tag recovery, and push behavior.
3. **Build deploy support** — add maintainer-scoped deploy dependencies/aliases and a `build/deploy` task that publishes the same core-only jar shape as the existing build.
4. **Release workflow skeleton and dry-run path** — add `.github/workflows/release.yml` with tag and manual triggers, full-history checkout, setup, verification commands, jar build, expected-version checks, and guaranteed non-publishing dry-run behavior.
5. **Publishing path** — add tag format validation, tag/build version agreement, Clojars deploy on tag pushes only, changelog-section extraction, and GitHub Release creation with the jar attached.
6. **Maintainer documentation** — update `AGENTS.md` with release commands, dry-run behavior, Clojars secrets, tag/version semantics, and release maintenance guidance; add a `CHANGELOG.md` Unreleased entry.
7. **Validation and handoff** — run focused release helper tests, existing core/Kaocha/build checks, jar build, and `actionlint`; record commands and outcomes in `implementation.md`.
