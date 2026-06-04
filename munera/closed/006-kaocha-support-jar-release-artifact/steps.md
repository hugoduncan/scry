# Steps

## Slice 1: Build artifact model

- [x] Inspect current `build.clj` helpers, `deps.edn` aliases, and focused build tests to confirm the existing core-only assumptions.
- [x] Add build constants for adapter coordinate `org.hugoduncan/scry-kaocha`, adapter source directory `src-kaocha`, adapter class directory such as `target/classes-kaocha`, and versioned adapter jar path `target/scry-kaocha-VERSION.jar`.
- [x] Add a deterministic helper that reads or centralizes the `lambdaisland/kaocha` version from the development `:kaocha` alias source.
- [x] Choose one Kaocha dependency source-of-truth mechanism for the plan (`deps.edn` read vs build constant), document the drift-prevention rule, and make the focused test fail if `:kaocha` alias and adapter pom version diverge.
- [x] Refactor core jar path and pom path helpers so core and adapter paths can be resolved independently without changing `clojure -T:build jar` behavior.
- [x] Implement `kaocha-jar` to copy only adapter sources, write an adapter pom for `org.hugoduncan/scry-kaocha`, and build `target/scry-kaocha-VERSION.jar`.
- [x] Decide and document whether standalone `kaocha-jar` cleans all `target/` output like `jar`, deletes only adapter outputs, or preserves an existing core jar; align tests/docs with that stale-output behavior.
- [x] Ensure the adapter pom declares same-version `org.hugoduncan/scry` and the pinned `lambdaisland/kaocha` dependency.
- [x] Implement `jars` to clean once, build the core jar and adapter jar, and return both artifact records with explicit jar and pom paths.
- [x] Preserve `clean` so it removes all generated build output, including adapter class and pom output.

## Slice 2: Focused build coverage

- [x] Update focused build tests to assert `clojure -T:build jar` remains core-only and returns the existing core coordinate and jar shape.
- [x] Add focused build tests that the adapter jar contains `scry/kaocha.clj`.
- [x] Add focused build tests that the adapter jar does not contain core classes such as `scry/core.clj`, unless implementation documents a reason to duplicate them.
- [x] Add focused build tests that the adapter pom uses `org.hugoduncan/scry-kaocha`.
- [x] Add focused build tests that the adapter pom depends on same-version `org.hugoduncan/scry`.
- [x] Add focused build tests that the adapter pom depends on `lambdaisland/kaocha` at the version used by `deps.edn` `:kaocha`.
- [x] Keep focused build tests asserting the core jar excludes `scry/kaocha.clj`.
- [x] Keep focused build tests asserting the core pom excludes `lambdaisland/kaocha`.
- [x] Add focused build tests for combined `jars` return shape, including both jar files and both pom files.
- [x] Add focused build tests that core and adapter pom paths are distinct and not clobbered.

## Slice 3: Deploy-all support

- [x] Keep `clojure -T:build:deploy deploy` as a core-only deploy command.
- [x] Implement `deploy-all` to require the same `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment contract as `deploy`.
- [x] Make `deploy-all` build or reuse both release artifacts with explicit jar and pom paths.
- [x] Make `deploy-all` call deps-deploy for the core artifact before the adapter artifact.
- [x] Add focused build tests for missing credentials on `deploy-all`.
- [x] Add focused build tests for `deploy-all` deploy argument shape.
- [x] Add focused build tests that `deploy-all` deploys core before adapter.

## Slice 4: Release workflow integration

- [x] Update `.github/workflows/release.yml` to build both jars with `clojure -T:build jars` or the chosen combined command.
- [x] Update artifact resolution in the workflow to capture separate core and adapter jar paths.
- [x] Specify exact workflow artifact matching patterns so the core lookup cannot accidentally match `scry-kaocha-*.jar` and each lookup fails clearly unless exactly one jar is present.
- [x] Update workflow version derivation to verify the core and adapter jar filenames both resolve to the same version.
- [x] Update dry-run expected-version validation to compare the expected version against both built artifact versions.
- [x] Update publishing tag validation to validate the tag against the shared artifact version after both jar versions agree.
- [x] Change the publishing deploy step to use `clojure -T:build:deploy deploy-all`.
- [x] Ensure deploy and GitHub Release creation remain gated to publishing tag pushes and never run for `workflow_dispatch` dry runs.
- [x] Update GitHub Release creation to attach both the core jar and the Kaocha adapter jar.

## Slice 5: Release/static checks

- [x] Inspect focused release helper tests for workflow command/artifact assumptions that mention only the core jar.
- [x] Update focused release tests or static workflow checks to cover combined artifact build, deploy-all usage, and two jar attachments where practical.
- [x] Run `actionlint` against the updated workflow.

## Slice 6: Documentation and changelog

- [x] Update `AGENTS.md` maintainer build workflow with `kaocha-jar`, `jars`, and `deploy-all` behavior.
- [x] Update `AGENTS.md` release workflow text so it states both core and adapter jars are built, verified, deployed, and attached.
- [x] Update `CHANGELOG.md` Unreleased with the new optional Kaocha adapter artifact and release behavior.
- [x] Decide whether README should include public installation guidance for `org.hugoduncan/scry-kaocha`; update README if that coordinate is presented to users.

## Slice 7: Verification

- [x] Run core tests with `clojure -M:test -e "(require '[scry.core :as scry]) (let [result (scry/run)] (println (scry/report-string result)) (when-not (:pass? result) (System/exit 1)))"`.
- [x] Run focused Kaocha adapter tests with `clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.kaocha-test)] (when-not (ct/successful? result) (System/exit 1)))"`.
- [x] Run focused build checks with `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"`.
- [x] Run focused release checks with `clojure -M:test:release-test -e "(require '[scry.release-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.release-test)] (when-not (ct/successful? result) (System/exit 1)))"`.
- [x] Run `clojure -T:build jars` and verify both jar files are produced under `target/`.
- [x] Run `actionlint`.

## Review follow-up: test-shaper

- [x] Strengthen focused build checks so the adapter pom assertion binds `org.hugoduncan/scry` to the same build version within the dependency entry (for example by parsing dependency coordinates or asserting a scoped dependency block), not merely by finding the artifact id and version independently.
- [x] Add a focused build test that creates a stale `target/scry-kaocha-*.jar`, runs standalone `kaocha-jar`, and asserts the stale adapter jar is removed while any existing core jar is preserved.

## Review follow-up: code-shaper

- [x] Replace `build.clj` `deps.edn` parsing via Clojure `read-string` with `clojure.edn/read-string` for data-only EDN parsing of the Kaocha alias dependency metadata.
- [x] Replace `test/scry/build_test.clj` `deps.edn` parsing via Clojure `read-string` with `clojure.edn/read-string` so focused tests use the same data-only EDN parser as `build.clj`.
