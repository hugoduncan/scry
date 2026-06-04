# Kaocha tests.edn suite awareness and suite selection

## Goal

Make `scry.kaocha/run` use Kaocha suites defined in `tests.edn` by default when a Kaocha config file is present, and allow callers to run specific suites by name from the REPL.

Keep the existing ability to pass a complete Kaocha config map with `:config`; that path remains authoritative and must not be broken.

## Context

`scry.kaocha/run` currently builds a small synthetic Kaocha config when `:config` is not supplied:

```clojure
{:kaocha/tests [{:kaocha.testable/id :unit
                 :kaocha.testable/type :kaocha.type/clojure.test
                 :kaocha/source-paths source-paths
                 :kaocha/test-paths test-paths
                 :kaocha/ns-patterns ns-patterns}]
 :kaocha/plugins [:kaocha.plugin/capture-output]
 :kaocha/reporter []
 :kaocha/color? false}
```

That means projects with a real `tests.edn` and multiple Kaocha suites cannot use their configured suites through the simple REPL entry point. They can only do so by constructing and passing a full `:config` manually.

Kaocha already has config-loading and CLI-argument selection behavior. `kaocha.config/load-config` reads a project config, and `kaocha.config/apply-cli-args` marks non-selected suites skipped based on suite ids. `scry.kaocha/run` should reuse Kaocha's own config semantics rather than inventing a parallel suite model.

## Proposed API

Support these REPL-oriented forms:

```clojure
(require '[scry.kaocha :as k])

(k/run)
(k/run {:suites [:unit]})
(k/run {:suites [:unit :integration]})
(k/run {:suite :unit}) ;; single-suite convenience form
```

Keep full config override:

```clojure
(k/run {:config full-kaocha-config})
```

Option semantics:

- `:config` remains the highest-precedence base option. When supplied, it supplies the Kaocha test configuration to run instead of loading `tests.edn` or building the fallback synthetic suite.
- `:suites` selects suites from the resolved config by suite id/name.
- `:suites` must be a non-empty collection of suite selectors. Strings, maps, scalar selector values, and empty collections are API errors; use `:suite` for a single selector. Throw an `ex-info` with the invalid value and a clear message rather than treating an empty collection as all suites or no suites.
- `:suite` is optional sugar for a single selected suite.
- Supplying both `:suite` and `:suites` is an API error. Throw an `ex-info` with a clear message and both option values rather than silently choosing one.
- Suite selectors are keywords, symbols, strings, or the same value type as a configured `:kaocha.testable/id`. Matching is resolved against the final config's suite ids as follows:
  1. If the selector is exactly equal to one configured suite id, select that id.
  2. Otherwise, derive comparable selector text: use the string itself for a string selector, or `(name selector)` for selectors that support `name`. Selectors that are neither strings nor named values cannot use fallback matching.
  3. Compare that selector text with comparable suite-id text: use the string itself for a string suite id, or `(name suite-id)` for suite ids that support `name`. Suite ids that are neither strings nor named values cannot participate in fallback matching.
  4. If the text comparison finds exactly one suite id, select that id.
  5. If no suite id matches, throw an `ex-info` naming the unknown selector and available suite ids.
  6. If text comparison matches multiple suite ids, throw an `ex-info` naming the ambiguous selector and matching ids. Use an exact qualified id to disambiguate.
- Namespace-qualified keywords/symbols therefore require exact equality to preserve their namespace. Their unqualified `name` is only a fallback and is intentionally rejected when duplicate text collides. String selectors match by exact string id first, then by this same text fallback against string or named suite ids.
- `:result-format` continues to control scry result formatting.

## Config resolution behavior

When `:config` is supplied:

1. Treat the supplied map as an already resolved Kaocha config. Do not call `kaocha.config/normalize` on it and do not merge fallback source/test/ns-pattern options into it. This preserves the current full-config override behavior and avoids surprising callers who intentionally prepared Kaocha config themselves.
2. Apply suite selection if `:suite` or `:suites` is supplied.
3. Merge only the scry adapter runtime defaults needed for structured quiet output, using the policy below.

When `:config` is not supplied:

1. Prefer loading the project's Kaocha config (`tests.edn`) using Kaocha's config loader when `tests.edn` exists.
2. If no config file exists, preserve the current fallback synthetic `:unit` suite behavior using `:source-paths`, `:test-paths`, and `:ns-patterns` options. Normalize only this synthetic config path.
3. Apply suite selection if `:suite` or `:suites` is supplied.
4. Merge the scry adapter runtime defaults needed for structured quiet output, using the policy below.

Scry adapter default merge policy:

- Always ensure `:kaocha.plugin/capture-output` is present in `:kaocha/plugins`, appending it only if absent. Preserve any existing plugins and their order.
- Always set `:kaocha/reporter` to `[]` before running through `scry.kaocha/run`. The adapter returns structured results and should stay quiet even if `tests.edn` configured human CLI reporters.
- Always set `:kaocha/color?` to `false` before running through `scry.kaocha/run`, because the adapter is not producing human terminal output.
- These quiet-output defaults apply equally to `tests.edn`, fallback, and `:config` input. Callers that need different reporter/color behavior should run Kaocha directly or pass a future explicit scry option if one is added; this task does not add such an override.
- Do not overwrite other Kaocha configuration keys.

The implementation should be careful not to reintroduce command-line-first behavior in documentation. The public examples should remain REPL-first.

## Scope

In scope:

- Update `src-kaocha/scry/kaocha.clj` config construction/loading.
- Add suite selection options for `k/run`.
- Preserve `:config` support.
- Preserve current no-`tests.edn` fallback behavior.
- Add tests or fixtures covering `tests.edn` suite loading and suite selection.
- Update README, AGENTS.md, and SKILL.md Kaocha sections if the public API changes.

Out of scope:

- Changing `scry.core/run` to depend on or dispatch to Kaocha.
- Adding a command-line interface.
- Implementing namespace/var-level Kaocha selectors equivalent to `scry.clojure-test/run`.
- Changing the scoped result formatter beyond what is necessary to report selected Kaocha suites.

## Design considerations

- Keep `scry.core` free of a hard dependency on Kaocha; all changes should stay under `src-kaocha/` and tests that require the `:kaocha` alias.
- Prefer using Kaocha's own config and suite-selection functions over duplicating behavior.
- Keep REPL ergonomics simple: suite selection should be one small option map, not manual config assembly.
- Avoid surprising projects without `tests.edn`; `(k/run)` should continue to work with the existing default paths and namespace pattern.
- Think through whether `kaocha.config/load-config` exits/warns when no file exists. If it warns loudly or returns unsuitable defaults, detect file presence before calling it and use the current fallback config when absent.
- If selected suites do not exist, fail clearly or return a clear no-tests result; prefer a clear exception if that is more actionable in a REPL.

## Acceptance criteria

- In a project with `tests.edn` defining multiple suites, `(k/run)` runs the configured suites rather than the hard-coded synthetic `:unit` suite.
- `(k/run {:suites [...]})` runs only the named suite ids from the resolved Kaocha config.
- The `:suite` single-suite convenience form is documented and tested.
- `(k/run {:config full-kaocha-config})` still runs the supplied full config.
- Suite selection works with full-config input as well as `tests.edn`-loaded input, or any limitation is explicitly documented and tested.
- Projects without `tests.edn` still support the current fallback behavior with default `source-paths`, `test-paths`, and `ns-patterns`.
- Result maps continue to use the existing scry scoped result model and `:result-format` option.
- Tests cover `tests.edn` suite awareness, named-suite selection, full-config preservation, and no-config fallback.
- User-facing and agent documentation describe the REPL-first Kaocha suite selection API.
