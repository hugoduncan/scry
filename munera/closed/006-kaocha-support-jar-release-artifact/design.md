# Kaocha support jar and release artifact

## Goal

Add a separate publishable jar for the optional `scry.kaocha` adapter and include that jar in the release build, Clojars deploy, and GitHub Release artifacts.

The core `org.hugoduncan/scry` jar should remain core-only. Kaocha support should become available through a second artifact, preferred coordinate `org.hugoduncan/scry-kaocha`, built at the same version as the core artifact.

## Context

Current packaging state:

- `build.clj` builds only `target/scry-VERSION.jar` for coordinate `org.hugoduncan/scry`.
- Core artifact versioning is `0.1.<git rev-list --count HEAD>`.
- The core jar intentionally copies only `src` and excludes `src-kaocha`.
- `deps.edn` keeps Kaocha adapter code and dependency behind `:kaocha`:

  ```clojure
  :kaocha {:extra-paths ["src-kaocha"]
           :extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}}
  ```

- Focused build tests currently assert the core jar excludes `scry/kaocha.clj` and the core pom excludes `lambdaisland/kaocha`.
- The release workflow currently builds and attaches only the core jar.
- Task `005-release-workflow-and-bb-task` added release automation; this task should build on that release surface after it is stable.

## Intended artifact model

Add an optional adapter artifact, preferably:

```clojure
org.hugoduncan/scry-kaocha {:mvn/version "0.1.N"}
```

The adapter artifact should:

- Package `src-kaocha`, including `scry/kaocha.clj`.
- Not duplicate the core `src` classes inside the adapter jar unless implementation proves duplication is necessary and documents why.
- Declare a dependency on `org.hugoduncan/scry` at the exact same version.
- Declare a dependency on `lambdaisland/kaocha`.
- Keep the core artifact free of `src-kaocha` and free of a hard Kaocha dependency.

The release should publish both artifacts for the same version:

- `target/scry-VERSION.jar` for `org.hugoduncan/scry`.
- `target/scry-kaocha-VERSION.jar` for `org.hugoduncan/scry-kaocha`.

## Scope

In scope:

- Extend `build.clj` with build support for a Kaocha adapter jar.
- Add or update build task entry points, for example:
  - keep `clojure -T:build jar` as the core jar build,
  - add `clojure -T:build kaocha-jar`, and
  - add `clojure -T:build jars` or equivalent to build both release artifacts.
- Ensure the adapter jar pom/dependency metadata is correct for Clojars consumers.
- Extend deploy support to deploy both the core and Kaocha adapter artifacts, or add a clearly named deploy-all task while preserving any existing core-only deploy command semantics if useful.
- Extend `.github/workflows/release.yml` so dry-run and tag-push release paths build and verify both jars.
- Extend release publishing so both artifacts are deployed to Clojars on publishing tag pushes.
- Attach both jars to the GitHub Release.
- Update focused build/release tests for the new artifact behavior.
- Update maintainer docs and changelog.
- Update public docs if artifact coordinates or installation guidance become user-facing.

Out of scope:

- Merging `src-kaocha` into the core artifact.
- Making Kaocha a hard dependency of `org.hugoduncan/scry`.
- Creating an uberjar or standalone launcher.
- Changing `scry.kaocha` runtime behavior except where needed to make packaging tests pass.
- Changing the versioning scheme away from `0.1.<git commit count>`.
- Reworking the release workflow beyond the minimum needed to build, verify, deploy, and attach both jars.

## Design considerations

- Preserve the existing dependency boundary: core users should not download Kaocha unless they choose the adapter artifact or the existing development alias.
- Prefer a separate adapter coordinate over classifier-based publication because separate coordinates are simpler for Clojars consumers and Clojure deps users.
- The adapter pom needs special care. A naive `tools.build/write-pom` using the project basis may treat core `src` as a local path rather than an external dependency. The implementation should deliberately verify the generated adapter pom includes `org.hugoduncan/scry` at the same version and `lambdaisland/kaocha`.
- Build commands should remain clear for maintainers. Existing documented `clojure -T:build jar` behavior should continue to mean the core jar unless documentation is intentionally updated.
- Keep the existing deploy command semantics clear: `clojure -T:build:deploy deploy` remains the core-only deploy task for `org.hugoduncan/scry`; this task should add a clearly named combined publish task, preferably `clojure -T:build:deploy deploy-all`, that builds/deploys both artifacts for release use. The release workflow should use the combined task, while the core-only task remains available for focused maintainer use and backward-compatible documentation.
- Pin the adapter pom's Kaocha dependency version to the same source used by the development `:kaocha` alias. Implementation should centralize that version in build code (or otherwise read it deterministically from `deps.edn`) so the adapter pom and alias cannot drift silently; focused tests should assert the generated adapter pom contains `lambdaisland/kaocha` at the pinned version currently used by `:kaocha`.
- Use isolated build output locations for separate artifacts. The core build should continue using its existing core class/pom output, and the adapter build should use a distinct class directory such as `target/classes-kaocha` so `tools.build/write-pom` output for one artifact cannot clobber the other. Combined build/deploy paths must retain both jar paths and both pom paths explicitly in their return values and deploy argument order.
- Release version validation should verify both jar filenames resolve to the same version as the tag/dry-run expected version.
- Publishing should deploy the core artifact before the adapter artifact because the adapter depends on the same-version core artifact.
- Dry-run release paths must remain non-publishing but should build both jars and verify both versions.
- Tests should continue to assert negative boundaries: the core jar/pom excludes Kaocha, while the adapter jar/pom includes the adapter and its dependencies.

## Acceptance criteria

- `build.clj` can build a Kaocha adapter jar, named like `target/scry-kaocha-0.1.N.jar`.
- The adapter jar contains `scry/kaocha.clj`.
- The adapter jar does not duplicate core classes unless a documented implementation decision says otherwise.
- The adapter pom uses coordinate `org.hugoduncan/scry-kaocha` or another documented adapter coordinate chosen during planning.
- The adapter pom depends on `org.hugoduncan/scry` at the exact same version.
- The adapter pom depends on `lambdaisland/kaocha`.
- The core jar still excludes `scry/kaocha.clj`.
- The core pom still excludes `lambdaisland/kaocha`.
- A build command exists to build both release jars in one invocation.
- Focused build checks cover both core and adapter artifact shapes and pom dependencies.
- Deploy support can deploy both artifacts with the documented Clojars credential contract.
- Release workflow dry-runs build both jars and verify both artifact versions.
- Release workflow tag-push publishing deploys both artifacts to Clojars, with the core artifact deployed before the adapter artifact.
- GitHub Release creation attaches both jars.
- Release workflow publishing gates still prevent deploy/release creation on `workflow_dispatch` dry runs.
- Focused release workflow/static checks are updated for both release artifacts.
- Existing core tests, focused Kaocha adapter tests, focused build checks, focused release checks, `actionlint`, and jar build verification pass.
- `AGENTS.md` and `CHANGELOG.md` document the new adapter artifact/release behavior.
- README or other user-facing docs are updated if the new adapter coordinate is presented as installable public API.
