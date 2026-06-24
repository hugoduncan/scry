# Plan: minimal CLI stdout summary on error/exception outcomes

## Approach

On the two genuinely-silent CLI error paths — `:scry.cli/runner-error` (the
`run-cli` `catch Throwable`) and `:scry.cli/argument-error` (the argument-error
path) — emit a short, clearly-labelled summary line to **stdout** before/at the
point where today only a stderr diagnostic (or nothing) is written. This mirrors
the existing `write-failure-diagnostic!` pattern: supplementary human-facing
output only, with all authoritative machine signals unchanged.

Key decisions:

- **New helper `write-error-summary!`** in `scry.cli`, alongside
  `write-summary!`/`write-failure-diagnostic!`, takes the boundary and the
  `outcome-kind` and writes a single explicit stdout line. The text must be
  unambiguous that no normal test summary was produced (not a "0 passed, 0
  failed" green run) and should name the `outcome-kind`. Suggested form:
  `No tests run — scry CLI error outcome: <outcome-kind>` (final wording chosen
  during implementation, bounded by Acceptance: "clearly-labelled", not a 0/0
  green run).

- **Call sites (stdout only):**
  - `run-cli` `catch Throwable` branch → call `write-error-summary!` with the
    computed `error-outcome-kind` (covers `:scry.cli/runner-error`; this branch
    can also yield `:scry.cli/argument-error` per `error-outcome-kind`).
  - The argument-error paths that bypass `run-cli`:
    - `argument-error-outcome` (used by the `-X` `run-with-boundary` path), and
    - `main-outcome`'s `catch` argument-error branch (the `-m` path).
    Ensure the stdout minimal summary is written for `:scry.cli/argument-error`
    in both the `-m` and `-X` entry paths. Centralize via the shared helper so
    the text is identical across paths.

- **Do not touch** the normal return path (`write-summary!` already runs there),
  so `:scry.cli/load-error` keeps its existing single stdout summary and gets no
  duplicate output.

- **Do not touch** the `--help`/usage success path in `main-outcome` (exit 0,
  already prints usage); it must not emit the error-style minimal summary.

- **`:summary` key stays `nil`** in the returned outcome maps for these error
  outcomes — this is a stdout-text-only change.

- **Docs:** update the CLI output-contract descriptions in `README.md` and
  `AGENTS.md` to document the always-emitted minimal stdout summary on
  `:scry.cli/runner-error` and `:scry.cli/argument-error`.

- **Tests:** extend `test/scry/cli_test.clj` for the argument-error stdout
  summary and `test/scry/cli_kaocha_test.clj` for a Kaocha-mode malformed-option
  `:scry.cli/runner-error` stdout summary. Add/keep assertions that successful
  pass/fail summary text, exit codes, outcome-kinds, `.scry-results`, and the
  `--help` path are unchanged.

## Risks

- **Double output / wrong path:** Adding a summary on the wrong path could
  duplicate the existing `write-summary!` output (esp. for load-error) or break
  byte-stable successful summary text. Mitigation: only touch the catch path and
  the argument-error paths; assert successful/load-error output unchanged.

- **Argument-error path duplication:** `argument-error` can surface via both the
  `-m` `main-outcome` catch and the `-X` `argument-error-outcome`/
  `run-with-boundary` path, plus via `run-cli`'s catch (`error-outcome-kind`
  maps it). Risk of emitting the minimal summary twice for one invocation.
  Mitigation: confirm each invocation flows through exactly one summary-writing
  site; add tests asserting a single stdout summary line.

- **Test capture of stdout vs stderr:** tests must assert on the correct stream;
  the diagnostic stays on stderr, the new summary is on stdout. Mitigation: use
  the existing boundary out/err capture helpers in the CLI tests.

- **Wording stability:** other tests may assert on exact stdout; choose wording
  that does not collide with success-summary assertions.

## Slice order

1. **Helper + runner-error path.** Add `write-error-summary!`; call it from
   `run-cli`'s `catch`. Add a Kaocha-mode `:scry.cli/runner-error` test
   asserting the stdout minimal summary (and stderr diagnostic still present).
2. **Argument-error paths.** Wire the helper into the `-m` (`main-outcome`) and
   `-X` (`argument-error-outcome`/`run-with-boundary`) argument-error paths. Add
   `cli-test` assertions for a single stdout minimal summary on
   `:scry.cli/argument-error`.
3. **Regression guards.** Assert success pass/fail summary, `--help`/usage,
   `:scry.cli/load-error` stdout, exit codes, outcome-kinds, and `.scry-results`
   are unchanged; assert returned `:summary` stays `nil` for error outcomes.
4. **Docs.** Update `README.md` and `AGENTS.md` CLI output-contract sections.
5. **Final verification.** Run focused `scry.cli-test` and `scry.cli-kaocha-test`
   command-line checks; record commands/results in `implementation.md`.
