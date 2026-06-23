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
