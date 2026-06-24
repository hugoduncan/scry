# Plan: 022 suppress stray Kaocha framework stdout

## Approach

Bind `*out*` and `*err*` to a discarding sink around the `api/run` call in
`scry.kaocha/run`, inside the existing `capture/without-context` /
`*report-counters*` binding. Kaocha framework-level direct prints (the
`randomize` plugin's `post-run` seed line, and any similar chatter) are
discarded; scry's progress callback and CLI summary write to the boundary
stream objects (not the dynamic vars) and Kaocha's `capture-output` plugin
rebinds `*out*` per test, so both are unaffected.

## Decisions

- Sink is a `PrintWriter` over `OutputStream/nullOutputStream` — no in-memory
  accumulation.
- Suppress both `*out*` and `*err*`: consistent with the adapter already
  forcing `:kaocha/reporter []` and `:kaocha/color? false` and discarding any
  user reporter, so no information the adapter intended to keep is lost.
- Discard rather than surface the seed. Reproducible-seed surfacing, if ever
  wanted, is a separate structured enhancement.

## Risks

- Over-suppression hiding something a caller wanted on `*out*`/`*err*`. Mitigated
  by the fact that the adapter already discards Kaocha's reporting and overwrites
  user reporters; per-test output is captured structurally by Kaocha's
  capture-output plugin and surfaced in entry `:out`.

## Verification

- New adapter regression test: failing randomized run leaks nothing to a
  bound `*out*`.
- Focused command-line checks: `scry.kaocha-test`, `scry.cli-kaocha-test`,
  `scry.cli-test`.
- Manual end-to-end CLI repro against a failing Kaocha project.
