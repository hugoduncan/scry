# Plan

## Approach

Keep the change localized to the CLI-owned surfaces: progress rendering, `.scry-results/` naming/writing, `run-cli` outcome maps, and the `clojure -X` wrapper ex-data. Do not change `scry.core/run` result shapes or the core/Kaocha dependency boundary.

Key implementation decisions:

- Treat a concrete executable test entry as a canonical result entry whose `:var` is a symbol with both namespace and name. Anything else is synthetic/non-var-backed for CLI execution and classification purposes.
- Add result-entry identity helpers in `scry.cli.results` so var-backed filenames remain exactly `<encoded-ns>__<encoded-var>.edn`, while synthetic failing/erroring entries receive deterministic `suite-error-N.edn` / `suite-fail-N.edn` names, optionally namespace-prefixed when `:ns` is present.
- Compute synthetic result-file names from the whole canonical result collection before writing files, using per-status counters plus a used-filename set and deterministic `--2`, `--3`, ... collision suffixes for file paths only.
- Make live progress robust for non-var-backed entries by using a per-run progress-label state in the CLI runner callback. Var-backed progress remains unchanged; synthetic fail/error/unknown progress prints `suite-fail-N`, `suite-error-N`, or `suite-unknown-N`, optionally prefixed with `:ns` for human context.
- Add a small CLI outcome classifier that applies the design precedence and returns top-level `:scry.cli/outcome-kind` for every `run-cli` outcome.
- Make `:scry.cli/outcome-kind` authoritative for `:exit-code`: `:scry.cli/pass` yields `0`; every other kind yields non-zero. Do not keep a parallel pass/fail decision based on total canonical entry count, because synthetic-only passing entries must classify as `:scry.cli/zero-tests` and exit non-zero.
- Preserve existing `:scry.cli/non-zero` behavior for `clojure -X`, adding the same top-level `:scry.cli/outcome-kind` to ex-data and ensuring the embedded `:outcome` contains the same key.
- Keep CLI summary fields human/result-entry oriented for compatibility: `:summary :tests` and `:summary :var-count` continue to count all canonical result entries, including synthetic/non-var-backed entries. Outcome classification uses a separate concrete executable var-backed entry count internally; synthetic entries never make a run executable.
- Keep `-m` parser errors process-oriented: human stderr plus exit code only. Structured argument-error classification is provided by `clojure -X`, direct `scry.cli/run`, and option-normalization surfaces.
- Cover behavior with focused state-based tests that use the existing CLI IO/runner boundary to return canonical nil-var results, avoiding dependence on a particular Kaocha synthetic shape.

## Risks

- Live progress is emitted before the final canonical result vector is available, while result-file collision handling is computed afterward. The implementation must keep progress labels useful without requiring progress labels and suffixed filenames to be identical in collision cases.
- Outcome classification must use aggregate assertion counts as well as canonical entry statuses, but aggregate pass counts must not make a synthetic-only run executable.
- The stdout summary remains a count of all result entries, so synthetic load/unknown entries appear in the `Tests:` line as failed/errored/unknown entries even though they are not concrete executable vars for pass/zero-tests classification; a genuinely empty canonical vector prints `Tests: 0 passed, 0 failed, 0 errored` and classifies/exits as zero-tests.
- Malformed runner results, result-directory failures, and optional Kaocha loading failures must remain runner/CLI infrastructure errors rather than being misclassified as test failures.
- Docs must present `:scry.cli/outcome-kind` as an additive CLI contract, not as a `scry.core/run` result-map change.

## Slice order

1. **Baseline and focused characterization** — inspect current CLI result/progress/outcome code, then add failing/regression tests for nil-var result files, nil-var progress, and run-cli nil-var synthetic entries.
2. **Synthetic entry identity and result files** — implement concrete-var detection, synthetic display tokens, deterministic synthetic file naming, namespace prefixing, and filename collision handling in `scry.cli.results`.
3. **Synthetic progress labels** — update CLI progress callbacks to tolerate nil/absent/non-concrete vars and print per-run synthetic labels for failing/erroring/unknown entries.
4. **Outcome classification** — implement precedence-based `:scry.cli/outcome-kind` classification for successful outcomes, runner errors, load errors, test failures, unknown results, zero tests, and structured argument errors.
5. **`clojure -X` propagation and parser boundary checks** — ensure `scry.cli/run` non-zero ex-data exposes top-level outcome kind and preserves the same key inside the embedded outcome, while `-m` parser errors remain process-only.
6. **Documentation** — update README.md, SKILL.md, AGENTS.md, and CHANGELOG.md for the new machine-readable CLI contract and synthetic suite-level result-file handling.
7. **Verification and task notes** — run focused CLI checks plus any affected core/Kaocha CLI checks, record exact commands/results in `implementation.md`, and keep `steps.md` checked as slices complete.
