# Implementation Notes

Task created on 2026-09-08. No implementation has started.

Design orientation inspected the existing core completed-var callback, CLI end-of-run writer and robust sanitizer/diagnostic boundary, pinned Kaocha 1.91.1392 reporter/plugin lifecycle, and prior tasks 007 and 025. The design resolves timing, payload, runner parity, atomic publication, reconciliation, duplicate callbacks, mutable snapshots, diagnostic failures, runner aborts, synthetic entries, and durability limits.

- no architectural review feedback
- ambiguity review added 6 new design steps
- inconsistency review added 3 new design steps
- Follow-up resolution should preserve runner authority and keep diagnostic I/O non-authoritative; use `src/scry/cli.clj` for outcome/progress composition, `src/scry/cli/results.clj` for naming/sanitization/filesystem lifecycle, `src/scry/clojure_test.clj` for core callback timing, `src-kaocha/scry/kaocha.clj` for hook ordering, and the public `scry.cli/run` contract generated into `doc/API.md` when settling exact semantics.
