# Testing-without-mocks REPL test strategy

## Goal

Apply the Testing Without Mocks pattern language to `scry`'s test strategy so maintainers and agents have deterministic, REPL-friendly, narrow verification paths that use real collaborators without relying on broad discovery, mocks, or process-global accidents.

The result should make it clear how to run meaningful all-green checks from a REPL, how to keep intentional failure fixtures from becoming accidental suite failures, and how to isolate optional/classpath-sensitive test slices such as Kaocha and build/release support.

## Context

`scry` is intentionally REPL-first, but recent REPL verification exposed several test-strategy problems:

- A broad `(scry/run)` in a long-lived REPL is not a reliable all-green check. It can mix product tests, intentional failing/erroring fixtures, optional adapter tests, generated temporary-project namespaces, and process-global state left by previous test runs.
- Optional Kaocha behavior is classpath-sensitive. Core CLI tests include behavior for “Kaocha unavailable,” while Kaocha CLI tests require the optional adapter on the classpath. Those cannot be treated as one uniform suite in the same runtime context.
- Some tests create temporary projects with namespace names such as `demo.integration-test` or `explicit.integration-test`. In a long-lived REPL, loaded namespaces can outlive their temp directories and interfere with later tests unless names are unique or explicitly cleaned up.
- The CLI task has already moved at least one runner-exception test away from an injected throwing runner toward a real runner path. This is aligned with Testing Without Mocks and should be made systematic.
- The current AGENTS guidance still emphasizes REPL-driven testing, but the canonical REPL verification slices and their boundaries are not yet precise enough to avoid broad-test coupling.

Testing Without Mocks suggests this project should prefer:

- narrow tests focused on one behavior or slice,
- state-based assertions on result maps, files, stdout/stderr writers, and computed plans,
- real collaborators such as `clojure.test`, real temp directories, real Kaocha config, and real build helpers where practical,
- thin/nullable infrastructure boundaries only for unavoidable external effects such as process execution, GitHub/Clojars, or `System/exit`,
- a small number of smoke checks as safety nets, not as the primary strategy.

## Problem

The repository has a mismatch between its REPL-first promise and its test organization/documentation:

- The phrase “run tests through the REPL” can be interpreted as “run broad discovery with `(scry/run)`,” but that broad run is sensitive to fixture names, loaded namespaces, optional aliases, and previous REPL activity.
- Long-lived REPLs need repeatable focused checks. A test that passes only in a fresh process, or only before another test namespace has run, is brittle for the way `scry` is intended to be developed.
- Classpath-sensitive tests need explicit slice boundaries so a core-only unavailable-adapter test is not expected to pass in a Kaocha-enabled REPL.
- Any remaining mock-like injected behavior should either be replaced with a real state-based path or documented as a narrow Nullable infrastructure boundary.

## Scope

In scope:

- Audit the existing test namespaces and verification docs through the Testing Without Mocks lens.
- Define canonical REPL verification slices for this repository, including at least:
  - core tests that can run in a core-only `:test` REPL,
  - optional Kaocha adapter tests in a `:test:kaocha` REPL,
  - optional Kaocha CLI tests in a `:test:kaocha` REPL,
  - focused build checks in a `:test:build` REPL,
  - focused release helper checks in an appropriate test/release classpath.
- Decide whether the canonical slices should be expressed as documentation only, a helper namespace, Babashka tasks, or another small maintainable mechanism.
- Make focused REPL checks deterministic when run repeatedly in the same REPL session where practical.
- Fix or isolate tests that leak temporary namespaces, `user.dir`, result directories, or other process-global state across REPL runs.
- Replace remaining mock-like runner/process tests with real state-based coverage when practical.
- Where external effects are unavoidable, shape them as thin Nullable infrastructure boundaries with state-based assertions on outputs, command plans, or recorded effects.
- Update `AGENTS.md` and task/repo documentation so future agents know which REPL checks to run and why broad discovery is not the primary strategy.
- Update CI core-test verification so the primary core signal uses the documented explicit focused core slice rather than broad `(scry/run)` discovery. A separate broad discovery command may remain only as a fresh-process smoke check if it is clearly named and documented as such.

Out of scope:

- Changing public `scry.core/run` result shapes or CLI result shapes.
- Removing intentional failing/erroring fixture namespaces that are needed to test failure capture.
- Making every possible alias combination pass as one monolithic suite in a single REPL.
- Replacing all integration/smoke checks with pure unit tests.
- Adding heavyweight test frameworks, mocking libraries, or process orchestration solely for this task.
- Redesigning build/release automation beyond the test-strategy boundaries needed for deterministic verification.

## Design direction

Use Testing Without Mocks as the organizing model:

1. **Narrow tests as the default.** Prefer explicit namespace/var selections for focused behavior checks. Broad discovery may remain a smoke check only if it is deterministic and documented as such.
2. **Sociable, real collaborators.** Keep using real `clojure.test`, real temp files/directories, real `scry` capture, real Kaocha config, and real build helper functions rather than replacing them with mocks.
3. **State-based assertions.** Assert on returned result maps, summaries, `.scry-results/` files, stdout/stderr text, changelog text, generated command plans, or other visible state. Avoid asserting that an internal function was called.
4. **Nullable infrastructure boundaries.** For external systems such as Git, `gh`, Clojars deploy, or process exit, keep boundaries thin and test them through configurable responses and output tracking rather than mock interaction verification.
5. **Classpath slices are explicit.** Core-only, Kaocha-enabled, build, and release-helper checks should be treated as different runtime contexts with documented expectations.
6. **Long-lived REPL safety.** Tests that create temporary namespaces/projects should either use unique namespace names per test run, unload/cleanup namespaces, or otherwise avoid depending on a fresh process.


## Resolved design decisions

### Canonical REPL slice mechanism

Use documented REPL snippets as the canonical mechanism for this task, recorded in `AGENTS.md` during implementation and mirrored in this design. Do not add a helper namespace or Babashka task solely to dispatch test slices yet. The snippets are the smallest maintainable fit because each slice is already one explicit `scry/run` call, avoids another API surface to keep in sync, and keeps the strategy visible to future agents at the point where they choose verification commands.

If the snippets become repetitive after implementation, a later task may add a helper namespace, but that is out of scope for this design.

### Exact REPL classpath contexts and forms

Start a REPL with the listed alias context, then evaluate the form. The `:namespaces` vectors are intentionally explicit; broad discovery is not the primary all-green mechanism.

Core-only test slice (`clojure -M:test:nrepl` or an equivalent `:test` REPL):

```clojure
(require '[scry.core :as scry])

(scry/run {:namespaces ['scry.capture-test
                        'scry.clojure-test-test
                        'scry.cli-test]})
(println (scry/report-string (scry/last-result)))
(:summary (scry/last-result))
```

Optional Kaocha adapter and Kaocha CLI slice (`clojure -M:test:kaocha:nrepl` or an equivalent `:test:kaocha` REPL):

```clojure
(require '[clojure.test :as ct]
         '[scry.kaocha-test]
         '[scry.cli-kaocha-test])

(let [adapter-result (ct/run-tests 'scry.kaocha-test)
      cli-result (ct/run-tests 'scry.cli-kaocha-test)]
  (and (ct/successful? adapter-result)
       (ct/successful? cli-result)))
```

Use `clojure.test/run-tests` for this slice because these tests intentionally run failing inner Kaocha projects; an outer `scry/run` would capture those intentional nested reports as failures of the verification run.

Focused build slice (`clojure -M:test:build:nrepl` or an equivalent `:test:build` REPL):

```clojure
(require '[scry.core :as scry])

(scry/run {:namespaces ['scry.build-test]})
(println (scry/report-string (scry/last-result)))
(:summary (scry/last-result))
```

Focused release-helper slice (`clojure -M:test:release-test:nrepl` or an equivalent `:test:release-test` REPL):

```clojure
(require '[scry.core :as scry])

(scry/run {:namespaces ['scry.release-test]})
(println (scry/report-string (scry/last-result)))
(:summary (scry/last-result))
```

The release-helper slice deliberately combines the base `:test` alias, which supplies `test/`, with `:release-test`, which documents the extra `bb/` release-helper classpath.

### CI and broad discovery

CI should use the same explicit focused slices rather than relying on broad `(scry/run)` discovery as the core all-green check. A remaining broad/discovery run is allowed only as a fresh-process smoke check with a clearly documented role: it may catch namespace-discovery regressions, but it must not be the only or primary correctness signal and must not be used to mix classpath-sensitive slices.

For this task, implementation must update `.github/workflows/ci.yml` so the current broad core `(scry/run)` step is not the only or primary core test signal. The required CI shape is:

1. a primary focused core step equivalent to the documented core REPL slice, selecting `scry.capture-test`, `scry.clojure-test-test`, and `scry.cli-test` explicitly and failing non-zero on `:pass? false`; and
2. optionally, a separately named broad discovery smoke step that runs in a fresh process and is documented/named as smoke coverage only.

Keeping the existing step named `Run core tests` with bare `(scry/run)` is not acceptable for implementation because it contradicts the focused-slice strategy and makes broad discovery appear to be the authoritative all-green check.

### Temporary Kaocha namespace cleanup and isolation

Temporary-project tests that cause Kaocha or `clojure.test` to load generated namespaces must avoid shared namespace names in long-lived REPLs. The preferred strategy is both:

1. generate unique namespace prefixes per test/project, so names such as `demo.integration-test`, `explicit.integration-test`, and `fallback.sample-test` are not reused across REPL runs; and
2. call `remove-ns` for every generated namespace in the test fixture `finally` path after restoring `user.dir`.

The implementation should keep using real temporary directories and real Kaocha config files; the unique namespace plus `remove-ns` cleanup is for process-global namespace hygiene, not for mocking. If a Kaocha behavior cannot be made safe in the same runtime, document that specific case as requiring a fresh-process/classpath boundary.

### Accepted Nullable infrastructure boundaries

Remaining injected boundaries are acceptable only when they stand in for external infrastructure or process termination and are asserted through visible state/output, not interaction verification:

- `scry.cli` writers and `:cwd` are accepted output/environment boundaries. Tests should assert stdout/stderr strings, result files, exit codes, returned outcomes, and canonical result maps.
- `scry.cli/main-outcome` and `scry.cli/run` are accepted `System/exit` boundaries. Tests should assert exit codes or structured `ex-info` data; only `-main` itself should call `System/exit`.
- `scry.cli`'s `:run-clojure-test` boundary may remain as a thin application seam, but focused tests should prefer the real in-process runner. If this seam is used, it must return public runner-shaped data and tests must assert CLI-visible state, not that the function was called. Existing runner-exception coverage should continue to use a real missing namespace path.
- `build/git-rev-count`'s nullable process function is accepted for Git failure/count parsing because Git availability and repository metadata are external infrastructure. Tests should assert clear exception messages and ex-data.
- `build/deploy`/`deploy-all`'s `:deploy-fn` is accepted for the Clojars/deps-deploy boundary. Tests should assert artifact state, pom/jar paths, and recorded deploy argument maps.
- `scry.release`'s command function is accepted for Git and `gh` command execution. Tests should assert computed release versions, changelog text, command plans/recorded commands, and ex-data.

Mock-like seams to remove or avoid include injected logical collaborators where a real `scry` runner, real `clojure.test` var, real temp directory, real result file, or real normalized Kaocha config can provide the same coverage.

## Acceptance criteria

- The repository has a documented canonical REPL verification strategy aligned with Testing Without Mocks.
- Running the documented focused core REPL check in a core-only `:test` REPL passes and does not require broad discovery of all namespaces.
- Running the documented focused Kaocha adapter and Kaocha CLI REPL checks in a `:test:kaocha` REPL passes, including when those checks are run repeatedly or in documented order within the same REPL where practical.
- Running the documented focused build and release helper checks through REPL evaluation passes in their required classpath contexts.
- Tests that create temporary project namespaces no longer interfere with each other in a long-lived REPL, or the remaining limitation is explicitly documented with a justified process/classpath boundary.
- Any remaining injected runner/process boundary in tests is either removed in favor of a real state-based path or documented as a Nullable infrastructure boundary with output/state assertions rather than interaction assertions.
- `AGENTS.md` documents the focused REPL slices as the canonical development checks for core, Kaocha, build, and release-helper verification. Any broad `(scry/run)` example is either removed from the primary workflow or clearly qualified as smoke/discovery-only, not the normal all-green REPL check.
- If CI or release workflow commands are adjusted, they still protect the same behavior and artifact boundaries as before.

## Planning questions status

The initial planning questions are resolved by the decisions above: canonical slices are documented snippets; CI must use an explicit focused core slice as the primary core signal with any broad discovery run treated only as a separately named smoke check; AGENTS.md must present focused REPL slices as canonical and qualify/remove broad `(scry/run)` examples; temporary Kaocha/project namespaces should use unique names plus `remove-ns`; and the accepted Nullable infrastructure boundaries are limited to output/environment, process exit, Git/`gh`, Clojars/deps-deploy, and similar external effects.
