# 020 — Surface Kaocha CLI load/suite errors on stderr

## Goal

When a test namespace fails to LOAD (compile/require error) under
`scry.cli --runner kaocha`, the user must get an inline signal of WHAT failed and
WHERE the detail lives. Today the CLI prints only the terse summary
(`Assertions: 0 ... 1 errored` / `Tests: 0 ... 1 errored`) with no diagnostic and no
pointer at `.scry-results/`, even though the exit code (1) and the full
`.scry-results/suite-error-1.edn` are already correct.

## Why

Raw `kaocha.runner` prints the full stacktrace + root cause; `scry.cli` swallows it
into a count. The bb wrapper in consuming projects reimplements a hint, but the fix
belongs in scry so it self-diagnoses regardless of caller.

## Root cause (two compounding spots)

1. `src-kaocha/scry/kaocha.clj` — `progress-reporter` only fires its callback on
   `:end-test-var`. A load error emits `:kaocha/begin-suite → :error →
   :kaocha/end-suite` with NO test-var events, so `current-var` stays nil and the
   callback never fires; not even the `suite-error-1` synthetic label is printed.
2. `src/scry/cli.clj` — `run-cli` classifies this as `:scry.cli/load-error` but then
   writes only `summary-text` to stdout via `write-summary!`. It never surfaces the
   classification or points at `.scry-results/`.

## Desired behaviour

When `run-cli`'s `outcome-kind` is a failure kind (`:scry.cli/load-error`,
`:scry.cli/test-failure`, `:scry.cli/unknown-result`):

- Write a short line to the boundary's stderr pointing at the results dir.
- For `:scry.cli/load-error` specifically, also include the failing entry's assertion
  `:message` and its root-cause class/message (from the synthetic entry's
  `:assertions[0]` → `:actual`, which is a live Throwable from the adapter, or an
  edn-ified `Throwable->map` shape with `:via`/`:cause`).
- Keep stdout summary unchanged (terse summary stays the contract).
- Preserve exit codes and `.scry-results/` file writing exactly as today.

Additionally, the Kaocha adapter `progress-reporter` must fire its callback for a
suite-level `:error` event (no enclosing test var) so the synthetic `suite-error-1`
progress label is printed during the run.

## Constraints

- Behaviour-preserving except for the new stderr output.
- Route all output through the existing `boundary` (`:out`/`:err`) — no direct
  `System/out`/`err` — so it stays testable via the nullable boundary.
- Do not change the result model, classification logic, or exit codes.

## Acceptance

- A synthetic `:scry.cli/load-error` driven through `run-cli` writes the pointer +
  cause to the stderr boundary, while stdout still receives only the summary.
- Existing `run-cli` synthetic-error test updated for the new stderr diagnostic.
- Kaocha adapter fires a progress callback for suite-level errors.
- Core + Kaocha CLI suites green; manual load-error run prints cause + results-dir
  hint to stderr, still exits 1, still writes `.scry-results/suite-error-1.edn`.
