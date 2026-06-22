# 021: Kaocha CLI positional suite selectors (`-m`)

## Goal

Simplify the scry `-m` CLI wrapper for Kaocha mode by removing the bespoke
`--suite`/`-s`/`--suites` flag parsing and instead accepting suite selectors as
trailing positional arguments, mirroring Kaocha's own CLI convention
(`bin/kaocha [OPTIONS]... [TEST-SUITE]...`). The selectors flow into the
existing `scry.kaocha/run` adapter via its current `:suite`/`:suites` options.

## Context

Today the `-m` path (`scry.cli/parse-main-args`) has dedicated suite handling:

- `--suite`/`-s VALUE` (repeatable) → `:suite-values` → collapsed by
  `main-opts->exec-opts` to `:suite` (one value) or `:suites` (many).
- `--suites EDN` → `:suites`.
- A `--suite`/`--suites` mutual-exclusion argument-error check.

These options feed scry's own adapter `scry.kaocha/run`, not Kaocha's
`kaocha.runner`. The adapter already "takes the suite as an optional argument"
via its `:suite`/`:suites` options, and performs its own selector resolution
(`suite-selectors` → `select-suites` → `resolve-suite-selector`): exact id match
first, then unique text match across string/keyword/symbol, throwing `ex-info`
on unknown or ambiguous selectors (task 002 behavior).

Kaocha's upstream CLI (`kaocha.runner`) takes suites as positional arguments,
not as a `--suite` flag. This task aligns scry's `-m` surface with that
convention while keeping scry's adapter-side selector semantics intact.

Decision (Interpretation A, confirmed with maintainer): keep the adapter and the
`-X` path unchanged; only reshape the `-m` wrapper. Do a clean removal of the
`--suite`/`-s`/`--suites` flags (no deprecated alias). The project is pre-1.0
(`0.1.x`); the CLI audience is agents driving it from maintainer-controlled docs.

## Approach

1. In `scry.cli/parse-main-args`, remove the `--suite`/`-s`, `--suites` flag
   branches and the `:suite-values`/mutual-exclusion machinery in
   `main-opts->exec-opts`.

2. Collect trailing non-flag tokens (positional arguments) during `-m` parsing
   as ordered suite selectors. To match the existing collapse semantics, a
   single positional token becomes `:suite <token>` and multiple tokens become
   `:suites [<tokens>...]`, so the values reaching `scry.kaocha/run` are
   identical to what the old flags produced.

3. Positional suite selectors are only meaningful in Kaocha mode. In
   `:clojure-test` mode, positional arguments must be rejected with
   `:scry.cli/argument-error` (core mode has no positional surface), preserving
   the structured error-classification contract.

4. Leave the `-X` map path (`:suite`/`:suites` keys) and `scry.kaocha/run`
   (its `:suite`/`:suites` options and fuzzy-match/validation) unchanged.

5. Update usage text in `scry.cli/usage` to document positional suite selectors
   and drop the `--suite`/`--suites` lines.

6. Update `cli_test.clj` Kaocha `-m` parsing tests to cover positional suites
   (single → `:suite`, multiple → `:suites`), and remove the obsolete
   `--suite`/`-s`/`--suites`/mutual-exclusion flag tests. Keep `--focus`,
   `--kaocha-opt`, and `--config` pass-through tests as-is.

7. Update `README.md`, `AGENTS.md`, and regenerate `doc/API.md` if the public
   surface text changes there.

## Constraints

- Core mode (`:clojure-test`) behavior must not change except that stray
  positional arguments are now an argument error (they were already rejected as
  unknown options when prefixed with `-`; bare positionals were previously
  unreachable in core mode and must remain an error).
- `-X` map path and `scry.kaocha/run` adapter API/semantics unchanged; task 002
  suite-selection tests must still pass.
- Preserve the CLI's `:scry.cli/outcome-kind` error-classification contract.
- `--focus`, `--kaocha-opt`, `--config` pass-through behavior unchanged.
- Keep README examples and AGENTS.md command snippets synchronized with the new
  positional form.

## Acceptance

- `clojure -M:test:kaocha -m scry.cli --runner kaocha unit` runs the `unit`
  suite (single positional → `:suite`).
- `clojure -M:test:kaocha -m scry.cli --runner kaocha unit integration` runs
  both suites (multiple positionals → `:suites`).
- `--suite`/`-s`/`--suites` are no longer accepted flags on `-m`.
- A positional argument in core mode produces `:scry.cli/argument-error`.
- `-X` `:suite`/`:suites` continue to work unchanged.
- `--focus`, `--kaocha-opt`, `--config` continue to work unchanged.
- Focused CLI tests cover positional suite parsing; obsolete flag tests removed.
- README/AGENTS/API docs reflect the positional form.

## Open Questions

- Ordering/interleaving: do we require positional suite tokens to appear only
  after flags, or accept them interleaved? Kaocha's tools.cli accepts them
  interleaved. Proposed default: accept any non-flag token as a positional
  regardless of position, since `parse-main-args` is a hand-rolled loop and
  flags always consume their own values explicitly.
