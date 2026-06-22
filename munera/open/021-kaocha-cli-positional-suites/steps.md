# 021 Steps

## Slice 1 — Parser, collapse, usage (`src/scry/cli.clj`)

- [ ] Remove the `("--suite" "-s")` flag branch (and its `--suites`
      mutual-exclusion check) from `parse-main-args`.
- [ ] Remove the `"--suites"` flag branch (and its `:suite-values`
      mutual-exclusion check) from `parse-main-args`.
- [ ] Change the `parse-main-args` loop `default` case so a token starting with
      `-` still raises `:scry.cli/argument-error` ("Unknown option: ...").
- [ ] In the same `default` case, collect any token not starting with `-` as an
      ordered positional suite selector into the accumulator that
      `main-opts->exec-opts` collapses.
- [ ] Confirm `main-opts->exec-opts` still collapses a single accumulated
      selector to `:suite <token>` and multiple to `:suites [<tokens>...]`, and
      remove only the now-dead flag-specific mutual-exclusion machinery.
- [ ] Update the `usage` def: drop the `-s, --suite SUITE` and `--suites EDN`
      lines; add a line documenting positional suite selectors for Kaocha mode.
- [ ] REPL-check parsing during development: `(#'cli/parse-main-args
      ["--runner" "kaocha" "unit"])` → `:suite "unit"`; `[... "unit"
      "integration"]` → `:suites ["unit" "integration"]`; verify a `-`-prefixed
      unknown token still errors.

## Slice 2 — Tests

- [ ] In `test/scry/cli_test.clj`, remove the "accepted repeated Kaocha suite
      flags", "accepted Kaocha short suite flags", and `--suite`/`--suites`
      mutual-exclusion parser-error tests.
- [ ] Keep the `--suites`/`--config` EDN test only for `--config`; replace the
      `--suites` portion with positional coverage (single positional → `:suite`,
      multiple positionals → `:suites`).
- [ ] Add a positional-selector test: `--runner kaocha unit` →
      `(:suite opts) = "unit"`.
- [ ] Add a positional-selector test: `--runner kaocha unit integration` →
      `(:suites opts) = ["unit" "integration"]`.
- [ ] Add a position-agnostic test: selectors interleaved with flags (e.g.
      `--runner kaocha unit --focus my.ns/test-foo integration`) collect both
      selectors and the focus pass-through.
- [ ] Add a core-mode positional rejection test: `--runner clojure-test foo`
      yields `:scry.cli/argument-error` (assert outcome-kind / argument-error?,
      not the old "Unknown option" text).
- [ ] Keep `--focus`, repeated `--focus`, `--kaocha-opt`, repeated
      `--kaocha-opt`, and `--focus rejected in core mode` tests unchanged.
- [ ] In `test/scry/cli_kaocha_test.clj`, add/adjust an end-to-end test proving
      `--runner kaocha <suite>` positional selection runs the selected suite
      (single → `:suite`) and, where covered, multiple positionals (→ `:suites`).
- [ ] REPL-run updated focused tests during iteration via `scry.core/run` on
      `scry.cli-test` (core) and `scry.cli-kaocha-test` (`:test:kaocha` REPL).

## Slice 3 — Docs sync

- [ ] Update `README.md` Kaocha CLI snippets (around the "Run Kaocha support by
      composing the aliases" and command-line usage sections) from
      `--runner kaocha --suite unit` / `--suite unit --suite integration` to the
      positional form `--runner kaocha unit` / `--runner kaocha unit integration`.
      Leave `-X` `:suite`/`:suites` examples unchanged.
- [ ] Update `AGENTS.md` Kaocha CLI command snippets to the positional `-m` form;
      leave `-X` and adapter REPL `:suite`/`:suites` text unchanged.
- [ ] Regenerate `doc/API.md` with `bb api-docs` and verify the CLI
      `--runner kaocha ...` example text reflects the positional form.

## Slice 4 — Final command-line verification (record in implementation.md)

- [ ] Run focused core CLI tests:
      `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"`.
- [ ] Run focused Kaocha CLI tests:
      `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"`.
- [ ] Run at least one real positional `-m` invocation, e.g.
      `clojure -M:test:kaocha -m scry.cli --runner kaocha unit` (and
      `... unit integration`) against an available Kaocha suite, confirming the
      selected suite(s) run.
- [ ] Verify docs: `bb api-docs --check` and the API-doc content regression
      `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"`.
- [ ] Run `bb clj-fmt:check` and `bb clj-kondo:lint`.
- [ ] Record all verification commands and results in `implementation.md`.
