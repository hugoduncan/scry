# Steps

## Slice 1 — helper + runner-error (catch) path

- [x] Add `write-error-summary!` helper in `src/scry/cli.clj` (near
      `write-summary!`/`write-failure-diagnostic!`) that writes a single
      clearly-labelled minimal summary line naming `outcome-kind` to
      `(:out boundary)` and flushes.
- [x] Choose minimal-summary wording that is unambiguous (not a "0 passed, 0
      failed" green run) and does not collide with success-summary text.
- [x] Call `write-error-summary!` from the `run-cli` `catch Throwable` branch
      using the computed `error-outcome-kind`, before/with the existing stderr
      `scry CLI error:` write.
- [x] Confirm returned outcome map `:summary` still stays `nil` in the catch
      branch (no change to the map).
- [x] Add a `test/scry/cli_kaocha_test.clj` assertion: a Kaocha-mode malformed
      option produces `:scry.cli/runner-error` and writes the minimal summary to
      stdout, with the stderr diagnostic still present.

## Slice 2 — argument-error paths

- [x] Wire `write-error-summary!` into the `-m` argument-error branch in
      `main-outcome` so `:scry.cli/argument-error` writes the minimal stdout
      summary (stderr diagnostic unchanged).
- [x] Wire the minimal stdout summary into the `-X` argument-error path
      (`argument-error-outcome` / `run-with-boundary`) so it is covered there
      too.
- [x] Verify each invocation path emits the minimal summary exactly once (no
      duplicate stdout line via `run-cli` catch + argument-error path).
- [x] Add `test/scry/cli_test.clj` assertion: an argument-error outcome writes a
      single minimal stdout summary line; returned `:summary` stays `nil`.

## Slice 3 — regression guards

- [x] Assert successful pass/fail stdout summary text is byte-stable (unchanged).
- [x] Assert `--help`/usage success path (exit 0) prints usage only and does not
      emit the error-style minimal summary.
- [x] Assert `:scry.cli/load-error` stdout output is unchanged (single summary
      via normal return path; no new/duplicate line).
- [x] Assert exit codes, `:scry.cli/outcome-kind`, and `.scry-results/*.edn`
      outputs are unchanged for all covered outcomes.

## Slice 4 — docs

- [x] Update `README.md` CLI output-contract section to document the always-
      emitted minimal stdout summary on `:scry.cli/runner-error` and
      `:scry.cli/argument-error`.
- [x] Update `AGENTS.md` CLI output-contract description to match.

## Slice 5 — final verification

- [x] Run focused core CLI tests:
      `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"`
- [x] Run focused Kaocha CLI tests:
      `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"`
- [x] Record commands and results in `implementation.md`.

## test-shaper review follow-ups

- [x] In `test/scry/cli_test.clj` (-X argument-error test, ~line 1336–1339),
      drop or simplify the redundant non-duplication assertion. The preceding
      exact-equality assertion on the whole `stdout` string already proves the
      summary line appears exactly once; the follow-up
      `(is (= 1 (count (filter #(= % "scry CLI error outcome") (re-seq ...)))))`
      adds no signal (the `filter` is a no-op over a literal-matching regex) and
      obscures intent.
- [x] Align the Kaocha runner-error stdout-summary assertion in
      `test/scry/cli_kaocha_test.clj` (~line 456) with the core `cli_test.clj`
      style: core tests assert the minimal summary with exact `=` (byte-stable
      contract), while the Kaocha test only uses `str/includes?`. Use exact
      equality if the runner-error path emits nothing else on stdout, or add a
      short comment justifying the looser substring match if progress output can
      precede the summary.
