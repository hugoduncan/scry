# Deterministic Kaocha CLI unavailable test

## Goal

Make the core CLI test suite deterministic when the project REPL or test process has the optional Kaocha adapter on the classpath.

The current `scry.cli-test/run-cli-no-tests-and-runner-errors-test` includes a scenario that expects `:runner :kaocha` to fail because `scry.kaocha` is unavailable. That expectation is valid for a core-only `:test` process, but it fails in a long-lived project REPL or any process started with the optional Kaocha classpath, because `scry.kaocha/run` can be dynamically resolved and Kaocha mode succeeds.

## Problem

The test is asserting classpath absence as ambient process state. This conflicts with the repository's REPL-first workflow and the Testing Without Mocks direction: focused tests should be deterministic in long-lived REPL sessions and should avoid depending on accidental classpath composition.

Production behavior is correct and should remain:

- `scry.cli` must not require `scry.kaocha` at namespace load time.
- Kaocha CLI mode must dynamically resolve `scry.kaocha/run` only when requested.
- If the optional adapter is unavailable, Kaocha CLI mode must return the existing structured runner error outcome.
- If the optional adapter is available, Kaocha CLI mode should run normally.

The unreliable part is only the core test's way of exercising the unavailable-adapter branch.

## Scope

In scope:

- Add a small injectable/nullable infrastructure boundary in `scry.cli` for resolving or invoking the optional Kaocha runner, analogous in spirit to the existing `:run-clojure-test` boundary.
- Keep the default production boundary dynamically resolving `scry.kaocha/run` at execution time.
- Update `scry.cli-test/run-cli-no-tests-and-runner-errors-test` so the unavailable-Kaocha scenario is simulated deterministically through the boundary rather than through ambient classpath absence.
- Preserve the existing structured error contract for unavailable Kaocha mode:
  - `:exit-code 1`
  - stderr includes `scry CLI error:` and the unavailable adapter message
  - `[:error :data]` includes `{:type :scry.cli/runner-error :runner :kaocha}`
- Add or adjust focused test coverage so successful Kaocha behavior remains covered in the optional Kaocha CLI test namespace/classpath.
- Update task notes and final verification records.

Out of scope:

- Changing public CLI options or result shapes.
- Making the core jar depend on Kaocha.
- Moving optional Kaocha adapter code out of `src-kaocha/`.
- Replacing the documented final command-line verification workflow.
- Broad redesign of the CLI boundary system beyond the minimum needed deterministic optional-adapter seam.

## Acceptance criteria

- The focused core REPL slice can run with the optional Kaocha adapter present and no longer fails solely because Kaocha is available:

  ```clojure
  (require '[scry.core :as scry])

  (scry/run {:namespaces ['scry.capture-test
                          'scry.clojure-test-test
                          'scry.cli-test]})
  (:summary (scry/last-result))
  ```

- `scry.cli-test/run-cli-no-tests-and-runner-errors-test` deterministically covers the unavailable-Kaocha branch without relying on classpath absence.
- Existing optional Kaocha CLI tests still verify the available-Kaocha branch using the `:test:kaocha` classpath.
- The core CLI implementation still loads `scry.kaocha` dynamically only for `:runner :kaocha`.
- Final verification includes at least:
  - focused core CLI/core REPL checks appropriate to the change
  - optional Kaocha CLI checks if the boundary touches Kaocha execution
  - documented command-line checks before handoff

## Design notes

Use a resolver-level internal CLI infrastructure boundary, not a whole-runner replacement. Extend the existing `run-cli` IO/infrastructure boundary with `:resolve-kaocha-runner`:

- Arity: zero arguments.
- Return value: an invokable runner equivalent to `#'scry.kaocha/run`, accepting the already-normalized Kaocha option map passed into `run-kaocha` after `run-kaocha` adds the CLI `:progress-callback`. `run-kaocha` should not own option normalization; `normalize-exec-opts` prepares Kaocha-specific options before dispatch.
- Default production value: dynamically call `(requiring-resolve 'scry.kaocha/run)` at Kaocha execution time, preserving the core jar's optional dependency boundary and avoiding any `scry.kaocha` require at `scry.cli` namespace load time.
- Missing adapter behavior: if the resolver throws `java.io.FileNotFoundException`, `run-kaocha` wraps it in the existing public runner error shape with message `Kaocha CLI mode requires the optional scry.kaocha adapter on the classpath` and ex-data `{:type :scry.cli/runner-error :runner :kaocha}`. Other resolver/load failures should continue to be wrapped as `Could not load Kaocha CLI runner` with the same runner-error type/runner data.
- Invalid resolver return behavior: a nil or non-invokable return value is a runner error for `:runner :kaocha`, not an argument error.

`run-kaocha` remains responsible for normal Kaocha CLI orchestration: resolving the runner through `:resolve-kaocha-runner`, adding the CLI `:progress-callback`, invoking the returned runner, and letting `run-cli` handle result-directory cleanup, result files, summary, stderr, exit code, and structured outcomes. The deterministic unavailable-adapter core test should therefore inject only `:resolve-kaocha-runner` with a function that throws `FileNotFoundException`; it must still call `cli/run-cli` end-to-end and must not replace `run-kaocha` or provide a fake whole-runner implementation.

Successful available-Kaocha behavior remains covered by the optional `scry.cli-kaocha-test` namespace on the `:test:kaocha` classpath using the default resolver path.
