# tools.build jar build

## Goal

Add a `tools.build`-based jar build for `scry`.

The Maven coordinate should use group `org.hugoduncan` and artifact `scry`:

```clojure
org.hugoduncan/scry
```

Versioning should be generated as:

```text
<major>.<minor>.<git-revcount>
```

Initial `major.minor` is `0.1`, so a repository with 123 commits should build version `0.1.123`.

## Context

The project currently has a Clojure `deps.edn` setup with:

- `src` for the core library.
- `src-kaocha` for the optional Kaocha adapter.
- `test` for tests.
- aliases for `:test`, `:kaocha`, and `:nrepl`.

There is no build script yet for producing a jar or generated pom. A jar build should make the project consumable by downstream Clojure projects without relying on a checkout.

## Desired user/developer workflow

A developer should be able to run a REPL-friendly or shell-friendly build invocation such as:

```sh
clojure -T:build jar
```

The exact task names may be chosen during implementation, but should be simple and conventional for `tools.build` projects. A `clean` task is expected, and an `install` task is useful if low-cost.

## Scope

In scope:

- Add `io.github.clojure/tools.build` as a build alias in `deps.edn`.
- Add a `build.clj` script.
- Generate a pom with coordinate `org.hugoduncan/scry`.
- Generate a jar under a conventional target directory.
- Compute the patch component from `git rev-list --count HEAD`.
- Start the fixed major/minor version at `0.1`.
- Ensure build output excludes test sources, Munera/Mementum state, `.psi`, `.cpcache`, and other repository-only files.
- Document build commands in `AGENTS.md` rather than `README.md`, unless a user-facing artifact-consumption note is added to README.
- Add or update tests/checks where practical to verify version computation and build behavior.

Out of scope:

- Publishing to Clojars or any remote repository.
- CI configuration.
- Signing artifacts.
- Changing public API or result shapes.
- Changing `scry.core` to depend on Kaocha.

## Design considerations

### What goes in the jar

The implementation must decide and document whether the jar contains:

1. only core `src`, or
2. both `src` and optional `src-kaocha`.

Current architecture keeps Kaocha support isolated under `src-kaocha` and available only when the Kaocha alias/dependency is present. A build should preserve that boundary. If the jar includes `src-kaocha`, the generated pom should not accidentally make Kaocha a hard runtime dependency unless that is an explicit decision. If supporting optional Maven dependencies is awkward, prefer a conservative core-only jar and document how the optional adapter is handled or deferred.

### Version computation

Use Git as the source of the patch version:

```sh
git rev-list --count HEAD
```

The implementation should fail clearly if Git metadata is unavailable, or provide a deliberately documented fallback only if needed for reproducible local builds. Avoid silently producing ambiguous versions.

### Build script shape

A typical implementation may include:

- constants for `lib`, `class-dir`, `basis`, `version`, `jar-file`.
- `(clean [_])` to remove `target`.
- `(jar [_])` to clean/copy sources/write pom/build jar.
- optionally `(install [_])` to install locally.

Prefer simple functions over framework-like abstractions.

## Acceptance criteria

- `deps.edn` has a `:build` alias using `io.github.clojure/tools.build`.
- `build.clj` exists and can build a jar with `clojure -T:build jar` or an equally documented `tools.build` invocation.
- The generated coordinate is `org.hugoduncan/scry`.
- The generated version is `0.1.<git-revcount>` using the current repository commit count.
- The jar is written under `target/` with a name that includes artifact id and version.
- The generated pom contains the expected group/artifact/version.
- Build output does not include tests or repository/task/memory files.
- The build preserves the current core/Kaocha dependency boundary by either excluding optional Kaocha sources or documenting/testing a safe optional-adapter packaging approach.
- Existing tests still pass after the build changes.
- Documentation for maintainers/agents describes how to build the jar and where the version comes from.
