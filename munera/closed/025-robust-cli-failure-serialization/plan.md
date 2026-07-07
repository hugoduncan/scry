# Plan

## Approach

Implement the work in layers so the CLI first preserves the test-derived outcome, then makes the diagnostic data path robust enough that fallback handling should rarely be needed.

Key decisions:

- Treat the test run and diagnostic/result-file rendering as separate phases. Once canonical entries, summary, and `classify-outcome` have produced a test-derived outcome, later diagnostic failures must not enter the outer `run-cli` runner-error path.
- Move normal summary emission earlier in `run-cli`: after classification and before result-file writing. Keep it the existing single summary, not a duplicate error-style summary.
- Wrap result-file writing in a focused diagnostic boundary that returns either result-file paths or a bounded `:scry.cli/diagnostic-error` map plus an empty file vector.
- Make `scry.cli.results/edn-readable-data` explicitly bounded: identity-cycle detection, maximum depth, maximum sequence length, bounded strings, and tagged placeholders for truncation/cycles/non-EDN values.
- Normalize `Throwable` values through a controlled shape rather than walking them as arbitrary object graphs. Bound cause chains, stack frames, suppressed exceptions, ex-data, and strings.
- Add tests at both levels: direct sanitizer/Throwable normalization tests and CLI regression tests that exercise cyclic assertion data and cyclic ex-data without producing `StackOverflowError` or `:scry.cli/runner-error`.
- Update user-facing documentation if behavior or result-map keys are exposed beyond tests, especially for `:scry.cli/diagnostic-error` and sanitizer placeholders.

## Risks

- Reordering summary/result-file writing can subtly change stdout/stderr ordering; tests should assert only the intended ordering and avoid brittle full-output matching where possible.
- Cycle detection based on identity must not mark shared immutable EDN values as cycles incorrectly, while still preventing recursion through Java objects and collections.
- Throwable normalization must preserve useful root-cause information without reintroducing unbounded traversal through `ex-data`, causes, suppressed exceptions, or stack frames.
- Existing tests may assume result files are always written for failures; update expectations only where diagnostic fallback behavior intentionally changes them.
- The fallback path for result-file-writing failure may be hard to trigger naturally; use a focused with-redefs/unit-level test if needed, while keeping end-to-end CLI tests for the sanitizer regressions.

## Slice order

1. Baseline orientation and characterization: inspect current CLI/result serialization flow and add focused failing/regression tests that reproduce cyclic data behavior.
2. Robust EDN sanitizer: implement bounded, cycle-safe `edn-readable-data` and direct tests for depth, sequence, cycle, non-EDN, and string limits.
3. Controlled Throwable normalization: implement bounded Throwable conversion and tests for cause depth, suppressed count, stack frames, cyclic ex-data, and root-cause preservation.
4. Non-authoritative diagnostic writing: restructure `run-cli` so classification and summary happen before result-file writing, wrap result-file writing failures, attach `:scry.cli/diagnostic-error`, and emit fallback stderr diagnostics.
5. End-to-end CLI regressions: verify cyclic assertion actuals and cyclic ex-data produce test-derived outcomes, summaries, placeholders or fallback diagnostics, and never surface `StackOverflowError` as the primary outcome.
6. Documentation and final verification: update README/AGENTS/state only if needed for user-facing behavior, run focused and command-line verification, and record results in `implementation.md`.
