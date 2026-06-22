# Implementation notes: 022

## Root cause (confirmed by reproduction)

`kaocha.plugin/randomize` `post-run` hook:

```clojure
(post-run [test-plan]
  (if (and (::randomize? test-plan) (result/failed? test-plan))
    (print "\nRandomized with --seed" (::seed test-plan)))
  test-plan)
```

— a direct `*out*` `print` (leading `\n`, no trailing newline), only on failure.
It bypasses the reporter that the adapter silences (`:kaocha/reporter []`), so it
landed on scry's clean stdout and abutted the CLI summary:

```
Randomized with --seed 1922892909Assertions: 9 passed, 2 failed, 0 errored
```

Reproduced directly: binding `*out*` to a StringWriter around `scry.kaocha/run`
on a failing temp project captured exactly `"\nRandomized with --seed 1184241963"`.

## Fix

`src-kaocha/scry/kaocha.clj`:

- Added `discarding-writer` (PrintWriter over `OutputStream/nullOutputStream`).
- Bound `*out*` and `*err*` to discarding sinks around `api/run`, inside the
  existing `capture/without-context` / `*report-counters*` binding.

Why it is safe:

- scry's progress callback (`progress!`) and CLI summary write to the boundary
  stream objects `(:out boundary)` / `(:err boundary)`, not the dynamic vars,
  so they are unaffected.
- Kaocha's `capture-output` plugin rebinds `*out*` per test to capture per-test
  output, restoring to the (now sink) outer binding afterward, so entry `:out`
  capture is unchanged.
- The adapter already forces `:kaocha/reporter []`, `:kaocha/color? false`, and
  overwrites any user reporter, so discarding framework direct prints loses
  nothing the adapter was not already dropping.

## Verification

- After the fix the StringWriter capture is `""`; summary stands alone.
- End-to-end `-m scry.cli --runner kaocha` against a failing project:
  `..Assertions: 2 passed, 2 failed, 0 errored` (no seed line), exit 1,
  per-test stdout (`hello-from-test`) present in the `.scry-results` EDN.
- Regression test `does-not-leak-framework-stdout-on-failing-run-test` added.
- `scry.kaocha-test` (16 tests, 78 assertions), `scry.cli-kaocha-test`
  (24/135), `scry.cli-test` (45/363) all green. `bb clj-fmt:check` /
  `bb clj-kondo:lint` clean.

## Scope notes

- Independent pre-existing adapter bug, surfaced while exercising task 021.
- No public API change: `scry.kaocha/run` signature/docstring unchanged, no
  `doc/API.md` regeneration needed. No README/AGENTS changes.
