# 024: Emit a minimal CLI summary on error/exception outcomes

## Intent (why this task exists)

`scry.cli`'s happy path always writes a final `Assertions:/Tests:` summary to
stdout via `write-summary!`. But the exception path in `run-cli`
(`catch Throwable`) returns `:summary nil` and writes only a `scry CLI error:
...` diagnostic to stderr — no stdout summary at all. The same is true for the
argument-error and usage paths, which also produce `:summary nil`.

This means any outcome that arrives via a thrown exception —
`:scry.cli/runner-error` and `:scry.cli/load-error` — produces **no final
summary on stdout**. A user (or wrapping task) watching stdout sees in-progress
output (e.g. Kaocha-mode `.` progress) abruptly end with no closing summary
line, which reads as "the run silently stopped".

This gap became more visible after task 023, which deliberately reclassified
Kaocha-mode argument problems (typos, malformed/unknown options, unknown
selectors) from the clean `:scry.cli/argument-error` into thrown
`:scry.cli/runner-error` / `:scry.cli/load-error`. A larger class of Kaocha
invocations now reaches the no-summary exception path.

(Field note: this surfaced while debugging a downstream project where an
in-process `System/exit` killed the JVM before any summary. That specific cause
was a project bug, but it highlighted that scry's own error path is also
silent on stdout, which makes such situations harder to diagnose.)

## Goal

On error/exception CLI outcomes, emit a minimal but explicit stdout summary so
the run is never silent on stdout. The authoritative machine signals
(`:scry.cli/outcome-kind`, exit code, `.scry-results/*.edn`) must remain
unchanged; this is supplementary human-facing output only.

Concretely, for outcomes where no real run summary is available
(`:scry.cli/runner-error`, `:scry.cli/load-error`, `:scry.cli/argument-error`,
and any other thrown/`:summary nil` outcome), `run-cli` (and the
argument/usage paths) should still write a short, clearly-labelled summary line
to stdout — e.g. an explicit "no tests run" / error-outcome summary naming the
`outcome-kind` — rather than leaving `:summary nil` with nothing on stdout.

## Context / constraints

- Keep existing stdout summary text for successful pass/fail runs byte-stable.
- Do not change exit codes, `:scry.cli/outcome-kind` values, the
  `.scry-results/*.edn` contract, or the stderr `scry CLI error:` /
  `write-failure-diagnostic!` behavior.
- The minimal summary must be unambiguous that no normal test summary was
  produced (i.e. not look like a "0 passed, 0 failed" green run).
- Cover both the in-`run-cli` `catch` path and the argument-error/usage paths
  in `main-outcome`.
- Add CLI tests asserting a minimal stdout summary appears for at least one
  thrown outcome (e.g. Kaocha-mode malformed option → `:scry.cli/runner-error`)
  and for an argument-error outcome.

## Acceptance

- A Kaocha-mode run that fails with `:scry.cli/runner-error` /
  `:scry.cli/load-error` writes a minimal, clearly-labelled summary to stdout
  (in addition to the existing stderr diagnostic).
- Argument-error / usage outcomes likewise produce a minimal stdout summary.
- Successful pass/fail summary output is unchanged.
- Exit codes, `:scry.cli/outcome-kind`, and `.scry-results` outputs unchanged.
- Focused `scry.cli-test` (and Kaocha CLI tests where relevant) cover the new
  behavior and pass.

## Out of scope

- Changing the task-023 reclassification of Kaocha-mode argument typos.
- Restructuring the result/outcome model or the structured `:summary` map shape
  for successful runs.
