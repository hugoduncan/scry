# 019: Steps — Kaocha CLI --focus and pass-through options

## Slice 1 — Core `-X` pass-through collection (`scry.cli`)

- [ ] Add a derived `scry-managed-keys` set in `src/scry/cli.clj` combining
  `:runner`, `core-only-keys`, `:result-format`, `:progress-callback`, and the
  explicit Kaocha keys (`:suite :suites :config :dirs :source-paths :test-paths
  :ns-patterns`); define it from existing key-set vars so it stays in sync.
- [ ] In `normalize-kaocha-options`, after the existing `cond->`, collect all
  `opts` keys not in `scry-managed-keys` into a `:kaocha-extra` map and
  `assoc` it onto `normalized` only when non-empty.
- [ ] Confirm `reject-keys` for `core-only-keys` still runs first (core
  selectors remain rejected, not forwarded).
- [ ] In `test/scry/cli_test.clj`, add a test: `-X` opts `{:runner :kaocha
  :focus "my.ns/test-foo"}` normalize to include `:kaocha-extra {:focus
  "my.ns/test-foo"}`.
- [ ] Add a test asserting no scry-managed key (`:runner`, `:result-format`,
  `:suite`, `:dirs`, etc.) ever appears under `:kaocha-extra`.
- [ ] Add a test that `:kaocha-extra` is absent when no extra keys are supplied.
- [ ] Run focused `scry.cli-test` in the REPL/`:test` slice; confirm green.

## Slice 2 — Core `-m` opt-in flags (`scry.cli`)

- [ ] In `parse-main-args`, add a `--focus` flag clause that reads a required
  value and accumulates it into raw `:kaocha-extra` under `:focus` (support
  repetition, mirroring `add-repeat`).
- [ ] Add a generic `--kaocha-opt` flag clause reading KEY then VALUE (two
  `require-value` reads), associng `(keyword KEY) -> VALUE` (raw string) into
  raw `:kaocha-extra`.
- [ ] Ensure the unknown-flag default branch still throws `argument-error`
  (`:scry.cli/argument-error`) for any flag outside the opt-in surface.
- [ ] Carry `:kaocha-extra` through `main-opts->exec-opts` (do not strip it) so
  it reaches `normalize-exec-opts`/`normalize-kaocha-options` intact.
- [ ] Decide and implement how `-m` `:kaocha-extra` survives
  `normalize-kaocha-options` without being treated as an unknown top-level key:
  pass it through unchanged (it is already a single collected map, not the
  scattered top-level keys of the `-X` path).
- [ ] Update CLI `usage` text to document `--focus SYM` and
  `--kaocha-opt KEY VALUE` (Kaocha mode only).
- [ ] In `test/scry/cli_test.clj`, add tests: `--runner kaocha --focus
  my.ns/test-foo` parses to opts with `:kaocha-extra {:focus ["my.ns/test-foo"]}`
  (or chosen scalar/coll shape); `--kaocha-opt foo bar` → `:kaocha-extra {:foo
  "bar"}`; an unrecognized flag still raises `:scry.cli/argument-error`.
- [ ] Confirm `--focus`/`--kaocha-opt` used in `:clojure-test` mode are rejected
  (they are Kaocha-only) — add/confirm a test.
- [ ] Run focused `scry.cli-test`; confirm green.

## Slice 3 — Adapter merge + `:focus` coercion (`scry.kaocha/run`, src-kaocha)

- [ ] Add a private coercion helper in `src-kaocha/scry/kaocha.clj` that
  coerces `:focus` raw values (string/symbol/keyword, scalar or collection)
  into a vector of keywords matching the filter plugin's parse semantics.
- [ ] Add a private merge helper that injects coerced `:kaocha-extra` into the
  resolved config under `:kaocha/cli-options`, with existing `:config`/config
  values authoritative on conflict (OQ2 merge-with-config-wins).
- [ ] Wire the merge into the `run` pipeline after `resolve-config` (and
  relative to `apply-runtime-defaults`) without disturbing `select-suites`,
  reporter, or capture-output behaviour.
- [ ] Verify the `:kaocha.plugin/filter` default plugin is present in the
  plugin chain for all three config paths (explicit `:config`, tests.edn,
  synthetic fallback); if missing on any path, ensure it like
  `apply-runtime-defaults` ensures `capture-output`.
- [ ] Update the `scry.kaocha/run` docstring to document the `:kaocha-extra`
  option (raw forwarded Kaocha cli-options; `:config` authoritative; `:focus`
  coercion) and the `-X` mistyped-key trade-off.
- [ ] In `test/scry/kaocha_test.clj`, add a test that `:kaocha-extra {:focus
  ...}` actually filters execution to the focused var(s) (assert reduced
  executed set, not just key presence).
- [ ] Add a test that explicit `:config` keys win over conflicting
  `:kaocha-extra` keys, and non-conflicting pass-through keys still apply.
- [ ] Run focused `scry.kaocha-test` with the `:kaocha` alias; confirm green.

## Slice 4 — CLI Kaocha integration + acceptance

- [ ] In `test/scry/cli_kaocha_test.clj`, add a test exercising the `-m`
  `--runner kaocha --focus my.ns/test-foo` path end-to-end (focused run).
- [ ] Add a test exercising the `-X` `:runner :kaocha :focus "my.ns/test-foo"`
  path end-to-end.
- [ ] Add/confirm a test that existing Kaocha options (`--suite`, `--config`,
  `--dirs`) still behave as before.
- [ ] Confirm `:clojure-test` (core) mode results are unchanged by the new code
  (no `:kaocha-extra` collection effect in core normalization path).
- [ ] Run focused `scry.cli-kaocha-test` with the `:kaocha` alias; confirm green.

## Slice 5 — Docs

- [ ] Run `bb api-docs` to regenerate `doc/API.md` for the updated
  `scry.kaocha/run` docstring.
- [ ] Run `bb api-docs --check` and the focused api-docs regression test
  (`scry.api-docs-test`) with the `:quickdoc:quickdoc-test:kaocha` aliases.
- [ ] Update `README.md`: document the `--focus` and `--kaocha-opt` CLI options,
  `-X` top-level pass-through, and the `-X` mistyped-key trade-off.
- [ ] Verify README examples stay consistent with the actual CLI/option surface.

## Slice 6 — Final command-line verification

- [ ] Run the core command-line CLI checks (`scry.cli-test`).
- [ ] Run the optional Kaocha CLI command-line checks
  (`scry.cli-kaocha-test`, `scry.kaocha-test`) with `:kaocha`.
- [ ] Run at least one real acceptance command:
  `clojure -M:test:kaocha -m scry.cli --runner kaocha --focus <var>` and/or
  `clojure -X:test:kaocha scry.cli/run :runner :kaocha :focus "<var>"`.
- [ ] Run `bb clj-fmt:check` and `bb clj-kondo:lint`.
- [ ] Record the commands run and results in `implementation.md`.
