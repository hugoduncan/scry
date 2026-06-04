# Implementation notes

Created for the public-readiness pass before making the repository public.

2026-06-04 architecture review: No new actionable architectural-fit feedback. The design keeps the change in the build/POM metadata surface, covers both core and optional Kaocha artifacts, preserves coordinates/versioning and the core/adapter dependency boundary, and requires focused build verification. Consulted AGENTS.md; META.md and doc/architecture.md are absent.

2026-06-04 ambiguity review: Found three actionable design ambiguities. The design requires "correct"/"equivalent" public POM metadata but does not pin the exact values for names, descriptions, URLs, SCM coordinates, or maintainer/developer fields; license metadata references EPL-2.0 but does not specify the exact Maven license elements/URL/SPDX-id treatment; and test acceptance does not say whether to verify filesystem POMs, jar-embedded POMs, or both. Added follow-up items to `design-steps.md`.

2026-06-04: Completed ambiguity-review design follow-ups. `design.md` now pins exact core and adapter metadata values: artifact names/descriptions, GitHub project URL, SCM URL/connection/developerConnection, deterministic `v${version}` SCM tag policy, and a shared Hugo Duncan developer/maintainer entry while explicitly omitting organization/timezone/properties. It also pins EPL-2.0 Maven license metadata with name `Eclipse Public License 2.0`, URL `https://www.eclipse.org/legal/epl-2.0/`, distribution `repo`, and SPDX id in `<comments>` as `SPDX-License-Identifier: EPL-2.0`. Metadata verification now requires focused build tests to inspect both filesystem POMs and jar-embedded POMs for both artifacts while preserving existing dependency/boundary assertions. All review-added items in `design-steps.md` are checked.

2026-06-04 inconsistency review: No new actionable inconsistency feedback. Reviewed `design.md` against the checked-in build/POM code (`build.clj`, `deps.edn`, `test/scry/build_test.clj`), release workflow expectations, AGENTS/README public artifact docs, and task `011-add-project-license`'s EPL-2.0 handoff. The pinned core/adapter metadata, EPL-2.0 Maven license fields, SCM `v${version}` tag policy, filesystem plus jar-embedded POM verification, and preserved core/adapter dependency boundary are internally consistent with the referenced artifacts. No new `design-steps.md` items were added.

2026-06-04 plan ambiguity review: No new actionable ambiguity feedback. Reviewed `plan.md` and `steps.md` against `design.md`, existing `build.clj` core/manual adapter POM generation, `test/scry/build_test.clj`, `deps.edn`, README/AGENTS artifact docs, and the checked design follow-ups. The plan pins the implementation slices, shared metadata model, filesystem plus jar-embedded POM coverage, existing dependency/boundary preservation, and final verification commands clearly enough for implementation. No new `steps.md` items were added.

2026-06-04 plan inconsistency review: No new actionable inconsistency feedback. Reviewed `plan.md` and `steps.md` against `design.md`, existing build/POM generation in `build.clj`, focused build coverage in `test/scry/build_test.clj`, `deps.edn`, AGENTS/README artifact guidance, and the task 011 EPL-2.0 handoff. The slice order, metadata helper model, pinned core/adapter metadata, filesystem plus jar-embedded POM test expectations, dependency/boundary preservation, and final verification steps are mutually consistent. No new `steps.md` items were added.

2026-06-04 implementation pass: Added shared public POM metadata helpers in `build.clj` for the GitHub project URL, SCM connections, deterministic `v${version}` SCM tag, EPL-2.0 Maven license metadata, and the shared Hugo Duncan developer/maintainer entry. Core `tools.build/write-pom` now receives `:scm` and `:pom-data`; the manual adapter POM XML now emits matching metadata while preserving same-version core and Kaocha alias-derived dependencies. Focused build tests now verify pinned metadata in both filesystem and jar-embedded POMs for core and adapter artifacts, while retaining existing coordinate/dependency/packaging-boundary assertions.

Verification:
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — passed: 6 tests, 140 assertions, 0 failures, 0 errors.
- `clojure -T:build jars` — passed and produced both current git-count versioned jars under `target/`.
- Diff inspection found only build/POM metadata generation, focused build-test assertions, and Munera task note/checklist updates; coordinates, versioning, dependency declarations, and core/adapter packaging boundaries remain unchanged.

2026-06-04 implementation review: No new actionable implementation-quality issues. Reviewed `build.clj` shared/core/adapter POM metadata generation, focused build-test coverage for filesystem and jar-embedded POMs, generated core/adapter POM contents, dependency and packaging-boundary preservation, and task/design alignment. Focused build checks and `clojure -T:build jars` pass.

2026-06-04 test review: Found two actionable test-quality issues. The POM metadata tests assert the project `<url>` with an unscoped string search, so the check can pass from the SCM `<url>` even if the artifact-level URL is missing. The tests also do not cover the design-pinned omission of developer organization, organization URL, timezone, and properties fields for both artifacts/locations.

2026-06-04 test-review follow-up implementation: Strengthened focused POM metadata tests by parsing generated POM XML and checking project-level children directly. Artifact `<url>` assertions now read the direct `<project><url>` child for both core and adapter filesystem and jar-embedded POMs, so SCM/license/developer URLs cannot satisfy the check. Added negative assertions that the direct project omits `<organization>` and `<properties>`, and that the developer entry omits `<organization>`, `<organizationUrl>`, `<timezone>`, and `<properties>` across both artifacts/locations. Marked both review-added `steps.md` items complete.

Verification:
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — passed: 6 tests, 160 assertions, 0 failures, 0 errors.
- `clojure -T:build jars` — passed.

2026-06-04 latest test review: Found one actionable test-quality issue. Focused POM metadata tests now scope project-level fields and omitted fields well, but they still inspect only the first direct `<license>` and first direct `<developer>` entry. The design pins one developer entry and a single license metadata entry, so tests should fail if extra license/developer entries are generated in either filesystem or jar-embedded POMs for either artifact. Verification rerun during review: focused build checks passed (6 tests, 160 assertions), and `clojure -T:build jars` passed.

2026-06-04 latest test-review follow-up implementation: Strengthened focused POM metadata tests to count direct metadata entries. `assert-public-pom-metadata` now parses the direct `<licenses><license>` children and direct `<developers><developer>` children and asserts exactly one of each for every checked POM, covering both core and adapter filesystem and jar-embedded POMs. Marked the review-added `steps.md` item complete.

Verification:
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — passed: 6 tests, 168 assertions, 0 failures, 0 errors.
- `clojure -T:build jars` — passed.

2026-06-04 latest test review: No new actionable test-quality issues. Reviewed `design.md`, `plan.md`, `steps.md`, `build.clj`, and `test/scry/build_test.clj`; focused POM metadata tests now cover pinned core/adapter metadata in filesystem and jar-embedded POMs, direct project URL scoping, omitted optional fields, exact direct license/developer counts, dependency metadata, and packaging boundaries. Verification rerun during review: focused build checks passed (6 tests, 168 assertions), and `clojure -T:build jars` passed. No new `steps.md` items were added.

2026-06-04 test-shaper review: Found one actionable test-shaping issue. The focused POM metadata exact-count assertions only inspect the first direct `<licenses>` container and the first direct `<developers>` container. A POM with an extra sibling container and extra license/developer metadata could still pass, weakening the design-pinned single-entry invariant across filesystem and jar-embedded POMs. Added a follow-up item to count entries across all direct containers or assert single containers. Verification rerun during review: focused build checks passed (6 tests, 168 assertions), and `clojure -T:build jars` passed.

2026-06-04 test-shaper follow-up implementation: Strengthened focused POM metadata exact-count assertions so they count all direct `<licenses>` containers and all direct `<developers>` containers, then count direct `<license>`/`<developer>` entries across every direct container. This prevents duplicate sibling metadata containers from hiding extra license/developer entries in either filesystem or jar-embedded POM checks for both artifacts. Marked the review-added `steps.md` item complete.

Verification:
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — passed: 6 tests, 176 assertions, 0 failures, 0 errors.
- `clojure -T:build jars` — passed.

2026-06-04 latest test-shaper review: No new actionable test-quality issues. Reviewed `design.md`, `plan.md`, `steps.md`, `build.clj`, and `test/scry/build_test.clj` with the test-shaper criteria for clarity, signal, robustness, economy, and behavior-focused coverage. Focused POM metadata tests now parse both filesystem and jar-embedded POMs for core and adapter artifacts, assert direct project metadata, SCM/license/developer values, intentionally omitted fields, exact direct license/developer containers and entries, dependency metadata, and packaging boundaries. Verification rerun during review: focused build checks passed (6 tests, 176 assertions), and `clojure -T:build jars` passed. No new `steps.md` items were added.

2026-06-04 docs review: Found one actionable documentation issue. The generated core and optional Kaocha artifact POMs now expose public Clojars metadata (project name/description/url, EPL-2.0 license metadata, SCM, and maintainer/developer fields), but `CHANGELOG.md` Unreleased does not mention this user-visible artifact metadata change. README's installation/license guidance remains accurate, no `doc/` directory is present, and no README examples need updates. Added a follow-up item to `steps.md`.

2026-06-04 docs-review follow-up implementation: Updated `CHANGELOG.md` Unreleased to mention the new public Maven POM metadata for both `org.hugoduncan/scry` and `org.hugoduncan/scry-kaocha`, including project descriptions, project URL, EPL-2.0 license metadata, SCM metadata, and maintainer/developer metadata for Clojars consumers. Marked the review-added `steps.md` item complete.

Verification:
- Documentation-only changelog follow-up; no code/test verification required.

2026-06-04 latest docs review: No new actionable documentation issues. Reviewed `README.md`, `CHANGELOG.md`, absence of `doc/`, task artifacts, `build.clj`, and focused POM metadata tests. `CHANGELOG.md` Unreleased now documents the user-visible public Maven POM metadata for both core and optional Kaocha artifacts; README installation/license guidance remains accurate and no examples or additional user-facing docs need updates. No new `steps.md` items were added.

2026-06-04 code-shaper review: No new actionable code-quality issues. Reviewed `build.clj` shared metadata helpers, core `tools.build/write-pom` metadata, manual adapter POM XML generation, focused POM metadata tests, generated filesystem POMs, and CHANGELOG entry. The code keeps common metadata centralized, artifact-specific fields explicit, adapter XML generation locally comprehensible, and dependency/packaging boundaries orthogonal. Focused build checks pass (6 tests, 176 assertions).
