# Add public installation docs

## Goal

Make `README.md` show exactly how public users add `scry` and optional Kaocha support to a Clojure project.

## Context

The README explains REPL, CLI, result-shape, and Kaocha behavior, but it does not currently include an installation section with dependency coordinates. Public users need copyable dependency snippets for both artifacts.

## Scope

- Add a public `Installation` section to `README.md`.
- Document the core artifact coordinate `org.hugoduncan/scry`.
- Document the optional adapter artifact coordinate `org.hugoduncan/scry-kaocha`.
- Explain that the adapter should use the same version as core and keeps Kaocha optional for core users.
- Use the exact version token `RELEASE` in copyable dependency snippets, with surrounding text explaining that published concrete versions are generated as `0.1.<git-count>` and should be pinned instead of `RELEASE` when users want reproducible dependency resolution.

## Installation documentation decisions

- README dependency snippets should put `scry` under a test/development alias rather than top-level `:deps`, because `scry` is a test runner and the existing CLI examples already assume `clojure -M:test` / `clojure -X:test` usage. Use `:aliases {:test {:extra-paths ["test"] :extra-deps {org.hugoduncan/scry {:mvn/version "RELEASE"}}}}` for the core-only snippet, with surrounding text telling projects to adjust or merge project-specific test classpath setup as needed.
- Command examples in the installation section must use the same alias shape as the snippets. Core examples should use `clojure -M:test ...` or `clojure -X:test ...`; optional Kaocha examples should compose the aliases as `clojure -M:test:kaocha ...` or `clojure -X:test:kaocha ...`.
- The version text in copyable snippets is exactly `"RELEASE"`. Add explanatory text that the release workflow publishes concrete Git-count versions such as `0.1.N`, so users who need repeatable builds should replace `RELEASE` with the latest published `0.1.N` from Clojars. Do not document a made-up numeric version such as `0.1.0` or the current local commit count.
- The optional Kaocha snippet should be composable with the core `:test` alias and should list `org.hugoduncan/scry-kaocha` explicitly at the same version token/value as core, for example under a `:kaocha` alias. The adapter artifact's POM depends on same-version `org.hugoduncan/scry` and on `lambdaisland/kaocha`, so the README should state that users do not need to declare Kaocha separately just to use `scry.kaocha`; projects that already manage Kaocha may still override/add their own Kaocha dependency deliberately through normal `deps.edn` resolution.

## Out of scope

- Changing artifact coordinates.
- Publishing the artifacts.
- Adding new runtime features.
- Changing the release workflow or generated `0.1.<git-count>` versioning scheme.

## Acceptance criteria

- README includes copyable `deps.edn` examples for core and optional Kaocha usage.
- The docs clearly distinguish core-only use from optional Kaocha adapter use.
- The README examples use test/development aliases and keep command examples aligned with those aliases.
- The version token is exactly `RELEASE` in snippets, with a note explaining concrete published versions use Git-count `0.1.<git-count>` / `0.1.N` values and should be pinned for reproducible builds.
- Optional Kaocha docs show the adapter coordinate explicitly at the same version token/value as core and explain that the adapter brings `scry` core and Kaocha transitively, so a separate Kaocha dependency is not required unless the user wants to manage/override Kaocha themselves.
- Existing usage examples remain accurate.
