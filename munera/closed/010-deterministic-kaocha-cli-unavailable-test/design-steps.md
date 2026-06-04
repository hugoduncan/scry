# Design follow-up steps

- [x] Pin the optional Kaocha CLI boundary contract in `design.md`: choose resolver-level versus whole-runner injection, specify the boundary key/arity/return value and missing-adapter failure wrapping, and ensure the deterministic test still exercises normal `run-kaocha` orchestration while the default production path dynamically resolves at execution time.
- [x] Align `design.md` with the current CLI flow: the resolved Kaocha runner should accept the normalized Kaocha options passed into `run-kaocha` after `run-kaocha` adds the CLI `:progress-callback`, not an option map that `run-kaocha` itself builds.
