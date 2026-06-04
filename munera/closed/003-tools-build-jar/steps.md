# Steps

## Slice 1: Inspect and shape build inputs

- [x] Confirm `deps.edn` currently has only `src` as the default path and that `src-kaocha` is alias-only.
- [x] Confirm `.gitignore` ignores `target/` or update it before producing build artifacts.
- [x] Inspect current source/test layout so build checks can verify expected included and excluded paths.

## Slice 2: Add build alias and script

- [x] Add `:build` alias to `deps.edn` with `io.github.clojure/tools.build` and `:ns-default build`.
- [x] Create `build.clj` with constants for `org.hugoduncan/scry`, major/minor `0.1`, `target/classes`, and source dirs.
- [x] Implement a helper that computes the version as `0.1.<git-revcount>` by running `git rev-list --count HEAD`.
- [x] Make the version helper fail with a clear exception/message when the Git command fails or returns an invalid count.
- [x] Implement `clean` to delete `target`.
- [x] Implement `jar` to clean, copy only `src`, write the pom, and build a jar under `target/` with artifact id and computed version in the filename.
- [x] Defer `install` for this task; implement only `clean` and `jar` for the initial artifact build, leaving local Maven installation for a later explicit task if needed.

## Slice 3: Add focused build checks

- [x] Add `test/scry/build_test.clj` check that verifies the computed version prefix is `0.1.` and the patch equals `git rev-list --count HEAD`; run it with `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"`.
- [x] In `test/scry/build_test.clj`, add a check that runs the jar build and verifies the jar exists under `target/` with the computed version in its filename.
- [x] In `test/scry/build_test.clj`, add a check that verifies the generated pom contains group `org.hugoduncan`, artifact `scry`, and the computed version.
- [x] In `test/scry/build_test.clj`, add a check that verifies the jar includes core `scry` namespaces from `src`.
- [x] In `test/scry/build_test.clj`, add a check that verifies the jar excludes `test`, `munera`, `mementum`, `.psi`, `.cpcache`, and `src-kaocha`/`scry/kaocha.clj`.

## Slice 4: Update documentation

- [x] Add an `AGENTS.md` maintainer build section documenting `clojure -T:build clean`, `clojure -T:build jar`, that `install` is deferred, and the focused build-check command `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"`.
- [x] Document that jar versions are generated as `0.1.<git rev-list --count HEAD>` and fail when Git metadata is unavailable.
- [x] Document that the initial jar packages core `src` only and does not include the optional Kaocha adapter.
- [x] Add a README artifact-consumption note only if the implementation introduces user-facing artifact guidance.

## Slice 5: Verify and finalize

- [x] Run the existing core test suite.
- [x] Run focused Kaocha adapter tests if changes could affect alias/dependency boundaries.
- [x] Run the new focused build checks with `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"`.
- [x] Run `clojure -T:build jar` successfully.
- [x] Inspect the generated jar and pom contents to confirm acceptance criteria.
- [x] Record implementation decisions and verification results in `implementation.md`.
## Review follow-up: plan ambiguity - 2026-06-02

- [x] Clarify in `plan.md`/`steps.md` where the focused build checks live and the exact command that runs them, so Slice 3 and Slice 5 verification are not left as unspecified "test/check" work.
- [x] Decide and document whether the optional `install` task is part of this task; if included, specify its command and verification, and if deferred, remove/defer the checklist item so implementation does not depend on interpreting "if low-cost".

## Review follow-up: plan inconsistency - 2026-06-02

- [x] Update `plan.md` so the build-alias decision no longer says maintainers can optionally run `install`; keep it consistent with the explicit task scope of implementing only `clean` and `jar` while deferring local Maven installation.

## Review follow-up: test review - 2026-06-02

- [x] Add focused tests for `build/version` or its helper covering non-zero Git command failures and invalid non-numeric counts, using an injectable/nullable process boundary rather than mocks/stubs so the required clear `ex-info` failure behavior is verified.

## Review follow-up: test-shaper - 2026-06-02

- [x] Add a focused build-test assertion that the generated pom does not include the optional Kaocha dependency (for example `lambdaisland/kaocha`), so the core-only artifact's dependency boundary is verified as well as its jar entries.

## Review follow-up: docs review - 2026-06-02

- [x] Add a CHANGELOG.md Unreleased entry for the new tools.build jar workflow/artifact, including the `clojure -T:build jar` command, `org.hugoduncan/scry` coordinate, Git-derived `0.1.<git-revcount>` versioning, and core-only packaging/optional Kaocha exclusion.
