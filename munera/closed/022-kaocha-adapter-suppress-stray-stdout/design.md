# 022: Suppress stray Kaocha framework stdout leaking into scry output

## Goal

Stop Kaocha framework-level direct prints (notably the `randomize` plugin's
`post-run` "Randomized with --seed N" line) from leaking onto scry's clean
output stream, where they abut scry's summary (e.g.
`Randomized with --seed 1922892909Assertions: 9 passed, 2 failed, 0 errored`).

## Context

The Kaocha adapter (`scry.kaocha/run`) deliberately silences Kaocha's terminal
reporting: `apply-runtime-defaults` sets `:kaocha/reporter []` (overwriting any
user/`:config` reporter) and `:kaocha/color? false`, and the adapter produces
its own structured result + the CLI prints its own summary.

However, Kaocha's `kaocha.plugin/randomize` plugin's `post-run` hook does:

```clojure
(print "\nRandomized with --seed" (::seed test-plan))
```

— a direct `*out*` print with a leading `\n` and **no trailing newline**, emitted
only when the run has failures. This bypasses the reporter, so it leaks onto the
real stdout. scry's CLI then writes its summary, which intentionally begins with
`Assertions:` and no leading newline (the progress-dot contract `.Assertions:`
is established and tested in `cli_test.clj`). The two run together.

scry's own progress dots and summary are written directly to the boundary
stream objects (`(:out boundary)` / `(:err boundary)`), independent of the
dynamic `*out*`/`*err*` vars. Kaocha's per-test output is captured by Kaocha's
`capture-output` plugin, which rebinds `*out*` around each test itself. So the
only thing reaching the adapter-level `*out*`/`*err*` during `api/run` is
Kaocha framework chatter that the adapter already intends to suppress.

This is an independent pre-existing adapter bug, surfaced while exercising task
021. It is not specific to suite selection — it triggers on any failing
randomized Kaocha run.

## Approach

In `scry.kaocha/run`, bind `*out*` and `*err*` to a discarding sink around the
`api/run` call (inside the existing `capture/without-context` /
`*report-counters*` binding), so Kaocha framework-level direct prints are
discarded. scry's progress callback and CLI summary are unaffected because they
write to the boundary stream objects, not the dynamic vars; Kaocha's per-test
output capture is unaffected because it rebinds `*out*` per test.

Use a null writer backed by `java.io.OutputStream/nullOutputStream` (no in-memory
accumulation).

## Constraints

- scry's structured result, progress output, and CLI summary must be unchanged.
- Kaocha per-test captured output (`:out` in entries) must be unchanged.
- The `:kaocha/reporter []` / `:kaocha/color? false` suppression intent is
  preserved and extended, not contradicted: the adapter already discards
  Kaocha's terminal reporting and any user reporter, so discarding framework
  direct prints loses nothing the adapter wasn't already dropping.
- Do not change the `-m`/`-X` CLI surfaces or the result model.
- The randomize *behavior* (random test ordering) is unchanged; only the leaked
  seed print is suppressed. (If reproducible-seed surfacing is ever wanted, it
  should be exposed structurally — out of scope here.)

## Acceptance

- A failing randomized Kaocha run no longer leaks `Randomized with --seed N`
  onto the stream carrying scry's summary; the summary stands alone.
- scry's existing Kaocha result shape and CLI summary output are unchanged for
  passing and failing runs.
- Kaocha per-test captured output still appears in entry `:out`.
- A focused test asserts that adapter-level `*out*` framework leakage during a
  failing randomized run does not reach scry's output (e.g. the seed line is not
  present on the captured summary stream).

## Open Questions

- Whether to also surface the Kaocha seed structurally for reproducibility.
  Deferred; out of scope. Current decision: discard the leaked line, consistent
  with the adapter already discarding Kaocha's terminal reporting.
