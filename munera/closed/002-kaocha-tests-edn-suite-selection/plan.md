# Plan

## Approach

Implement the Kaocha adapter change entirely under `src-kaocha/`, preserving `scry.core`'s no-Kaocha boundary and preserving the full `:config` override path.

Key decisions:

- Split config resolution into explicit paths:
  - supplied `:config`: use as the base config without normalization or fallback option merging;
  - existing `tests.edn`: load with Kaocha's config loader;
  - no `tests.edn`: build the current synthetic `:unit` suite from `:source-paths`, `:test-paths`, and `:ns-patterns`, then normalize it.
- Apply `:suite` / `:suites` selection after the base config is resolved, including for full `:config` input.
- Treat `:suite` plus `:suites` as an API error using `ex-info` with both supplied values.
- Treat invalid `:suites` values as API errors: it must be a non-empty collection of selectors, not a string, map, scalar selector, or empty collection; callers use `:suite` for a single selector.
- Resolve suite selectors against final suite ids with exact equality first, then unique textual fallback matching (`string` as-is, named values by `name`); unknown and ambiguous selectors throw clear `ex-info` exceptions.
- Prefer Kaocha's own suite skipping semantics after selector resolution by passing the resolved suite id values directly to `kaocha.config/apply-cli-args`. Inspection confirmed `apply-cli-args` compares suite ids by value and preserves already resolved exact ids when given those values directly. Do not stringify resolved ids: stringifying namespace-qualified keywords/symbols or other non-string ids loses type/namespace information and can skip the wrong suites or reintroduce ambiguous text matching.
- Merge only scry runtime defaults before `kaocha.api/run`: ensure capture-output plugin is present without duplicating it, force `:kaocha/reporter` to `[]`, and force `:kaocha/color?` to `false`.
- Add Kaocha-specific tests/fixtures that exercise config loading and suite selection without adding Kaocha as a core dependency.
- Update README, AGENTS.md, and SKILL.md with REPL-first Kaocha suite-selection examples.

## Risks

- `kaocha.config/load-config` may have side effects, warnings, or assumptions when invoked programmatically; guard by checking for `tests.edn` before calling it.
- Kaocha's `apply-cli-args` API may expect CLI-shaped string args rather than ids; verify behavior before committing to it.
- Suite ids can be keywords, symbols, strings, or other values; selector resolution must avoid silently selecting the wrong namespace-qualified id when fallback text collides.
- Test fixtures that write or rely on `tests.edn` may need isolated working directories to avoid interacting with the repository's own config.
- Quiet reporter/color defaults intentionally override loaded or supplied config values; tests should lock this down because it is a deliberate adapter policy.

## Slice order

1. **Kaocha config resolution** — introduce helpers for detecting/loading `tests.edn`, preserving supplied `:config`, and retaining the normalized synthetic fallback.
2. **Runtime default merge** — add a small helper that appends capture-output when absent and forces quiet reporter/color settings for all config sources.
3. **Suite option validation and selector resolution** — implement `:suite`/`:suites` normalization, conflict errors, exact matching, textual fallback matching, unknown selector errors, and ambiguous selector errors.
4. **Suite selection application** — apply resolved suite ids to the resolved config using Kaocha semantics and ensure selection works for loaded, fallback, and supplied configs.
5. **Kaocha adapter tests** — add fixtures/tests for `tests.edn` loading, selected suites, `:suite` convenience, full-config preservation, selector edge cases, and no-config fallback.
6. **Documentation updates** — update README, AGENTS.md, and SKILL.md with REPL-first Kaocha examples and option semantics.
7. **Verification** — run focused Kaocha adapter tests plus the existing project tests/aliases that should still pass.
## Test and fixture decisions

- Kaocha adapter tests will live under `test/scry/kaocha_test.clj`. The namespace will require `scry.kaocha`, so it is loaded only by focused commands that include the optional `:kaocha` alias. Core verification remains `clojure -M:test -e "(require '[scry.core :as scry]) (scry/run)"`, which does not discover or require the Kaocha adapter namespace unless the test namespace is explicitly required.
- Focused Kaocha adapter verification will use both the core test path and the optional adapter path/dependency: `clojure -M:test:kaocha -e "(require '[scry.kaocha-test :as t] '[clojure.test :as ct]) (ct/run-tests 'scry.kaocha-test)"`. This keeps the optional Kaocha dependency out of normal core test runs while allowing adapter tests to share the existing `test/` tree.
- `tests.edn` loading tests will create temporary project directories with their own `src`/`test` files and `tests.edn`, bind execution to those directories by temporarily setting `System/getProperty "user.dir"`, and restore the original value in `try`/`finally`. Each fixture must assert or structure cleanup so a failed run cannot leak `user.dir` into later tests. Temporary project tests must not rely on the repository root config, and no repository-root `tests.edn` should be consulted.

