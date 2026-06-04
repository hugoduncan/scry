# Steps

## Slice 1: Metadata model

- [x] Inspect `build.clj` core `b/write-pom` generation and manual adapter `pom-xml` generation to identify the minimal metadata insertion points.
- [x] Add shared metadata values/helpers in `build.clj` for project URL, SCM fields, EPL-2.0 license metadata, developer metadata, and `v${version}` SCM tag derivation.
- [x] Add artifact-specific metadata values/helpers in `build.clj` for core name/description and adapter name/description.

## Slice 2: Core POM metadata

- [x] Extend the core `b/write-pom` call so `target/classes/META-INF/maven/org.hugoduncan/scry/pom.xml` contains the pinned core name, description, URL, license, SCM, and developer metadata.
- [x] Confirm the core POM still omits Kaocha source/dependency metadata and keeps coordinate `org.hugoduncan/scry` with version `0.1.<git-revcount>`.

## Slice 3: Adapter POM metadata

- [x] Extend the manual adapter POM XML so `target/classes-kaocha/META-INF/maven/org.hugoduncan/scry-kaocha/pom.xml` contains the pinned adapter name, description, URL, license, SCM, and developer metadata.
- [x] Confirm the adapter POM still depends on same-version `org.hugoduncan/scry` and the `lambdaisland/kaocha` version read from the `:kaocha` alias.

## Slice 4: Focused build tests

- [x] Add focused assertions for pinned core metadata in the core filesystem POM.
- [x] Add focused assertions for pinned core metadata in the core jar-embedded POM.
- [x] Add focused assertions for pinned adapter metadata in the adapter filesystem POM.
- [x] Add focused assertions for pinned adapter metadata in the adapter jar-embedded POM.
- [x] Preserve or strengthen existing focused assertions for dependency metadata and core/adapter packaging boundaries.

## Slice 5: Verification and notes

- [x] Run focused build checks with `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"`.
- [x] Run `clojure -T:build jars` and confirm both core and adapter jars are produced.
- [x] Inspect the final diff for unintended coordinate, version, dependency, or artifact-boundary changes.
- [x] Record implementation decisions and verification results in `implementation.md`.
- [x] Strengthen focused POM metadata tests to assert the artifact-level `<url>` as a direct project child for both core and adapter filesystem and jar-embedded POMs, instead of accepting any matching `<url>` occurrence such as SCM metadata.
- [x] Add focused negative POM metadata assertions that both core and adapter filesystem and jar-embedded POMs omit developer organization, organization URL, timezone, and properties fields as pinned in the design.
- [x] Add focused POM metadata assertions that both core and adapter filesystem and jar-embedded POMs contain exactly one direct `<license>` entry and exactly one direct `<developer>` entry, so extra generated metadata cannot hide behind the pinned first entry.
- [x] Strengthen focused POM metadata exact-count assertions to cover duplicate direct `<licenses>` and `<developers>` containers, so extra license/developer entries cannot pass by only checking the first container.
- [x] Update `CHANGELOG.md` Unreleased to mention the new public POM metadata for both `org.hugoduncan/scry` and `org.hugoduncan/scry-kaocha` artifacts.
