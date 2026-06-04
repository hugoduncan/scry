# Steps

## Slice 1: Kaocha config resolution

- [x] Inspect `kaocha.config/load-config` and `kaocha.config/apply-cli-args` behavior in a REPL or one-off command with the current Kaocha dependency.
- [x] Add a helper in `src-kaocha/scry/kaocha.clj` that detects whether `tests.edn` exists in the current working directory.
- [x] Add a helper that loads Kaocha config from `tests.edn` only when the file exists.
- [x] Refactor the existing synthetic fallback config builder so it still uses `:source-paths`, `:test-paths`, and `:ns-patterns` and normalizes only that fallback config.
- [x] Add a config-resolution helper that chooses supplied `:config`, loaded `tests.edn`, or synthetic fallback in that precedence order.

## Slice 2: Runtime default merge

- [x] Add a helper that appends `:kaocha.plugin/capture-output` to `:kaocha/plugins` only when it is absent.
- [x] Add a helper that forces `:kaocha/reporter` to `[]` and `:kaocha/color?` to `false` without changing unrelated config keys.
- [x] Apply the runtime-default merge to every resolved config path immediately before running Kaocha.

## Slice 3: Suite option validation and selector resolution

- [x] Add validation that throws `ex-info` when both `:suite` and `:suites` are supplied, including both values in `ex-data`.
- [x] Add validation that throws `ex-info` when `:suites` is a string, map, scalar selector value, or empty collection, including the invalid value in `ex-data`.
- [x] Normalize `:suite` into a single-selector collection and valid `:suites` into a selector collection for shared processing.
- [x] Add helper logic to collect configured suite ids from `:kaocha/tests` in the resolved config.
- [x] Implement exact selector matching against suite ids before fallback matching.
- [x] Implement fallback text extraction: strings use their own value, named values use `(name value)`, and other values have no fallback text.
- [x] Implement unique fallback text matching against suite-id text using the same string/named-value rules.
- [x] Throw `ex-info` for unknown suite selectors with the selector and available suite ids in `ex-data`.
- [x] Throw `ex-info` for ambiguous fallback matches with the selector and matching suite ids in `ex-data`.

## Slice 4: Suite selection application

- [x] Apply resolved suite ids to the resolved config using `kaocha.config/apply-cli-args` if inspection confirms it matches Kaocha suite-selection semantics.
- [x] If `apply-cli-args` is unsuitable, apply the equivalent skip marking directly to non-selected suites in the config.
- [x] Decide and document how resolved non-string suite ids are passed to `apply-cli-args` or skip marking so exact selected ids, namespace-qualified ids, and string ids are not rematched ambiguously during selection application.
- [x] Wire suite selection into `run` after base config resolution and before runtime-default merging.
- [x] Confirm selected suite ids work for loaded `tests.edn`, supplied full `:config`, and fallback synthetic config.

## Slice 5: Kaocha adapter tests

- [x] Decide and document the exact Kaocha adapter test location and command/alias combination so core `:test` runs do not require the optional Kaocha dependency while focused Kaocha tests include both test sources and `src-kaocha`.
- [x] Add Kaocha adapter tests under the existing test tree that run only when the `:kaocha` alias is active.
- [x] Add isolated fixtures or temporary project directories for a project with `tests.edn` defining multiple suites.
- [x] Specify and implement the working-directory isolation/restoration strategy for `tests.edn` loading tests so they cannot accidentally read the repository root config or leak `user.dir` changes across tests.
- [x] Test that `(scry.kaocha/run)` loads and runs configured `tests.edn` suites instead of only the synthetic `:unit` suite.
- [x] Test that `{:suites [...]}` runs only the requested suite ids.
- [x] Test the `{:suite ...}` single-suite convenience form if implemented.
- [x] Test that supplying both `:suite` and `:suites` throws a clear `ex-info`.
- [x] Test that invalid `:suites` values (string, map, scalar selector, and empty collection) throw clear `ex-info` errors.
- [x] Test exact selector matching, string/name fallback matching, unknown selector errors, and ambiguous fallback errors.
- [x] Test that selection application preserves already resolved exact ids without reintroducing ambiguity for namespace-qualified keyword/symbol ids or string ids.
- [x] Test that `{:config full-kaocha-config}` is used without fallback source/test/ns-pattern merging and still supports suite selection.
- [x] Test that projects without `tests.edn` retain the current synthetic `:unit` fallback behavior.
- [x] Test that runtime defaults preserve existing plugins, avoid duplicate capture-output, and force quiet reporter/color settings.
- [x] Test that returned result maps still follow the existing scry scoped result model and honor `:result-format`.

## Slice 6: Documentation updates

- [x] Update the README Kaocha section with REPL-first examples for `(k/run)`, `{:suites [...]}`, `{:suite ...}`, and `{:config ...}`.
- [x] Update README option semantics for suite selector matching, conflicts, and fallback behavior without introducing command-line-first guidance.
- [x] Update `AGENTS.md` Kaocha guidance to describe `tests.edn` loading and REPL suite selection.
- [x] Update `SKILL.md` with the same public Kaocha API guidance for agents using scry in other projects.

## Slice 7: Verification

- [x] Run the focused Kaocha adapter test command with the `:kaocha` alias.
- [x] Run the existing core test command to confirm no regression outside the Kaocha adapter.
- [x] Run any full project verification command documented in README or AGENTS.md.
- [x] Record important implementation decisions, discoveries, and verification results in `implementation.md`.

## Review follow-up

- [x] Reconcile `steps.md` and `implementation.md` with the already-applied `src-kaocha/scry/kaocha.clj` changes and existing `test/scry/kaocha_test.clj` coverage so completed code/test work is checked or accurately noted before continuing implementation.
- [x] Update `CHANGELOG.md` Unreleased with the user-visible Kaocha adapter changes: `tests.edn` loading, REPL suite selection via `:suite`/`:suites`, full `:config` preservation, and related selector/error semantics.
