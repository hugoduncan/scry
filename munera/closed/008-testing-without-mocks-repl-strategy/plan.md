# Plan

## Approach

Implement the Testing Without Mocks REPL strategy as a documentation-and-test-hygiene task, not as a new dispatcher API.

Key decisions:

- Use explicit documented `scry/run` namespace selections as the canonical REPL slice mechanism. Do not add a helper namespace or Babashka task in this task.
- Treat classpath-sensitive checks as separate runtime contexts:
  - core-only `:test` REPL: `scry.capture-test`, `scry.clojure-test-test`, `scry.cli-test` through `scry/run`
  - optional `:test:kaocha` REPL: `scry.kaocha-test`, `scry.cli-kaocha-test` through `clojure.test/run-tests`, because these tests intentionally run failing inner Kaocha projects and an outer `scry/run` would capture nested reports as verification failures
  - build `:test:build` REPL: `scry.build-test` through `scry/run`
  - release helper `:test:release-test` REPL: `scry.release-test` through `scry/run`
- Update `AGENTS.md` so focused slices are the canonical REPL development checks, and broad `(scry/run)` is qualified as a fresh-process smoke/discovery check only.
- Update `.github/workflows/ci.yml` so the primary core signal is the explicit focused core slice. If broad discovery remains, make it a separately named smoke step.
- Keep `.github/workflows/release.yml`'s existing broad core test unchanged for this task and treat it as release-workflow smoke coverage: the release workflow already has focused build and release-helper checks, and changing release gating is not required by the reviewed task slice.
- Include `scry.cli-kaocha-test` alongside `scry.kaocha-test` in CI optional Kaocha verification. Until the temp namespace cleanup slice makes the combined `scry/run` form deterministic, run the two optional namespaces in separate fresh processes so CI gets both protections without relying on shared Kaocha runtime state.
- Audit tests for mock-like seams and state leakage, but keep accepted Nullable infrastructure boundaries where they stand in for external infrastructure or process termination and assert visible state/output.
- Make temporary Kaocha/project namespaces unique per run and remove generated namespaces in fixture cleanup while continuing to use real temp directories, real `tests.edn`, and real Kaocha config.
- Preserve public `scry` API/result shapes and CLI result shapes.

## Risks

- Long-lived REPL behavior can still be affected by process-global state outside this task's tests; implementation should clean the known `user.dir` and generated namespace cases and document any remaining fresh-process boundary.
- Kaocha test namespace uniqueness may require updating assertions that currently expect fixed namespace/file names.
- CI changes must keep the same protection as existing checks while changing the core test step's selection semantics.
- Over-removing Nullable boundaries could make external-effect tests brittle; only replace seams when a real state-based path is practical.

## Slice order

1. **Document canonical REPL slices** — update `AGENTS.md` to present focused core, Kaocha, build, and release-helper REPL snippets as the normal development checks; qualify broad discovery as smoke-only.
2. **Make CI primary core signal focused** — update `.github/workflows/ci.yml` to run the explicit core namespace slice and fail on `:pass? false`; optionally keep broad discovery as a separately named smoke check.
3. **Harden temporary namespace cleanup** — update Kaocha and Kaocha CLI temporary-project fixtures to generate unique namespaces and call `remove-ns` in cleanup; adjust state-based assertions to use generated namespace/file names.
4. **Audit Nullable boundaries and mock-like seams** — review CLI/build/release tests and implementation comments for remaining injected boundaries; replace practical logical-collaborator seams with real state-based paths, and document accepted external-effect boundaries where they remain.
5. **Verify focused REPL/command slices** — run the documented focused slices in their alias contexts and the relevant command-line checks for CI/build/release surfaces; record results in `implementation.md`.
