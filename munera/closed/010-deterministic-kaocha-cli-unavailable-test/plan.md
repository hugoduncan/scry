# Plan

## Approach

Implement the task as a minimal internal CLI infrastructure-boundary change. Preserve the production optional-dependency boundary by keeping `scry.cli` free of any namespace-load `scry.kaocha` require, and make the default Kaocha runner resolver call `(requiring-resolve 'scry.kaocha/run)` only when Kaocha CLI mode is actually executed.

Add `:resolve-kaocha-runner` to the `run-cli` IO/infrastructure boundary. The boundary is zero-arity and returns the invokable Kaocha runner. `run-kaocha` remains the orchestration point: it obtains the resolver from the merged boundary, resolves the runner, validates that the result is invokable, adds the CLI `:progress-callback` to the already-normalized Kaocha option map, invokes the runner, and leaves result-directory cleanup, result files, summary printing, stderr reporting, exit-code calculation, and structured outcomes to the existing `run-cli` path.

Keep the unavailable-adapter public error contract stable. If resolver execution fails with `java.io.FileNotFoundException`, wrap it as the existing Kaocha-unavailable runner error with message `Kaocha CLI mode requires the optional scry.kaocha adapter on the classpath` and ex-data including `{:type :scry.cli/runner-error :runner :kaocha}`. Other resolver/load failures are wrapped as `Could not load Kaocha CLI runner` with the same runner-error type/runner data. Nil and non-invokable resolver returns use the distinct runner-error message `Resolved Kaocha CLI runner is not invokable`; because `run-cli` prefixes caught errors, stderr should include `scry CLI error: Resolved Kaocha CLI runner is not invokable`. Their ex-data must still include `{:type :scry.cli/runner-error :runner :kaocha}` (optionally with diagnostic return-value class detail), and they must not be classified as argument errors.

Update the core CLI test to simulate the unavailable adapter by injecting only `:resolve-kaocha-runner` into `cli/run-cli`, still exercising `run-cli` and `run-kaocha` end-to-end. Leave available-Kaocha behavior covered in `scry.cli-kaocha-test` on the `:test:kaocha` classpath using the default resolver.

## Risks

- Accidentally requiring `scry.kaocha` at `scry.cli` namespace load time would break the core jar's optional dependency boundary.
- Injecting a whole Kaocha runner instead of a resolver would bypass too much production orchestration and weaken the regression test.
- Resolver error wrapping could drift from the existing stderr/ex-data contract expected by CLI callers and tests.
- Nil or non-invokable resolver results must be classified as runner failures, not option-normalization failures.
- Core tests must remain deterministic whether or not the optional Kaocha adapter is already present in a long-lived REPL classpath.

## Slice order

1. Inspect the current CLI boundary and Kaocha execution flow to identify the smallest resolver-level insertion point.
2. Add the default `:resolve-kaocha-runner` boundary and update `run-kaocha` to resolve, validate, wrap errors, attach progress, and invoke through it.
3. Update the core unavailable-Kaocha CLI test to inject a throwing resolver and assert the existing structured error contract end-to-end.
4. Confirm optional Kaocha CLI coverage still exercises the default resolver and available-adapter execution path under `:test:kaocha`.
5. Run focused core/CLI and optional Kaocha verification, then record the command-line verification results in `implementation.md` before handoff.
