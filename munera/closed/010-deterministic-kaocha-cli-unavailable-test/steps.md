# Steps

## Slice 1 — Inspect current CLI/Kaocha flow

- [x] Inspect `src/scry/cli.clj` to locate the current Kaocha dynamic `requiring-resolve` call and the existing `run-cli` IO/infrastructure boundary merge.
- [x] Inspect `test/scry/cli_test.clj` unavailable-Kaocha assertions and `test/scry/cli_kaocha_test.clj` available-Kaocha coverage to confirm the intended split.

## Slice 2 — Add resolver-level boundary

- [x] Add a default zero-arity `:resolve-kaocha-runner` function to the CLI boundary that dynamically resolves `scry.kaocha/run` at Kaocha execution time.
- [x] Update `run-kaocha` to obtain and call `:resolve-kaocha-runner`, add the CLI `:progress-callback` to the normalized Kaocha options, and invoke the returned runner.
- [x] Wrap `java.io.FileNotFoundException` from resolver execution as the existing unavailable-adapter `:scry.cli/runner-error` for `:runner :kaocha`.
- [x] Wrap other resolver/load failures as `Could not load Kaocha CLI runner` with the same runner-error type/runner data.
- [x] Treat nil or non-invokable resolver returns as Kaocha runner errors, not argument errors.
- [x] Verify `scry.cli` still has no namespace-load require of `scry.kaocha`.

## Slice 3 — Deterministic core CLI test

- [x] Update `run-cli-no-tests-and-runner-errors-test` to inject only `:resolve-kaocha-runner` throwing `FileNotFoundException` for the unavailable-adapter scenario.
- [x] Assert the injected unavailable-adapter run still goes through `cli/run-cli` end-to-end and preserves exit code, stderr text, and `[:error :data]` runner-error fields.
- [x] Add focused assertions for nil or non-invokable resolver return behavior if not already covered by the unavailable-adapter scenario.
- [x] Clarify in `plan.md` the exact stderr/error-message contract for nil or non-invokable `:resolve-kaocha-runner` returns before adding the focused assertions for that branch.
- [x] Add focused core CLI coverage for a `:resolve-kaocha-runner` non-`FileNotFoundException` resolver/load failure, asserting the `Could not load Kaocha CLI runner` stderr/message and `{:type :scry.cli/runner-error :runner :kaocha}` data.
- [x] Add focused core CLI coverage proving a normal `:clojure-test` run does not call `:resolve-kaocha-runner`, so core runner execution is independent of optional Kaocha resolution.

## Slice 4 — Optional Kaocha coverage

- [x] Confirm `scry.cli-kaocha-test` continues to use the default resolver path rather than an injected resolver.
- [x] Run the optional Kaocha CLI focused tests with the `:test:kaocha` classpath and record the result.

## Slice 5 — Verification and notes

- [x] Run the focused core CLI test namespace and record the result in `implementation.md`.
- [x] Run the focused core REPL slice from the acceptance criteria, including `scry.cli-test`, and record the result in `implementation.md`.
- [x] Run the required final command-line verification for the changed CLI surface and record commands/results in `implementation.md`.
- [x] Update any task notes in `implementation.md` with implementation decisions, discoveries, and final verification details.
