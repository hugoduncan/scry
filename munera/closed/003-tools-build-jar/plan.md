# Plan

## Approach

Implement a small conventional `tools.build` setup for producing a core `scry` jar.

Key decisions:

- Add a `:build` alias to `deps.edn` using `io.github.clojure/tools.build` and `:ns-default build` so maintainers can run the in-scope tasks `clojure -T:build clean` and `clojure -T:build jar`; local Maven `install` is deferred to a later explicit task if needed.
- Add `build.clj` at the repository root with simple task functions and constants for:
  - `lib` = `org.hugoduncan/scry`
  - fixed major/minor = `0.1`
  - patch = `git rev-list --count HEAD`
  - `class-dir` under `target/classes`
  - jar path under `target/` including artifact id and computed version.
- Build a conservative core-only jar from `src` for the first artifact. Do not package `src-kaocha`; this preserves the current dependency boundary and avoids making Kaocha a hard runtime dependency in the generated pom.
- Generate the pom from the project basis and the computed coordinate/version. Verify the pom uses group `org.hugoduncan`, artifact `scry`, and version `0.1.<git-revcount>`.
- Fail clearly when Git metadata is unavailable or `git rev-list --count HEAD` fails; do not silently invent a version.
- Keep build output under `target/` and copy only selected source paths so tests, Munera/Mementum state, `.psi`, `.cpcache`, and repository-only files cannot enter the jar.
- Add focused build checks in `test/scry/build_test.clj`. The test namespace should load `build.clj` directly (for example with `load-file`) and use direct Clojure assertions against build vars/functions, generated pom data, jar path, and jar contents rather than broad shell-output scraping. Run these checks with:

  ```sh
  clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.build-test)"
  ```

- Defer a local Maven `install` task for this task. The acceptance criteria require `clean` and `jar`, not local installation; deferring keeps this slice focused on producing and verifying the jar artifact. A later task can add `install` with explicit local-repository verification if needed.
- Update `AGENTS.md` with maintainer build commands, the version source, output location, the focused build-check command, and the core-only optional Kaocha adapter decision. Leave `README.md` unchanged unless implementation adds a user-facing artifact-consumption note.

## Risks

- The exact repository commit count changes whenever commits are added, so tests must compute the expected version dynamically rather than hard-code a patch number.
- `build.clj` task functions often execute shell/Git and filesystem operations; tests should avoid brittle assumptions and may need to isolate `target/` cleanup/build side effects.
- The generated pom dependency set comes from the default basis. The build must avoid including alias-only Kaocha dependencies and must not package `src-kaocha` unless optional dependency handling is deliberately added later.
- Worktrees use a `.git` file rather than a `.git/` directory; Git commands must be run through `git` instead of inspecting `.git` paths manually.
- Building a jar may create `target/`; ensure it remains ignored/uncommitted.

## Slice order

1. Inspect and shape the build inputs: confirm current `deps.edn`, source paths, ignored build output, and dependency boundary.
2. Add the `tools.build` alias and `build.clj` with clean, version, jar, and optional install tasks.
3. Add focused checks/tests for computed version, generated pom coordinate/version, jar name/location, and jar contents excluding tests/repository state and optional Kaocha sources.
4. Update maintainer/agent documentation in `AGENTS.md` for build commands, versioning, output, and optional adapter packaging.
5. Run verification: existing tests, any new focused build checks, and `clojure -T:build jar`; inspect jar/pom contents as needed.
