# Steps

- [x] Update `AGENTS.md` CLI description with the new stderr failure diagnostic.
- [x] Update `README.md` CLI section with the new stderr failure diagnostic.
- [x] Add focused `run-cli` load-error stderr diagnostic test to `scry.cli-test`.
- [x] Update `run-cli-synthetic-nil-var-results-test` stderr assertion.
- [x] Add real load-error Kaocha CLI test to `scry.cli-kaocha-test`.
- [x] Implement cause-extraction helpers + `write-failure-diagnostic!` in `scry.cli`.
- [x] Fire suite-level `:error` progress callback in `scry.kaocha/progress-reporter`.
- [x] Run core CLI suite (`clojure.test` paths) green.
- [x] Run Kaocha adapter + CLI suites green.
- [x] Manual load-error run: cause + results-dir hint on stderr, exit 1, file written.
- [x] `bb clj-fmt:check` and `bb clj-kondo:lint` clean.
