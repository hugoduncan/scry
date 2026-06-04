# Plan

## Approach

Implement the optional Kaocha adapter as a second, same-version release artifact while preserving the existing core-only build and deploy semantics.

Key decisions:

- Use adapter coordinate `org.hugoduncan/scry-kaocha` and jar name `target/scry-kaocha-VERSION.jar`.
- Keep `clojure -T:build jar` and `clojure -T:build:deploy deploy` core-only for backward-compatible maintainer use.
- Add `clojure -T:build kaocha-jar` for the adapter artifact and `clojure -T:build jars` for a combined release build.
- Add `clojure -T:build:deploy deploy-all` for release publishing of both artifacts; deploy core first, then adapter.
- Keep the core class/pom output isolated from the adapter class/pom output, for example `target/classes` and `target/classes-kaocha`, and return both jar and pom paths explicitly from combined tasks.
- Generate the adapter pom deliberately so it depends on `org.hugoduncan/scry` at the exact same version and on `lambdaisland/kaocha` at the version read deterministically from the development `:kaocha` alias in `deps.edn`; do not duplicate the Kaocha version as an independent build constant. Focused build tests should compare the generated adapter pom dependency against the `deps.edn` alias value so alias/pom drift fails visibly.
- Make standalone `clojure -T:build kaocha-jar` delete only adapter-specific outputs before building (`target/classes-kaocha`, the adapter pom path, and stale `target/scry-kaocha-*.jar`) and preserve any existing core jar output. The combined `clojure -T:build jars` command remains the release command that cleans all `target/` output once before producing both artifacts.
- Update the release workflow to build/verify both jars on dry-run and tag-push paths, deploy both artifacts only for publishing tag pushes, and attach both jars to the GitHub Release.
- Update focused build tests for artifact contents, pom metadata, output isolation, deploy ordering, and negative dependency-boundary assertions.
- Update focused release/static tests for two-artifact workflow behavior where practical, plus `actionlint` validation.
- Update maintainer documentation and changelog; update README if the adapter coordinate is presented as user-facing installation guidance.

## Risks

- `tools.build/write-pom` can accidentally derive local path metadata from the project basis; adapter pom generation must be tested to ensure it contains external Maven dependencies for same-version core and Kaocha.
- Combined builds can clobber pom/class output if core and adapter outputs share a directory; outputs must stay isolated and tests should verify both pom paths are retained.
- Release workflow shell globs may accidentally match only one jar or derive the wrong version; workflow logic should resolve core and adapter jar filenames separately and compare both versions. Use exact non-overlapping patterns: core lookup should match `target/scry-[0-9]*.[0-9]*.[0-9]*.jar` and explicitly exclude any `target/scry-kaocha-*.jar`; adapter lookup should match only `target/scry-kaocha-[0-9]*.[0-9]*.[0-9]*.jar`. Each lookup should fail clearly unless it resolves exactly one jar.
- Deploy ordering matters because the adapter artifact depends on the same-version core artifact; tests should verify core deploy happens before adapter deploy.
- Keeping the Kaocha dependency version synchronized with `deps.edn` can drift if build code hard-codes it without tests.
- Task 005 release automation is the base surface for this task; if task 005 changes again before implementation, this plan may need a small refresh.

## Slice order

1. **Build artifact model** — extend `build.clj` constants/helpers for core and adapter coordinates, versioned jar paths, isolated class directories, adapter dependency metadata, `kaocha-jar`, and `jars` while preserving existing `jar` behavior.
2. **Focused build coverage** — update `test/scry/build_test.clj` to cover core exclusions, adapter jar contents, adapter pom dependencies, output isolation, combined build return shape, credential checks, and deploy/deploy-all behavior including deploy order.
3. **Deploy-all support** — add `deploy-all` to build/deploy support so release publishing can deploy the core artifact first and the adapter artifact second under the documented Clojars credential contract.
4. **Release workflow integration** — update `.github/workflows/release.yml` to run the combined build, resolve the core jar with a non-overlapping `target/scry-[0-9]*.[0-9]*.[0-9]*.jar` lookup that excludes `scry-kaocha`, resolve the adapter jar with `target/scry-kaocha-[0-9]*.[0-9]*.[0-9]*.jar`, fail unless each lookup finds exactly one jar, verify both artifact versions, deploy all on publishing tags only, and attach both jars to GitHub Releases.
5. **Release/static checks** — update focused release workflow/static checks as needed for the new commands/artifacts and run `actionlint`.
6. **Documentation and changelog** — update `AGENTS.md`, `CHANGELOG.md`, and README if public install guidance is added.
7. **Verification** — run existing core tests, focused Kaocha adapter tests, focused build checks, focused release checks, actionlint, and combined jar build verification.
