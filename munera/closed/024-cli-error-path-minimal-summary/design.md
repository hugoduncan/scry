# 024: Emit a minimal CLI summary on error/exception outcomes

## Intent (why this task exists)

`scry.cli`'s happy path always writes a final `Assertions:/Tests:` summary to
stdout via `write-summary!`. But the exception path in `run-cli`
(`catch Throwable`) returns `:summary nil` and writes only a `scry CLI error:
...` diagnostic to stderr — no stdout summary at all. The same is true for the
argument-error path, which also produces `:summary nil`.

This means any outcome that arrives via a thrown exception —
`:scry.cli/runner-error` (and the `:scry.cli/argument-error` raised before the
run) — produces **no final summary on stdout**. A user (or wrapping task)
watching stdout sees in-progress output (e.g. Kaocha-mode `.` progress) abruptly
end with no closing summary line, which reads as "the run silently stopped".

Note: `:scry.cli/load-error` is *not* one of these silent outcomes. The
catch-path `error-outcome-kind` only ever yields `:scry.cli/argument-error` or
`:scry.cli/runner-error`; `:scry.cli/load-error` is produced solely by
`classify-outcome` on the normal return path, which already calls
`write-summary!` (stdout summary) and `write-failure-diagnostic!` (stderr
detail). Load-error therefore already emits a stdout summary and is out of
scope here — adding output for it would duplicate the existing summary.

This gap became more visible after task 023, which deliberately reclassified
Kaocha-mode argument problems (typos, malformed/unknown options, unknown
selectors) from the clean `:scry.cli/argument-error` into thrown
`:scry.cli/runner-error` (and the normal-return `:scry.cli/load-error`). A
larger class of Kaocha invocations now reaches the no-summary exception path
via `:scry.cli/runner-error`.

(Field note: this surfaced while debugging a downstream project where an
in-process `System/exit` killed the JVM before any summary. That specific cause
was a project bug, but it highlighted that scry's own error path is also
silent on stdout, which makes such situations harder to diagnose.)

## Goal

On error/exception CLI outcomes, emit a minimal but explicit stdout summary so
the run is never silent on stdout. The authoritative machine signals
(`:scry.cli/outcome-kind`, exit code, `.scry-results/*.edn`) must remain
unchanged; this is supplementary human-facing output only.

Concretely, for outcomes where no real run summary is available because the run
threw or was rejected before producing a summary (`:scry.cli/runner-error` from
the `run-cli` catch path, and `:scry.cli/argument-error` from the
argument-error path), the CLI should still write a short, clearly-labelled
summary line to stdout — e.g. an explicit "no tests run" / error-outcome
summary naming the `outcome-kind` — rather than leaving `:summary nil` with
nothing on stdout.

`:scry.cli/load-error` is excluded: it already emits a stdout summary via the
normal return path (`write-summary!`), so it needs no new output here.

This change affects stdout text only; the returned outcome map's `:summary` key
stays `nil` for these error outcomes (see Context / constraints).

## Context / constraints

- Keep existing stdout summary text for successful pass/fail runs byte-stable.
- Do not change exit codes, `:scry.cli/outcome-kind` values, the
  `.scry-results/*.edn` contract, or the stderr `scry CLI error:` /
  `write-failure-diagnostic!` behavior.
- The minimal summary must be unambiguous that no normal test summary was
  produced (i.e. not look like a "0 passed, 0 failed" green run).
- This is a stdout-text-only change. The returned outcome map's `:summary` key
  stays `nil` for these error outcomes; do not populate it.
- Cover both the in-`run-cli` `catch` path (`:scry.cli/runner-error`) and the
  argument-error path in `main-outcome`. Do not add output for
  `:scry.cli/load-error` (already summarized on the normal return path).
- The deliberate `--help`/usage success path (exit 0, prints usage to stdout)
  is out of scope: it is not a `:summary nil` error outcome and must not emit
  the error-style "no tests run" minimal summary.
- Add CLI tests asserting a minimal stdout summary appears for at least one
  thrown outcome (e.g. Kaocha-mode malformed option → `:scry.cli/runner-error`)
  and for an argument-error outcome.

## Acceptance

- A Kaocha-mode run that fails with `:scry.cli/runner-error` (the thrown
  catch-path outcome) writes a minimal, clearly-labelled summary to stdout
  (in addition to the existing stderr diagnostic).
- The `:scry.cli/argument-error` outcome likewise produces a minimal stdout
  summary.
- The `--help`/usage success path is unchanged (it already prints usage and
  must not emit the error-style minimal summary).
- `:scry.cli/load-error` output is unchanged (it already emits a stdout summary
  via the normal return path; no new/duplicate output is added).
- Successful pass/fail summary output is unchanged.
- Exit codes, `:scry.cli/outcome-kind`, and `.scry-results` outputs unchanged.
- The returned outcome map's `:summary` key stays `nil` for these error
  outcomes (stdout-only change).
- `README.md` and `AGENTS.md` CLI output-contract descriptions are updated to
  document the always-emitted minimal stdout summary on the
  `:scry.cli/runner-error` and `:scry.cli/argument-error` outcomes, so the
  documented contract stays aligned with the implemented behavior.
- Focused `scry.cli-test` (and Kaocha CLI tests where relevant) cover the new
  behavior and pass.

## Out of scope

- Changing the task-023 reclassification of Kaocha-mode argument typos.
- Restructuring the result/outcome model or the structured `:summary` map shape
  for successful runs.
