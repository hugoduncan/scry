# Steps

## Slice 1: Document canonical REPL slices

- [x] Replace the primary broad `(scry/run)` REPL guidance in `AGENTS.md` with focused core slice guidance for `scry.capture-test`, `scry.clojure-test-test`, and `scry.cli-test`.
- [x] Add the optional Kaocha REPL slice to `AGENTS.md` for `scry.kaocha-test` and `scry.cli-kaocha-test` in a `:test:kaocha` REPL.
- [x] Add focused build and release-helper REPL slices to `AGENTS.md` for `scry.build-test` and `scry.release-test` in their required alias contexts.
- [x] Qualify any remaining broad `(scry/run)` example in `AGENTS.md` as fresh-process smoke/discovery coverage, not the canonical all-green REPL check.
- [x] Ensure final verification guidance in `AGENTS.md` still distinguishes REPL iteration from command-line/process checks.

## Slice 2: Make CI primary core signal focused

- [x] Update `.github/workflows/ci.yml` so the primary core test step selects `scry.capture-test`, `scry.clojure-test-test`, and `scry.cli-test` explicitly through `scry/run`.
- [x] Ensure the focused core CI step prints `scry/report-string` and exits non-zero when `:pass?` is false.
- [x] Decide whether to retain broad discovery in CI; if retained, rename it as a fresh-process smoke/discovery step and keep it separate from the primary core step.
- [x] Verify existing Kaocha, build, jar, and release-related CI protections remain present or intentionally unchanged.

## Slice 3: Harden temporary namespace cleanup

- [x] Audit `test/scry/kaocha_test.clj` for hard-coded generated temporary project namespaces.
- [x] Change Kaocha temporary-project helpers/tests to generate unique namespace names per test run.
- [x] Ensure Kaocha temporary-project fixtures call `remove-ns` for every generated namespace in a `finally` cleanup path after restoring `user.dir`.
- [x] Update Kaocha adapter assertions to derive expected vars and result data from the generated namespace symbols instead of fixed namespace strings.
- [x] Audit `test/scry/cli_kaocha_test.clj` for hard-coded generated temporary project namespaces.
- [x] Change Kaocha CLI temporary-project helpers/tests to generate unique namespace names per test run.
- [x] Ensure Kaocha CLI temporary-project fixtures call `remove-ns` for every generated namespace in a `finally` cleanup path after restoring `user.dir`.
- [x] Update Kaocha CLI result-file, stderr/stdout, and canonical-result assertions to use generated namespace/file names.

## Slice 4: Audit Nullable boundaries and mock-like seams

- [x] Review `scry.cli` tests for uses of `:run-clojure-test` and keep only cases justified by public runner-shaped data or replace them with real runner paths.
- [x] Confirm runner-exception coverage continues to use a real missing namespace path rather than an injected throwing runner.
- [x] Review build tests around `git-rev-count`, `deploy`, and `deploy-all` to ensure nullable process/deploy boundaries assert exceptions, artifact state, and deploy argument maps rather than interaction-only expectations.
- [x] Review release helper tests around `command-fn` to ensure assertions are on versions, changelog text, command plans/recorded commands, and ex-data.
- [x] Record any accepted Nullable infrastructure boundaries or remaining fresh-process limitations in `implementation.md` and, if user-facing for agents, in `AGENTS.md`.

## Slice 5: Verify focused slices and final checks

- [x] Run the documented focused core slice in the `:test` alias context and record the result in `implementation.md`.
- [x] Run the documented optional Kaocha slice in the `:test:kaocha` alias context and record the result in `implementation.md`.
- [x] Run the documented focused build slice in the `:test:build` alias context and record the result in `implementation.md`.
- [x] Run the documented focused release-helper slice in the `:test:release-test` alias context and record the result in `implementation.md`.
- [x] Run local workflow linting or an equivalent YAML check if CI workflow syntax changed.
- [x] Run the relevant command-line final verification checks for changed documentation/test/CI surfaces and record commands/results in `implementation.md`.

## Review follow-up: plan ambiguity

- [x] Decide and document whether `.github/workflows/release.yml` must replace its broad `Run core tests` step with the focused core slice, or intentionally keep it unchanged as release-workflow smoke coverage.
- [x] Decide and document whether CI optional Kaocha verification should include the documented `scry.cli-kaocha-test` namespace alongside `scry.kaocha-test`, or intentionally remain adapter-only as existing protection.
- [x] Delete temporary project directories created by `test/scry/kaocha_test.clj` after each test, preferably by shaping its helper to match the `with-temp-dir` cleanup pattern used in `test/scry/cli_kaocha_test.clj` while preserving namespace cleanup.

## Review follow-up: code-shaper

- [x] Replace the `System/nanoTime`-based generated namespace suffixes in `test/scry/kaocha_test.clj` and `test/scry/cli_kaocha_test.clj` with an explicit collision-resistant or monotonic id source, preferably factored consistently across the adjacent helpers.
