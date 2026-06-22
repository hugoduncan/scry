# 021 Plan: Kaocha CLI positional suite selectors (`-m`)

Scope is frozen to design.md Interpretation A (maintainer-confirmed): reshape
only the `-m` wrapper. The `-X` map path (`:suite`/`:suites` keys) and the
`scry.kaocha/run` adapter (its `:suite`/`:suites` options and
fuzzy-match/validation) stay unchanged. Clean removal of `--suite`/`-s`/`--suites`
flags (no deprecated alias).

## Approach

The CLI pipeline is: `parse-main-args` (hand-rolled `-m` loop) →
`main-opts->exec-opts` (collapse) → `normalize-exec-opts` (mode resolution +
validation) → execute. The whole change is confined to the `-m` parse and
collapse stages plus usage text; downstream normalization and the adapter are
reused unchanged.

Key decisions:

1. **Parser discrimination rule (`parse-main-args`).** Remove the
   `("--suite" "-s")` and `"--suites"` flag branches, including their
   mutual-exclusion argument-error checks. Change the loop's `default` case so
   that:
   - a token starting with `-` that is not a recognized flag still raises an
     `:scry.cli/argument-error` ("Unknown option: ..."), and
   - any token not starting with `-` is collected, in order, as a positional
     suite selector.
   Selectors are accepted position-agnostically: flags explicitly consume their
   own values in the loop, so any remaining non-`-` token is a selector
   regardless of where it sits relative to flags.

2. **Reuse the existing collapse pathway.** Continue collecting positional
   selectors into the same accumulator that feeds `main-opts->exec-opts`'s
   collapse, so a single selector becomes `:suite <token>` and multiple
   selectors become `:suites [<tokens>...]`. The values reaching
   `scry.kaocha/run` are byte-for-byte what the old flags produced. Drop only the
   flag-specific mutual-exclusion machinery; keep the count-based
   single→`:suite` / many→`:suites` collapse.

3. **Core-mode rejection via the existing path (no parallel branch).** Do not add
   a positional-specific core-mode check. Positionals collapse to
   `:suite`/`:suites`, which are in `kaocha-only-keys`; in `:clojure-test` mode
   `normalize-core-options`'s `reject-keys` already rejects them as
   `:scry.cli/argument-error` ("Kaocha options require :runner :kaocha"). The
   outcome-kind contract is preserved; only the rejection message/mechanism moves
   from parse-time "Unknown option" to normalize-time.

4. **Usage text.** Remove the `-s, --suite` and `--suites` lines from `usage`;
   document positional suite selectors for Kaocha mode.

5. **Tests.** Replace the obsolete `--suite`/`-s`/`--suites`/mutual-exclusion
   parse tests in `cli_test.clj` with positional-selector tests (single →
   `:suite`, multiple → `:suites`, position-agnostic interleaving with flags) and
   a core-mode positional rejection test that asserts the
   `:scry.cli/argument-error` outcome-kind (not the old "Unknown option" text).
   Add/adjust an end-to-end `cli_kaocha_test.clj` case proving positional
   selectors run the selected suite(s). Keep `--focus`, `--kaocha-opt`,
   `--config` tests unchanged.

6. **Docs.** Update `README.md` and `AGENTS.md` Kaocha CLI snippets to the
   positional form, and regenerate `doc/API.md` (`bb api-docs`) if the public
   surface text there changes.

## Risks

- **Collapse-key coupling.** `main-opts->exec-opts` currently keys on
  `:suite-values`. If the accumulator key or collapse is changed carelessly, the
  `-X` path or downstream normalization could be affected. Mitigation: keep the
  collapse logic and its output keys (`:suite`/`:suites`) identical; only the
  source of accumulated values changes.
- **Accidental scope creep into `-X`/adapter.** Mitigation: touch only
  `parse-main-args`, `main-opts->exec-opts`, and `usage`; leave
  `normalize-kaocha-options`, `normalize-core-options`, and `scry.kaocha`
  untouched. Task 002 suite-selection tests must still pass.
- **Position-agnostic acceptance swallowing a typo.** A mistyped non-`-` token
  becomes a suite selector and surfaces later as an adapter unknown-selector
  error rather than a parse error. This is the accepted, documented behavior per
  design; no mitigation needed beyond docs.
- **Docs drift.** README/AGENTS/API examples must all move to the positional
  form together; `bb api-docs --check` and the API-doc regression test guard
  `doc/API.md`.

## Slice order

1. **Parser + collapse + usage** — `parse-main-args` discrimination rule, remove
   flag branches/mutual-exclusion, retain collapse, update `usage`. Validate via
   REPL-driven `scry` runs of `scry.cli-test` parse tests as they are updated.
2. **Tests** — rewrite obsolete flag parse tests as positional tests, add
   core-mode positional rejection test, add/adjust end-to-end Kaocha CLI
   positional run test.
3. **Docs sync** — README, AGENTS, regenerate `doc/API.md`.
4. **Final command-line verification** — focused core CLI tests
   (`scry.cli-test`), Kaocha CLI tests (`scry.cli-kaocha-test`, `:kaocha`
   alias), `bb api-docs --check` + API-doc regression, fmt/lint; record commands
   and results in `implementation.md`.
