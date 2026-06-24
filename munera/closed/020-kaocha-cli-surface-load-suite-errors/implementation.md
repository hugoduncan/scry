# Implementation notes

## Investigation (repro)

Reproduced a real Kaocha load error in a temp project (broken test ns with a
load-time unresolved symbol). Findings:

- Adapter result entry (CLI result-format includes `:canonical-results`):
  ```
  {:var nil :ns nil :status :error
   :assertion-summary {:pass 0 :fail 0 :error 1}
   :assertions [{:type :error
                 :message "Failed loading tests:"
                 :expected nil
                 :actual <clojure.lang.Compiler$CompilerException>  ; live Throwable
                 :stacktrace "..."}]
   :out "" :err ""}
  ```
  So `:assertions[0] :actual` is a **live Throwable** whose root cause carries the
  real message (e.g. `java.lang.RuntimeException: Unable to resolve symbol: ...`).

- Kaocha reporter events for a load failure: `[:kaocha/begin-suite :error
  :kaocha/end-suite :summary]`. The `:error` arrives with no enclosing test var, which
  is why `progress-reporter` (keyed on `:end-test-var`) never fired.

## Changes

- `src/scry/cli.clj`: added `root-cause-throwable`, `throwable-cause-text`,
  `assertion-cause-text` (handles live Throwable and `Throwable->map` shapes),
  `load-error-detail`, `failure-outcome-kinds`, `results-dir-pointer`, and
  `write-failure-diagnostic!`. `run-cli` calls `write-failure-diagnostic!` after
  `write-summary!`. stdout summary, classification, exit codes, and result-file writing
  are unchanged; the diagnostic is stderr-only and routed through the `boundary :err`.
- `src-kaocha/scry/kaocha.clj`: `progress-reporter` now fires the callback for a
  suite-level `:error` (no `current-var`) with a synthetic `{:var nil :ns nil :status
  :error :assertion-summary {:pass 0 :fail 0 :error 1}}` entry, so the `suite-error-1`
  progress label streams during the run.

## Behaviour change (stderr) and test impact

The pointer line is emitted for ALL failure outcome kinds (`:scry.cli/load-error`,
`:scry.cli/test-failure`, `:scry.cli/unknown-result`), per the design. Existing
`scry.cli-test` / `scry.cli-kaocha-test` cases that asserted exact stderr for those
outcomes were relaxed to `str/starts-with?` the progress labels plus
`str/includes? "for failure details"`. `zero-tests`, `pass`, and `runner-error`
outcomes are unaffected (stderr unchanged).

## Verification

- Core slice: `scry.capture-test` + `scry.clojure-test-test` + `scry.cli-test` → 51
  tests, 525 pass, 0 fail/error.
- `scry.cli-test` direct `clojure.test` run: 45 tests, 353 assertions, green.
- `scry.cli-kaocha-test` + `scry.kaocha-test` (`:kaocha`): 22 tests, 124 assertions, green.
- `bb clj-fmt:check` and `bb clj-kondo:lint`: clean.
- Manual: `scry.cli --runner kaocha` on a broken-load project prints
  `suite-error-1` then `Load error: Failed loading tests: — java.lang.RuntimeException:
  Unable to resolve symbol: ...` then `See .../​.scry-results for failure details (1 file).`
  to stderr; stdout stays the terse summary; exit 1; `.scry-results/suite-error-1.edn`
  written.
