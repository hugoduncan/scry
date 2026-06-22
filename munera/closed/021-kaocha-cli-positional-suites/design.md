# 021: Kaocha CLI positional suite selectors (`-m`)

## Goal

Simplify the scry `-m` CLI wrapper for Kaocha mode by removing the bespoke
`--suite`/`-s`/`--suites` flag parsing and instead accepting suite selectors as
positional arguments, mirroring Kaocha's own CLI convention
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

2. Collect non-flag tokens (positional arguments) during `-m` parsing as ordered
   suite selectors, regardless of their position relative to flags. The parser's
   positional-vs-token discrimination rule is: a token that starts with `-` and
   is not a recognized flag remains an "Unknown option" argument error, while any
   token that does not start with `-` is collected as an ordered positional suite
   selector. To match the existing collapse semantics, a single positional token
   becomes `:suite <token>` and multiple tokens become `:suites [<tokens>...]`,
   so the values reaching `scry.kaocha/run` are identical to what the old flags
   produced.

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
   `--suite`/`-s`/`--suites`/mutual-exclusion flag tests. Keep `--focus` and
   `--kaocha-opt` pass-through tests as-is. `--config` *coverage* is preserved,
   but its test is not left unchanged: the only `--config` parse test is the
   fused "accepted Kaocha suites and config EDN flags" case, which also
   exercises the now-removed `--suites` flag, so it is split/edited to drop the
   `--suites` portion and keep `--config` (matching plan step 5 / steps
   Slice 2).

7. Update `README.md`, `AGENTS.md`, and regenerate `doc/API.md` if the public
   surface text changes there.

## Constraints

- Core mode (`:clojure-test`) behavior must not change. In particular, a stray
  positional argument in core mode remains a `:scry.cli/argument-error`, exactly
  as today: `parse-main-args`'s `default` branch already rejects any unrecognized
  token (including bare, non-`-` tokens) as an "Unknown option" argument error at
  parse time, before runner mode is resolved in `normalize-exec-opts`. The only
  delta is the rejection mechanism/message, not the error-ness or the
  `:scry.cli/outcome-kind` contract: after this change a non-`-` token is no
  longer rejected at parse time, but collapses to `:suite`/`:suites` in
  `main-opts->exec-opts` and is then rejected at normalize time by the existing
  `kaocha-only-keys`/`reject-keys` path ("Kaocha options require :runner
  :kaocha"). The outcome remains `:scry.cli/argument-error` in core mode.
- Positional suite selectors are accepted position-agnostically: any non-`-`
  token is collected as a selector regardless of where it appears relative to
  flags (flags always consume their own values explicitly in the hand-rolled
  parse loop).
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
