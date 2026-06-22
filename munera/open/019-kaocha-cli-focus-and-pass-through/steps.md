# 019: Steps — Kaocha CLI --focus and pass-through options

## Slice 1 — Core `-X` pass-through collection (`scry.cli`)

- [x] Add a derived `scry-managed-keys` set in `src/scry/cli.clj` combining
  `:runner`, `core-only-keys`, `:result-format`, `:progress-callback`,
  `:kaocha-extra`, and the explicit Kaocha keys (`:suite :suites :config :dirs
  :source-paths :test-paths :ns-patterns`); define it from existing key-set vars
  so it stays in sync. Include `:kaocha-extra` so the `-X` collection step never
  re-collects an already-present `:kaocha-extra` map (e.g. one carried in from
  the `-m` path) into a nested `:kaocha-extra`.
- [x] In `normalize-kaocha-options`, after the existing `cond->`, collect all
  `opts` keys not in `scry-managed-keys` into a map, `merge` it into any
  pre-existing top-level `:kaocha-extra` map (collected top-level extras win on
  conflict), and `assoc` the combined `:kaocha-extra` onto `normalized` only when
  non-empty.
- [x] Confirm `reject-keys` for `core-only-keys` still runs first (core
  selectors remain rejected, not forwarded).
- [x] In `test/scry/cli_test.clj`, add a test: `-X` opts `{:runner :kaocha
  :focus "my.ns/test-foo"}` normalize to include `:kaocha-extra {:focus
  "my.ns/test-foo"}`.
- [x] Add a test asserting no scry-managed key (`:runner`, `:result-format`,
  `:suite`, `:dirs`, etc.) ever appears under `:kaocha-extra`.
- [x] Add a test that a pre-existing top-level `:kaocha-extra` map (the `-m`
  shape) survives `normalize-kaocha-options` unchanged — not nested under a
  second `:kaocha-extra` — and that scattered top-level extras merge into it.
- [x] Add a test that `:kaocha-extra` is absent when no extra keys are supplied.
- [x] Run focused `scry.cli-test` in the REPL/`:test` slice; confirm green.

## Slice 2 — Core `-m` opt-in flags (`scry.cli`)

- [x] In `parse-main-args`, add a `--focus` flag clause that reads a required
  value and accumulates it into raw `:kaocha-extra` under `:focus` (support
  repetition, mirroring `add-repeat`).
- [x] Add a generic `--kaocha-opt` flag clause reading KEY then VALUE (two
  `require-value` reads), associng `(keyword KEY) -> VALUE` (raw string) into
  raw `:kaocha-extra`.
- [x] Ensure the unknown-flag default branch still throws `argument-error`
  (`:scry.cli/argument-error`) for any flag outside the opt-in surface.
- [x] Carry `:kaocha-extra` through `main-opts->exec-opts` (do not strip it) so
  it reaches `normalize-exec-opts`/`normalize-kaocha-options` intact.
- [x] Carry the `-m` `:kaocha-extra` map through `normalize-kaocha-options`
  unchanged via the Slice 1 exclusion: because `:kaocha-extra` is in
  `scry-managed-keys`, the collection step does not re-collect it, and the merge
  step preserves it (there are no scattered `-m` extras to merge). See plan
  "Plan-review resolutions".
- [x] Update CLI `usage` text to document `--focus SYM` and
  `--kaocha-opt KEY VALUE` (Kaocha mode only).
- [x] In `test/scry/cli_test.clj`, add tests: `--runner kaocha --focus
  my.ns/test-foo` parses to opts with `:kaocha-extra {:focus ["my.ns/test-foo"]}`
  (or chosen scalar/coll shape); `--kaocha-opt foo bar` → `:kaocha-extra {:foo
  "bar"}`; an unrecognized flag still raises `:scry.cli/argument-error`.
- [x] Reject `--focus`/`--kaocha-opt` in `:clojure-test` (core) mode by adding
  `:kaocha-extra` to the `normalize-core-options` reject set
  (`kaocha-only-keys ∪ kaocha-fallback-keys`, `src/scry/cli.clj:201`); confirm
  `--runner clojure-test --focus …` raises `:scry.cli/argument-error` — add a
  test.
- [x] Run focused `scry.cli-test`; confirm green.

## Slice 3 — Adapter merge + `:focus` coercion (`scry.kaocha/run`, src-kaocha)

- [x] Add a private coercion helper in `src-kaocha/scry/kaocha.clj` that
  coerces `:focus` raw values (string/symbol/keyword, scalar or collection)
  into a vector of keywords matching the filter plugin's parse semantics.
- [x] Add a private merge helper that injects coerced `:kaocha-extra` into the
  resolved config under `:kaocha/cli-options`, with existing `:config`/config
  values authoritative on conflict (OQ2 merge-with-config-wins).
- [x] Wire the merge into the `run` pipeline after `resolve-config` (and
  relative to `apply-runtime-defaults`) without disturbing `select-suites`,
  reporter, or capture-output behaviour.
- [x] Verify the `:kaocha.plugin/filter` default plugin is present in the
  plugin chain for all three config paths (explicit `:config`, tests.edn,
  synthetic fallback); if missing on any path, ensure it like
  `apply-runtime-defaults` ensures `capture-output`.
- [x] Update the `scry.kaocha/run` docstring to document the `:kaocha-extra`
  option (raw forwarded Kaocha cli-options; `:config` authoritative; `:focus`
  coercion) and the `-X` mistyped-key trade-off.
- [x] In `test/scry/kaocha_test.clj`, add a test that `:kaocha-extra {:focus
  ...}` actually filters execution to the focused var(s) (assert reduced
  executed set, not just key presence).
- [x] Add a test that explicit `:config` keys win over conflicting
  `:kaocha-extra` keys, and non-conflicting pass-through keys still apply.
- [x] Run focused `scry.kaocha-test` with the `:kaocha` alias; confirm green.

## Slice 4 — CLI Kaocha integration + acceptance

- [x] In `test/scry/cli_kaocha_test.clj`, add a test exercising the `-m`
  `--runner kaocha --focus my.ns/test-foo` path end-to-end (focused run).
- [x] Add a test exercising the `-X` `:runner :kaocha :focus "my.ns/test-foo"`
  path end-to-end.
- [x] Add/confirm a test that existing Kaocha options (`--suite`, `--config`,
  `--dirs`) still behave as before.
- [x] Confirm `:clojure-test` (core) mode results are unchanged by the new code
  (no `:kaocha-extra` collection effect in core normalization path).
- [x] Run focused `scry.cli-kaocha-test` with the `:kaocha` alias; confirm green.

## Slice 5 — Docs

- [x] Run `bb api-docs` to regenerate `doc/API.md` for the updated
  `scry.kaocha/run` docstring.
- [x] Run `bb api-docs --check` and the focused api-docs regression test
  (`scry.api-docs-test`) with the `:quickdoc:quickdoc-test:kaocha` aliases.
- [x] Update `README.md`: document the `--focus` and `--kaocha-opt` CLI options,
  `-X` top-level pass-through, and the `-X` mistyped-key trade-off.
- [x] Verify README examples stay consistent with the actual CLI/option surface.

## Slice 6 — Final command-line verification

- [x] Run the core command-line CLI checks (`scry.cli-test`).
- [x] Run the optional Kaocha CLI command-line checks
  (`scry.cli-kaocha-test`, `scry.kaocha-test`) with `:kaocha`.
- [x] Run at least one real acceptance command:
  `clojure -M:test:kaocha -m scry.cli --runner kaocha --focus <var>` and/or
  `clojure -X:test:kaocha scry.cli/run :runner :kaocha :focus "<var>"`.
- [x] Run `bb clj-fmt:check` and `bb clj-kondo:lint`.
- [x] Record the commands run and results in `implementation.md`.

## Test review follow-ups (2026-06-21)

- [x] Add an automated test that `:kaocha-extra {:focus ...}` actually filters
  execution on the **synthetic-fallback** config path (no `tests.edn`,
  caller-provided `:test-paths`/`:ns-patterns`, no explicit `:config`). The
  plan listed filter-plugin presence across all three config paths (tests.edn,
  explicit `:config`, synthetic fallback) as a risk, and the implementation
  generalized `ensure-runtime-plugins` to add `:kaocha.plugin/filter`
  specifically because "synthetic fallback ... may omit Kaocha's default plugin
  chain". Real end-to-end focus filtering is currently automated only for the
  tests.edn path (`kaocha-cli-focus-pass-through-test`) and the explicit bare
  `:config` path (`kaocha-extra-focus-filters-execution-test`); the
  synthetic-fallback path is verified only by the manual acceptance command
  recorded in `implementation.md`. Extend `no-tests-edn-fallback-test` (or add a
  sibling) to assert the focused-var executed set is actually reduced via the
  fallback path, locking in the filter-plugin-ensure behaviour against
  regression. (Pattern: `kaocha-run {:test-paths [...] :ns-patterns [...]
  :kaocha-extra {:focus [...]}}` with a mixed pass/fail fixture, asserting the
  executed `:var` set and `:summary :var-count`.)

## Test review follow-ups (2026-06-21, second pass)

- [x] Add a test that multiple `--kaocha-opt KEY VALUE` flags accumulate
  distinct keys into raw `:kaocha-extra` (e.g.
  `["--runner" "kaocha" "--kaocha-opt" "a" "1" "--kaocha-opt" "b" "2"]` →
  `:kaocha-extra {:a "1" :b "2"}`). The committed OQ1 generic mechanism is only
  covered for a single invocation in `parse-main-args-test`
  (`test/scry/cli_test.clj:218`); the `assoc-in` accumulation path is a distinct
  code path from the `add-repeat` mechanism that the existing repeated-`--focus`
  test exercises, and is currently unverified.
- [x] Add an end-to-end test exercising the generic `--kaocha-opt` mechanism
  reaching the Kaocha runner (not just parsing). `--focus` has full end-to-end
  coverage (`kaocha-cli-focus-pass-through-test`,
  `test/scry/cli_kaocha_test.clj:250`), but the generic `--kaocha-opt` path —
  which OQ1 committed to as a real, bounded pass-through surface — is verified
  only piecewise (parse → `:kaocha-extra` in `cli_test`; `:kaocha-extra` →
  `:kaocha/cli-options` in `apply-kaocha-extra` unit test with a typed
  `:threads` value). Add a CLI-level test such as
  `["--runner" "kaocha" "--kaocha-opt" "focus" "<var>"]` asserting it filters
  execution to the focused var like `--focus` does, locking in the full
  `-m` flag → normalize → `:kaocha-extra` → `scry.kaocha/run` chain for the
  generic mechanism (including raw-string `:focus` coercion arriving via
  `--kaocha-opt` rather than the named `--focus` flag).

## Test review follow-ups (2026-06-21, third pass)

- [x] Broaden the boundary no-leak assertion so it covers the full
  `scry-managed-keys` closed set, not just a spot-check. The plan's "Boundary
  regression" risk required asserting the *closed set* is excluded from
  `:kaocha-extra`, and Slice 1's "no scry-managed key ... ever appears under
  `:kaocha-extra`" step is implemented in
  `normalize-exec-opts-kaocha-pass-through-test` ("scry-managed keys never
  leak", `test/scry/cli_test.clj:137`) but only exercises `:runner`,
  `:result-format`, `:suite`, and `:dirs`. The derived set
  (`src/scry/cli.clj:47`) also includes `:progress-callback`, `:source-paths`,
  `:ns-patterns`, `:config`, and `:suites` — none currently asserted absent from
  `:kaocha-extra`. The most dangerous omission is `:progress-callback`: it is a
  *function* value, and if a future edit dropped it from `scry-managed-keys` it
  would silently leak into `:kaocha-extra` → `:kaocha/cli-options` with no test
  catching the regression. Extend the test to pass a single unknown key
  alongside `:progress-callback`, `:source-paths`, `:ns-patterns`, `:config`,
  and `:suites` (valid Kaocha-mode values), asserting `:kaocha-extra` contains
  only the unknown key while each scry-managed key is routed to its normalized
  destination or excluded. (`core-only-keys` are rejected earlier in Kaocha mode
  and so are out of scope for this collection-leak assertion.)

## Test review follow-ups (2026-06-21, fourth pass)

- [ ] Lock the new `:kaocha-extra` public surface in the api-docs content
  contract (`scry.api-docs-test`, `test-quickdoc/scry/api_docs_test.clj`).
  Slice 5 added `:kaocha-extra` to the `scry.kaocha/run` docstring (now in
  committed `doc/API.md:268`), and the design lists "Generated API docs must be
  updated if the public `scry.kaocha/run` API surface changes" as a constraint.
  The content-regression test asserts the `scry.cli/run` section's required
  content in detail (`:scry.cli/argument-error`, `:scry.cli/runner-error`, etc.)
  but the `scry.kaocha` section is asserted only at the namespace/var-anchor
  level — `grep -c kaocha-extra test-quickdoc/scry/api_docs_test.clj` is 0. So
  this task's headline public option has no content lock: a future docstring edit
  could silently drop the `:kaocha-extra` documentation and the focused content
  test would still pass (`bb api-docs --check` only compares committed-vs-
  generated and would not catch a coordinated removal). Add a `kaocha-run-section`
  (mirroring the existing `cli-run-section` via `var-section markdown
  "scry.kaocha" "run"`) and `assert-includes` the required `:kaocha-extra`
  content fragments (e.g. `":kaocha-extra"`, the `:config`-authoritative-on-
  conflict phrasing, and the `:focus` coercion / mistyped-key trade-off text)
  so the generated content contract guards the documented option.
