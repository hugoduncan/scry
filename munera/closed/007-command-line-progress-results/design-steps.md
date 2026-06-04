# Design follow-up steps

- [x] Clarify the concrete command-line option input model shared by `-m` and `-X`, including how directory, namespace, var, namespace-pattern/regex, result-format, and runner-mode options are represented and normalized before calling existing runner options.
- [x] Decide whether Kaocha CLI mode is mandatory for this task or explicitly narrowed/deferred, and if included, define which Kaocha options are accepted from `-m` string flags versus `-X` EDN data, especially `:suite`, `:suites`, and `:config`.
- [x] Specify the process-exit contract for both entry points: where `System/exit` or exceptions are used for `-m` and `-X`, how non-zero statuses are produced for failures/no tests/argument errors, and what shared pure/testable function returns before process termination.
- [x] Clarify the per-runner selector contract so core-only selectors (`:namespaces`, `:vars`, `:dirs`/`--dir`) are not implied to work in Kaocha mode unless explicitly mapped/supported; state how `:dirs` relates to Kaocha `:test-paths` if applicable.
- [x] Clarify Kaocha CLI result-file output semantics: because `scry.kaocha` merges stdout and stderr into `:out` and leaves `:err` empty, the CLI EDN detail requirement should either accept that adapter behavior or specify a new separation mechanism.
