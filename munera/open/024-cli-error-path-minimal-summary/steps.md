# Steps

## Slice 1 — helper + runner-error (catch) path

- [ ] Add `write-error-summary!` helper in `src/scry/cli.clj` (near
      `write-summary!`/`write-failure-diagnostic!`) that writes a single
      clearly-labelled minimal summary line naming `outcome-kind` to
      `(:out boundary)` and flushes.
- [ ] Choose minimal-summary wording that is unambiguous (not a "0 passed, 0
      failed" green run) and does not collide with success-summary text.
- [ ] Call `write-error-summary!` from the `run-cli` `catch Throwable` branch
      using the computed `error-outcome-kind`, before/with the existing stderr
      `scry CLI error:` write.
- [ ] Confirm returned outcome map `:summary` still stays `nil` in the catch
      branch (no change to the map).
- [ ] Add a `test/scry/cli_kaocha_test.clj` assertion: a Kaocha-mode malformed
      option produces `:scry.cli/runner-error` and writes the minimal summary to
      stdout, with the stderr diagnostic still present.

## Slice 2 — argument-error paths

- [ ] Wire `write-error-summary!` into the `-m` argument-error branch in
      `main-outcome` so `:scry.cli/argument-error` writes the minimal stdout
      summary (stderr diagnostic unchanged).
- [ ] Wire the minimal stdout summary into the `-X` argument-error path
      (`argument-error-outcome` / `run-with-boundary`) so it is covered there
      too.
- [ ] Verify each invocation path emits the minimal summary exactly once (no
      duplicate stdout line via `run-cli` catch + argument-error path).
- [ ] Add `test/scry/cli_test.clj` assertion: an argument-error outcome writes a
      single minimal stdout summary line; returned `:summary` stays `nil`.

## Slice 3 — regression guards

- [ ] Assert successful pass/fail stdout summary text is byte-stable (unchanged).
- [ ] Assert `--help`/usage success path (exit 0) prints usage only and does not
      emit the error-style minimal summary.
- [ ] Assert `:scry.cli/load-error` stdout output is unchanged (single summary
      via normal return path; no new/duplicate line).
- [ ] Assert exit codes, `:scry.cli/outcome-kind`, and `.scry-results/*.edn`
      outputs are unchanged for all covered outcomes.

## Slice 4 — docs

- [ ] Update `README.md` CLI output-contract section to document the always-
      emitted minimal stdout summary on `:scry.cli/runner-error` and
      `:scry.cli/argument-error`.
- [ ] Update `AGENTS.md` CLI output-contract description to match.

## Slice 5 — final verification

- [ ] Run focused core CLI tests:
      `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"`
- [ ] Run focused Kaocha CLI tests:
      `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"`
- [ ] Record commands and results in `implementation.md`.
