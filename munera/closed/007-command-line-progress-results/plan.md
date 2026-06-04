# Plan

## Approach

Implement command-line behavior as a dedicated CLI layer that reuses the existing scry runner/capture machinery without changing `scry.core/run` or the REPL-first API.

Key decisions:

- Add a new `scry.cli` namespace under `src/` with three public-ish boundaries:
  - `normalize-exec-opts` for `clojure -X` EDN maps.
  - `parse-main-args` / `-main` for `clojure -M -m scry.cli` string flags.
  - `run-cli` as the common implementation returning an outcome map and never calling `System/exit`.
- Keep process termination outside the shared implementation:
  - `-main` prints/uses CLI effects and calls `System/exit` with the returned exit code.
  - `run` for `-X` returns the successful outcome, and throws `ex-info` with `:exit-code`, `:summary`, and error data on non-zero outcomes.
- Introduce an injectable/bindable CLI boundary for output, filesystem, runner dispatch, and process exit where practical. Tests should call `run-cli` directly with temporary directories and captured writers, not exercise real `System/exit` except indirectly through small unit tests around the `-main` plumbing if needed.
- Add live per-test-var progress by extending the core clojure.test runner/capture path with an optional progress callback invoked after each test var completes, using canonical per-var status after the var's events/output have been captured. The default remains nil so existing API behavior stays quiet.
- Request detailed result data for CLI runs regardless of broad suite formatting by configuring `:result-format` to retain canonical detailed entries, and write `.scry-results/*.edn` from canonical/detailed entries rather than terminal-formatted compact output.
- Implement Kaocha CLI mode through dynamic loading of `scry.kaocha` so the core jar does not gain a hard dependency. Kaocha mode must run through the same `run-cli` outcome/result-file/summary code and must emit live per-test-var progress when the optional adapter is available. Implement this with an adapter progress callback/reporter hook that observes completed leaf testables during the run; if that cannot be done without compromising the optional adapter boundary, treat it as a blocking implementation issue rather than falling back to post-run progress.
- Normalize selectors by runner:
  - Core mode passes `:dirs`, `:namespaces`, `:vars`, and `:ns-pattern` to `scry.clojure-test/run`.
  - Kaocha mode rejects core-only `:namespaces`, `:vars`, and `:ns-pattern`; accepts `:suite`, `:suites`, `:config`, and known fallback config keys; maps `:dirs` to fallback `:test-paths` only when no explicit `:config` is supplied; rejects conflicting `:dirs` plus explicit `:test-paths`.
- Add focused CLI tests in `test/scry/cli_test.clj` using temporary directories so repository `.scry-results/` is never mutated by tests.
- Update docs and project metadata: README for users, AGENTS.md for agent/maintainer workflow, CHANGELOG.md for user-visible CLI behavior, `.gitignore` for `.scry-results/`, and `deps.edn` examples/aliases only if needed.

## Risks

- Live progress for core tests likely requires touching `scry.capture` and `scry.clojure-test`; this must be optional and backwards-compatible so all existing REPL/API result shapes remain stable.
- Kaocha may not provide a straightforward live per-var callback through the current adapter. If true, support Kaocha CLI mode with deterministic per-var progress derived from adapter results and record the limitation clearly; do not introduce a hard Kaocha dependency into the core namespace graph.
- Resolving vars from `-X` symbols/strings requires requiring the namespace and validating `:test` metadata; unresolved vars and non-test vars must fail clearly before the runner starts.
- CLI result files need full readable EDN. Some assertion `:expected`/`:actual` values may not be fully EDN-readable if they contain arbitrary objects; tests should pin practical readable cases and implementation should prefer `pr-str`/`spit` of data already stored by scry.
- Tests around `-main` process exit can be brittle; prefer testing parse/normalize and `run-cli` directly, keeping `System/exit` isolated.
- The task is broad. Keep slices small and verify after each slice to avoid entangling parser, runner, filesystem, and docs changes.

## Slice order

1. **Core progress hook and detailed CLI result support** — add optional per-var progress callback to the clojure.test path, preserve current behavior by default, and ensure CLI can obtain detailed canonical entries for all executed vars.
2. **CLI option normalization and argument parsing** — implement `-X` EDN normalization and the small explicit `-m` flag parser, including runner-specific validation and structured argument errors.
3. **Shared CLI runner and filesystem/output effects** — implement `run-cli`, `.scry-results/` lifecycle, result-file writing, progress printing, final summary, exit-code classification, and no-tests/unknown-status handling.
4. **Kaocha CLI bridge** — dynamically dispatch to `scry.kaocha/run`, normalize Kaocha options, enforce selector constraints/conflicts, preserve merged-output result-file semantics, and test both available and unavailable adapter paths where practical.
5. **Entry points and invocation verification** — add `-main` and `run` exec entry functions on top of the shared implementation, verify successful and non-zero behavior without changing `scry.core/run`.
6. **Documentation and housekeeping** — update README, AGENTS.md, CHANGELOG.md, `.gitignore`, and any command examples/aliases needed for `clj -M:test -m scry.cli` and `clj -X:test scry.cli/run`.
7. **Full regression verification** — run focused CLI tests plus existing core, Kaocha, build, and release-adjacent checks needed to prove the CLI did not alter existing behavior or artifact boundaries.

## Plan ambiguity follow-up decisions

Focused verification for this task is pinned to two focused test namespaces:

- Core CLI tests live in `test/scry/cli_test.clj` and run with:

  ```sh
  clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? r) (System/exit 1)))"
  ```

- Optional Kaocha CLI tests live in `test/scry/cli_kaocha_test.clj` and run with:

  ```sh
  clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? r) (System/exit 1)))"
  ```

Core CLI tests should cover normalization/parser behavior, core progress callback behavior, `run-cli` filesystem/output effects, entry-point plumbing, and exit-code classification. Kaocha CLI tests should cover dynamic adapter loading when available, suite/config/fallback option handling, core-selector rejection, result-file output semantics, and Kaocha progress/summary behavior.

CLI runs obtain detailed entries by forcing retention of `:canonical-results` in the runner result. The CLI will merge user-supplied `:result-format` with an internal full-detail retention requirement for every scope: append/include `:canonical-results` in each scope's `:top-level-keys` while leaving user-facing `:results` projection controlled by the caller's `:result-format`. Result files and CLI summaries/progress are generated from `:canonical-results`, not from scoped `:results` or `:failures`, so broad suite runs still have all executed var entries with assertions and output. If a runner cannot return `:canonical-results`, `run-cli` treats that as a runner error rather than writing compact/incomplete result files.

`.scry-results/` file names use this deterministic namespace-prefixed scheme:

```clojure
(str (encode-file-segment (namespace var-symbol))
     "__"
     (encode-file-segment (name var-symbol))
     ".edn")
```

`encode-file-segment` preserves ASCII letters, digits, `.`, `_`, and `-`; every other character is encoded as `_u` followed by its four-digit lowercase hexadecimal code point and `_` (for example `?` becomes `_u003f_`). The common case therefore reads naturally, e.g. `.scry-results/scry.fixtures.failing__equality-fails.edn`, while punctuation-heavy legal Clojure var names remain deterministic and portable enough for tests. Namespaces are always included, so same unqualified var names in different namespaces cannot collide.

Kaocha CLI progress must be live per test var when `:runner :kaocha` is used with the optional adapter present. Implement this by extending the optional adapter path with a progress callback hook that observes completed Kaocha leaf testables during the run (through Kaocha reporter/event integration), then invokes the same CLI progress printer used by core mode once each leaf var's final status is known. The existing adapter result tree remains the source of final `:canonical-results` and result files. If a future implementation discovers Kaocha cannot expose completed leaf-var events without compromising the adapter boundary, that is a blocking implementation issue to record rather than silently switching the task to post-run progress.
