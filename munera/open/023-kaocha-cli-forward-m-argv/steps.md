# Steps

## Slice 0 — Spike: confirm Kaocha argv parse entry point (OQ2)

- [x] In a `:test:kaocha` REPL, inspect `kaocha.runner`'s `tools.cli` option spec
      and `kaocha.config/apply-cli-args` to find how raw argv → cli-options +
      positional suite ids.
- [x] Determine the concrete function(s) the adapter will call to parse a raw
      argv vector into (a) a cli-options map and (b) positional selectors.
- [x] Record the chosen parse approach (public fn vs minimal internal assembly)
      and any limitations in `implementation.md`.

## Slice 1 — Core `scry.cli` runner-aware `-m` forwarding

- [x] Add a pre-pass over argv that resolves the effective runner
      (`--runner`/`-r`, else default) before per-token parsing.
- [x] In Kaocha mode, parse scry-owned flags only (`--runner`/`-r`,
      `--help`/`-h`, `--result-format`, `--config`, `--dir`/`-d`) and collect all
      other tokens in original order into `:kaocha-argv` (vector of strings).
- [x] In Kaocha mode, keep core-only selectors (`--namespace`/`--ns`, `--var`,
      `--ns-pattern`) parsed-then-rejected with the existing clean
      `:scry.cli/argument-error` (do NOT forward them into `:kaocha-argv`).
- [x] Keep core mode (`--runner clojure-test`) behaviour identical: unknown
      `--flags` still raise `:scry.cli/argument-error`.
- [x] Remove the `--focus` and `--kaocha-opt` branches in `parse-main-args`.
- [x] Remove the `:suite-values` collection and the
      `main-opts->exec-opts` positional-suite → `:suite`/`:suites` collapse for
      Kaocha mode.
- [x] Thread `:kaocha-argv` through `normalize-exec-opts` so it reaches the
      adapter call as an opaque string vector.
- [x] Confirm core `scry.cli` still does not require `scry.kaocha` at load time.

## Slice 2 — Adapter `scry.kaocha` `:kaocha-argv` parsing

- [x] Add `:kaocha-argv` handling to `scry.kaocha/run`: parse the vector via the
      Slice 0 entry point into cli-options map + positional selectors.
- [x] Merge parsed cli-options through `apply-kaocha-extra`/`:kaocha/cli-options`,
      keeping explicit `:config` authoritative on conflict.
- [x] Route parsed positional selectors through existing `select-suites`
      (exact-id then unique text match) to preserve fuzzy resolution (OQ3).
- [x] Update the `run` docstring to document the new `:kaocha-argv` option.

## Slice 3 — Help text and docs

- [x] Update `usage-for :kaocha`: drop `--focus`/`--kaocha-opt`; describe raw
      forwarding and positional suite selectors.
- [x] Update `README.md` Kaocha `-m` usage and option surface.
- [x] Update `AGENTS.md` Kaocha CLI guidance to match new `-m` behaviour.
- [x] Update the Unreleased `CHANGELOG.md` entry (supersede the prior
      pass-through wording).
- [x] Regenerate `doc/API.md` via `bb api-docs` for the new `:kaocha-argv`
      option and verify with `bb api-docs --check`.

## Slice 4 — Tests

- [x] CLI test: `--runner kaocha --focus my.ns/test-foo` runs only the focused
      test via forwarding.
- [x] CLI/adapter test: a previously-unsupported Kaocha option (e.g.
      `--no-randomize` or another boolean/valued option) reaches Kaocha and
      affects the run.
- [x] CLI test: positional suite selectors still select suites
      (`--runner kaocha unit [integration ...]`), including fuzzy resolution.
- [x] CLI test: a malformed `-m` Kaocha option surfaces as
      `:scry.cli/runner-error` / `:scry.cli/load-error`, not argument-error.
- [x] CLI test: a core-only selector in Kaocha mode (`--runner kaocha
      --namespace my.ns`, and likewise `--var`/`--ns-pattern`) still yields a
      clean `:scry.cli/argument-error`, not a forward/runner-error.
- [x] Regression test: core mode unknown flag still yields
      `:scry.cli/argument-error`.
- [x] Confirm the `-X` path and programmatic adapter API behave exactly as
      before (no `:kaocha-argv` leakage into `-X`).

## Slice 5 — Final verification

- [x] Run focused CLI checks (`scry.cli-test`) command-line.
- [x] Run focused Kaocha CLI + adapter checks
      (`scry.cli-kaocha-test`, `scry.kaocha-test`) with `:kaocha`.
- [x] Run the acceptance commands from design.md
      (`--focus`, an unsupported option, positional suites, a typo).
- [x] Record all commands and results in `implementation.md`.

## Implementation review follow-up

- [x] `doc/API.md` is stale: `bb api-docs --check` fails because the committed
      `scry.kaocha/run` Source line range (`kaocha.clj#L372-L453`) no longer
      matches the committed source (actual `L384-L465`). Regenerate with
      `bb api-docs`, verify `bb api-docs --check` passes, and commit — Slice 3
      and Slice 5's API-doc verification are not actually satisfied.
- [x] `implementation.md` only documents Slice 0 and Slice 1; Slices 2–5 have no
      notes and Slice 5's "Record all commands and results" step is checked
      without any recorded final-verification commands/results. Append the
      adapter/test/docs implementation notes and the actual command-line
      verification commands+outcomes (AGENTS.md requires recording them).

## Test review follow-up

- [x] `:kaocha-argv` is in `scry-managed-keys` specifically to stop it leaking
      into `:kaocha-extra` on the `-X` path, but the
      `normalize-exec-opts-kaocha-pass-through-test` "full scry-managed closed
      set never leaks" subtest does not include `:kaocha-argv` in its input map,
      so that leak-prevention is untested. Add `:kaocha-argv` to that closed-set
      input and assert it neither lands in `:kaocha-extra` nor is otherwise
      forwarded (covers Slice 4's "no `:kaocha-argv` leakage into `-X`").
- [x] The "explicit `:config` is authoritative over forwarded options" constraint
      is only exercised for the `-X` `:kaocha-extra` path
      (`kaocha-extra-merge-config-authoritative-test` calls `apply-kaocha-extra`
      directly). No test conflicts an explicit `:config` cli-option with a
      `:kaocha-argv`-forwarded option to prove the parsed-argv path also defers
      to `:config`. Add an adapter test where `:config` and a forwarded
      `:kaocha-argv` option set the same cli-option and assert `:config` wins.
- [x] Positional-suite forwarding is asserted only with exact suite ids
      (`unit`/`integration`); OQ3's preserved unique-text/name fuzzy resolution
      on forwarded `-m` positionals is not exercised end-to-end. Add a forwarded
      positional case that resolves via the unique-text fallback (not an exact
      id) to lock in the claimed `select-suites` parity for the `:kaocha-argv`
      path.

## Code-shaper review follow-up

- [x] Stale comment in `src/scry/cli.clj` `normalize-kaocha-options` (the
      `collected` let-binding) still says "on `-m` the parser pre-builds a
      `:kaocha-extra` map (which is in `scry-managed-keys`, so it is not
      re-collected)". This task removed the `--kaocha-opt` branch, so the `-m`
      Kaocha path no longer produces `:kaocha-extra`; it now forwards
      `:kaocha-argv`. Update the comment to describe current behaviour (`-X`
      scatters top-level extras; `-m` forwards opaque `:kaocha-argv`, parsed in
      the adapter) so the reader is not misled.

## Code-shaper review follow-up (2nd pass)

- [x] `parse-kaocha-argv` unconditionally `(dissoc options :config-file)`, but
      `clojure.tools.cli` always populates `:config-file` (with the `"tests.edn"`
      default *and* any explicitly forwarded `--config-file PATH`). So a user
      forwarding `--runner kaocha --config-file ci-tests.edn` on `-m` has that
      path silently discarded: `resolve-config` already picked the config from
      defaults/`:config`/`tests.edn`, and the forwarded value never reaches it.
      This silently diverges from the drop-in intent and contradicts plan.md
      OQ1 ("Kaocha's own `--config-file` etc. simply forward"). Decide and
      implement one of: (a) honor a forwarded `--config-file` by loading that
      config before suite/extra resolution, or (b) explicitly reject/document a
      forwarded `--config-file` in Kaocha mode (since scry owns `--config`/`--dir`)
      rather than dropping it silently. Only the parser-injected *default* should
      be dropped — distinguish it from an explicit value (e.g. compare against the
      default or detect presence in argv). Add a test for the chosen behaviour.

## Test review follow-up (test-shaper)

- [x] `kaocha-argv-forwarded-config-authoritative-test` proves the "explicit
      `:config` wins over a forwarded `:kaocha-argv` option" constraint only by
      hand-reconstructing `run`'s pipeline (calling private `parse-kaocha-argv`
      then `apply-kaocha-extra` directly), asserting an implementation
      composition rather than an observable `run` outcome. It can stay green even
      if `run` stops composing parse→merge in that order. Add an end-to-end case
      through `kaocha-run` (as `kaocha-argv-forwarded-focus-filters-execution-test`
      already does) where a resolved `:config` cli-option and a conflicting
      forwarded `:kaocha-argv` option set the same key, asserting the `:config`
      value governs the actual run.
- [x] `kaocha-argv-forwarded-positional-unique-text-fallback-test` likewise
      reconstructs the path by calling private `parse-kaocha-argv` then
      `select-suites` directly instead of routing through `run`. Prove the OQ3
      unique-text positional parity for the real `:kaocha-argv` path by asserting
      suite selection via a `kaocha-run`/CLI invocation with a non-exact
      positional, so the test breaks if `run`'s positional threading regresses.
