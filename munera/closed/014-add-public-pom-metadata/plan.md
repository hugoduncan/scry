# Plan

## Approach

Add public POM metadata at the build layer, where both release artifacts already generate their deployable POM files.

Keep the existing artifact coordinates, version scheme, jar names, and source boundaries unchanged:

- Core artifact: `org.hugoduncan/scry`, built from `src`, no Kaocha source or dependency.
- Adapter artifact: `org.hugoduncan/scry-kaocha`, built from `src-kaocha`, depending on same-version core plus the Kaocha version read from the `:kaocha` alias.

Use shared metadata helpers/constants in `build.clj` so the core and adapter POMs cannot drift on common fields:

- Project URL and SCM connection values.
- EPL-2.0 Maven license metadata from task `011-add-project-license`.
- Shared Hugo Duncan maintainer/developer entry.
- SCM tag derived deterministically as `v${version}`.

Use artifact-specific metadata only for the pinned `<name>` and `<description>` values. Extend the core `tools.build/write-pom` invocation with supported metadata/pom data, and extend the manually generated adapter POM XML with the same metadata structure while preserving its existing dependency XML.

Extend focused build tests to verify the pinned metadata in both generated filesystem POMs and jar-embedded POMs for both artifacts. Keep the existing dependency and packaging-boundary assertions in place.

## Risks

- `tools.build/write-pom` metadata support may require using its `:pom-data` extension shape rather than simple scalar options; implementation should choose the smallest supported form that produces the pinned XML.
- Manual adapter XML must preserve valid Maven POM ordering/escaping while adding nested license, SCM, and developer elements.
- Tests should avoid weak string-only coverage where practical; if string assertions are used, assert complete element snippets or parsed fields for both filesystem and jar-embedded POMs.
- Build tests invoke tasks that clean or replace `target/`; filesystem POM checks must read the POMs produced by the same build invocation being asserted.

## Slice order

1. **Metadata model** — inspect the current POM generation paths and add shared/artifact-specific metadata helpers in `build.clj`.
2. **Core POM metadata** — add the pinned metadata to the core `org.hugoduncan/scry` POM while preserving core-only packaging and dependency boundaries.
3. **Adapter POM metadata** — add corresponding metadata to the manual `org.hugoduncan/scry-kaocha` POM while preserving same-version core and Kaocha dependencies.
4. **Focused build tests** — extend `test/scry/build_test.clj` to verify filesystem and jar-embedded POM metadata for both artifacts plus existing boundary/dependency expectations.
5. **Verification and notes** — run the focused build checks and `clojure -T:build jars`, inspect the diff, and record verification in `implementation.md`.
