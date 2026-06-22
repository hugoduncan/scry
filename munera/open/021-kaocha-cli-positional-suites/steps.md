# 021 Steps

## Slice 1 — Parser, collapse, usage (`src/scry/cli.clj`)

- [x] Remove the `("--suite" "-s")` flag branch (and its `--suites`
      mutual-exclusion check) from `parse-main-args`.
- [x] Remove the `"--suites"` flag branch (and its `:suite-values`
      mutual-exclusion check) from `parse-main-args`.
- [x] Change the `parse-main-args` loop `default` case so a token starting with
      `-` still raises `:scry.cli/argument-error` ("Unknown option: ...").
- [x] In the same `default` case, collect any token not starting with `-` as an
      ordered positional suite selector into the accumulator that
      `main-opts->exec-opts` collapses.
- [x] Confirm `main-opts->exec-opts` still collapses a single accumulated
      selector to `:suite <token>` and multiple to `:suites [<tokens>...]`, and
      remove only the now-dead flag-specific mutual-exclusion machinery.
- [x] Update the `usage` def: drop the `-s, --suite SUITE` and `--suites EDN`
      lines; add a line documenting positional suite selectors for Kaocha mode.
- [x] REPL-check parsing during development: `(#'cli/parse-main-args
      ["--runner" "kaocha" "unit"])` → `:suite "unit"`; `[... "unit"
      "integration"]` → `:suites ["unit" "integration"]`; verify a `-`-prefixed
      unknown token still errors.

## Slice 2 — Tests

- [x] In `test/scry/cli_test.clj`, remove the "accepted repeated Kaocha suite
      flags", "accepted Kaocha short suite flags", and `--suite`/`--suites`
      mutual-exclusion parser-error tests.
- [x] Keep the `--suites`/`--config` EDN test only for `--config`; replace the
      `--suites` portion with positional coverage (single positional → `:suite`,
      multiple positionals → `:suites`).
- [x] Add a positional-selector test: `--runner kaocha unit` →
      `(:suite opts) = "unit"`.
- [x] Add a positional-selector test: `--runner kaocha unit integration` →
      `(:suites opts) = ["unit" "integration"]`.
- [x] Add a position-agnostic test: selectors interleaved with flags (e.g.
      `--runner kaocha unit --focus my.ns/test-foo integration`) collect both
      selectors and the focus pass-through.
- [x] Add a core-mode positional rejection test: `--runner clojure-test foo`
      yields `:scry.cli/argument-error` (assert outcome-kind / argument-error?,
      not the old "Unknown option" text).
- [x] Keep `--focus`, repeated `--focus`, `--kaocha-opt`, repeated
      `--kaocha-opt`, and `--focus rejected in core mode` tests unchanged.
- [x] In `test/scry/cli_kaocha_test.clj`, add/adjust an end-to-end test proving
      `--runner kaocha <suite>` positional selection runs the selected suite
      (single → `:suite`) and, where covered, multiple positionals (→ `:suites`).
- [x] REPL-run updated focused tests during iteration via `scry.core/run` on
      `scry.cli-test` (core) and `scry.cli-kaocha-test` (`:test:kaocha` REPL).

## Test-review follow-up

- [x] Add a focused regression test in `test/scry/cli_test.clj`
      (`parse-main-args-test`) asserting the clean-removal acceptance criterion:
      `--suite unit`, `-s unit`, and `--suites "[:unit]"` now each raise
      `:scry.cli/argument-error` ("Unknown option: ...") on `-m`. This behaviour
      is currently only manually verified (implementation.md) and has zero
      automated coverage (`grep` finds no `--suite`/`-s`/`--suites` reference in
      `test/`); the generic `--unknown` test exercises the discrimination rule
      but does not guard against re-introducing these specific flags or a
      discrimination-rule regression that would re-accept them.

## Slice 3 — Docs sync

- [x] Update `README.md` Kaocha CLI snippets (around the "Run Kaocha support by
      composing the aliases" and command-line usage sections) from
      `--runner kaocha --suite unit` / `--suite unit --suite integration` to the
      positional form `--runner kaocha unit` / `--runner kaocha unit integration`.
      Leave `-X` `:suite`/`:suites` examples unchanged.
- [x] Update `AGENTS.md` Kaocha CLI command snippets to the positional `-m` form;
      leave `-X` and adapter REPL `:suite`/`:suites` text unchanged.
- [x] Edit the curated Kaocha `-m` example in `bb/scry/api_docs.clj` (the
      `intro` string, ~line 72:
      `clojure -M:test:kaocha -m scry.cli --runner kaocha --suite unit`) to the
      positional form `clojure -M:test:kaocha -m scry.cli --runner kaocha unit`.
      This example is curated prose, not derived from a docstring, so
      regeneration alone re-emits the stale text and the doc gates won't flag it.
      Leave the `-X` example `:runner :kaocha :suite :unit` in the same string
      unchanged. (Flag, but do not edit, the out-of-scope stale `--suite unit`
      form at `SKILL.md:159`.)
- [x] Regenerate `doc/API.md` with `bb api-docs` and verify the CLI
      `--runner kaocha ...` example text reflects the positional form.

## Docs-review follow-up

- [x] Record this user-visible CLI change in `CHANGELOG.md` under `##
      Unreleased`: the `-m` Kaocha mode now takes suite selectors as trailing
      positional arguments (`--runner kaocha unit [integration ...]`), and the
      `--suite`/`-s`/`--suites` flags are removed (clean removal, no alias). The
      `-X` `:suite`/`:suites` keys and the `scry.kaocha/run` adapter are
      unchanged. This is a user-facing breaking change to the `-m` surface and
      is not currently reflected in the Unreleased section (which only describes
      the earlier `--focus`/`--kaocha-opt` pass-through work); the
      review-task-docs checklist requires user-visible flag/command changes to
      be logged in `CHANGELOG.md`.

## Slice 4 — Final command-line verification (record in implementation.md)

- [x] Run focused core CLI tests:
      `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"`.
- [x] Run focused Kaocha CLI tests:
      `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"`.
- [x] Run at least one real positional `-m` invocation, e.g.
      `clojure -M:test:kaocha -m scry.cli --runner kaocha unit` (and
      `... unit integration`) against an available Kaocha suite, confirming the
      selected suite(s) run.
- [x] Verify docs: `bb api-docs --check` and the API-doc content regression
      `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"`.
- [x] Run `bb clj-fmt:check` and `bb clj-kondo:lint`.
- [x] Record all verification commands and results in `implementation.md`.
