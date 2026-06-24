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

Two parts: suppress the malformed framework print, and surface the seed cleanly
as structured data (maintainer override of the original deferral — the seed is
wanted for reproducing failing orders).

1. In `scry.kaocha/run`, bind `*out*` and `*err*` to a discarding sink around
   the `api/run` call (inside the existing `capture/without-context` /
   `*report-counters*` binding), so Kaocha framework-level direct prints (the
   randomize seed line, which is failure-only and has no trailing newline) are
   discarded. scry's progress callback and CLI summary are unaffected because
   they write to the boundary stream objects, not the dynamic vars; Kaocha's
   per-test output capture is unaffected because it rebinds `*out*` per test.
   Use a null writer backed by `java.io.OutputStream/nullOutputStream` (no
   in-memory accumulation).

2. Surface the seed as structured run metadata: read
   `:kaocha.plugin.randomize/seed` from the Kaocha result (present only when
   randomization was active — i.e. the tests.edn / full-plugin path, not the
   synthetic fallback) and put it in the scry result `:summary :seed`.

3. In the CLI, print a clean `Randomized with --seed N` line on stdout *after*
   the summary block (so the established `.Assertions:` progress-dot contract is
   untouched), gated to failing outcomes (`failure-outcome-kinds`), mirroring
   Kaocha's own failure-only seed reporting. The structured `:summary :seed` is
   present on pass and fail; only the CLI *display* is failure-gated.

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

- A failing randomized Kaocha run no longer leaks Kaocha's stray
  `Randomized with --seed N` print onto scry's summary stream.
- The randomize seed is surfaced as `:summary :seed` in the structured result
  on both passing and failing randomized runs.
- The CLI prints a clean `Randomized with --seed N` line after the summary on
  failing Kaocha runs, and omits it on passing runs; the `.Assertions:` prefix
  contract is preserved.
- Kaocha per-test captured output still appears in entry `:out`.
- Focused tests cover: no `*out*` leak + seed surfaced (failing tests.edn run),
  seed surfaced on a passing run, and the CLI seed line present-on-fail /
  absent-on-pass.

## Open Questions

- Resolved: the Kaocha seed is surfaced structurally (and in the CLI on
  failure), per maintainer request, rather than discarded. The seed only exists
  when randomization is active (tests.edn / full-plugin config); the synthetic
  fallback config has no randomize plugin and therefore no seed.
