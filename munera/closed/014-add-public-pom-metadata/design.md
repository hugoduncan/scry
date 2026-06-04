# Add public POM metadata

## Goal

Ensure generated Clojars artifacts carry public project metadata in their POMs for both the core and optional Kaocha adapter jars.

## Context

The build currently creates `org.hugoduncan/scry` and `org.hugoduncan/scry-kaocha` artifacts. The core POM is generated with `tools.build/write-pom`; the adapter POM is generated manually. Public Clojars artifacts should include metadata such as description, URL, license, SCM, and maintainer/developer information as appropriate.

## Scope

- Add public metadata to the generated core POM.
- Add equivalent public metadata to the manually generated Kaocha adapter POM.
- Include license metadata matching the chosen `LICENSE` task result.
- Add or update focused build tests to verify metadata appears in both POMs.
- Preserve existing artifact boundaries: core jar remains free of Kaocha code/dependencies, adapter depends on same-version core and Kaocha.

## Pinned metadata values

Emit the following public metadata in the generated POM for `org.hugoduncan/scry`:

- `<name>`: `scry`
- `<description>`: `An in-process Clojure test runner for AI agents and REPL-driven development.`
- `<url>`: `https://github.com/hugoduncan/scry`
- `<scm>`:
  - `<url>`: `https://github.com/hugoduncan/scry`
  - `<connection>`: `scm:git:https://github.com/hugoduncan/scry.git`
  - `<developerConnection>`: `scm:git:ssh://git@github.com/hugoduncan/scry.git`
  - `<tag>` policy: emit `v${version}` for the generated artifact version, e.g. version `0.1.42` emits `v0.1.42`. Published releases are built from matching tags; local/dry-run builds use the same deterministic would-be release tag value.

Emit the following public metadata in the generated POM for `org.hugoduncan/scry-kaocha`:

- `<name>`: `scry-kaocha`
- `<description>`: `Optional Kaocha adapter for scry's structured in-process test results.`
- `<url>`: `https://github.com/hugoduncan/scry`
- `<scm>`: the same `<url>`, `<connection>`, `<developerConnection>`, and `v${version}` tag policy as the core artifact.

Emit the same maintainer/developer metadata in both POMs:

- One `<developer>` entry:
  - `<id>`: `hugoduncan`
  - `<name>`: `Hugo Duncan`
  - `<email>`: `hugo@hugoduncan.org`
  - `<url>`: `https://github.com/hugoduncan`
  - `<roles>`: `maintainer`, `developer`
- Omit developer organization, organization URL, timezone, and properties unless a later task explicitly adds them.

Emit the same Maven license metadata in both POMs, carrying forward task `011-add-project-license`'s EPL-2.0 handoff:

- `<licenses><license>`:
  - `<name>`: `Eclipse Public License 2.0`
  - `<url>`: `https://www.eclipse.org/legal/epl-2.0/`
  - `<distribution>`: `repo`
  - `<comments>`: `SPDX-License-Identifier: EPL-2.0`
- The SPDX id appears in `<comments>`, not in `<name>`, because Maven POMs do not have a dedicated SPDX field.

## Metadata verification expectations

Focused build tests must inspect both the filesystem POMs and the jar-embedded POMs for each artifact:

- Core filesystem POM: `target/classes/META-INF/maven/org.hugoduncan/scry/pom.xml`
- Core jar-embedded POM: `META-INF/maven/org.hugoduncan/scry/pom.xml` inside `target/scry-${version}.jar`
- Adapter filesystem POM: `target/classes-kaocha/META-INF/maven/org.hugoduncan/scry-kaocha/pom.xml`
- Adapter jar-embedded POM: `META-INF/maven/org.hugoduncan/scry-kaocha/pom.xml` inside `target/scry-kaocha-${version}.jar`

Tests should verify the pinned metadata values in both locations and should also preserve the existing dependency/boundary assertions for the core and adapter artifacts.

## Out of scope

- Changing coordinates or version scheme.
- Publishing to Clojars.
- Choosing the project license if not already done; depend on the license task decision.

## Acceptance criteria

- Generated core POM contains correct name/description/url/license/scm metadata.
- Generated adapter POM contains corresponding metadata and keeps its existing dependencies correct.
- Focused build tests cover the metadata for both artifacts.
- `clojure -M:test:build ...` focused build checks pass.
- `clojure -T:build jars` succeeds and produces both jars.
