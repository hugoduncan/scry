# Implementation Notes

Task created on 2026-09-08. No implementation has started.

Design orientation inspected the existing core completed-var callback, CLI end-of-run writer and robust sanitizer/diagnostic boundary, pinned Kaocha 1.91.1392 reporter/plugin lifecycle, and prior tasks 007 and 025. The design resolves timing, payload, runner parity, atomic publication, reconciliation, duplicate callbacks, mutable snapshots, diagnostic failures, runner aborts, synthetic entries, and durability limits.

- no architectural review feedback
- ambiguity review added 6 new design steps
- inconsistency review added 3 new design steps
- Follow-up resolution should preserve runner authority and keep diagnostic I/O non-authoritative; use `src/scry/cli.clj` for outcome/progress composition, `src/scry/cli/results.clj` for naming/sanitization/filesystem lifecycle, `src/scry/clojure_test.clj` for core callback timing, `src-kaocha/scry/kaocha.clj` for hook ordering, and the public `scry.cli/run` contract generated into `doc/API.md` when settling exact semantics.

2026-09-08 — Design-review follow-up: Kaocha executes plugin hooks left-to-right (`plugin/run-hook*` reduces the active chain), capture-output materializes and removes its buffer in `post-test`, and `run-testables` starts the next leaf only after the current leaf’s full `post-test` chain returns. The adapter completion plugin therefore needs to be appended last after the final configured/ensured plugin chain, not merely placed after capture-output. Existing CLI terminal progress writes/flushes synchronously and does not contain writer failures; the refined design deliberately preserves that behavior while containing sink failures only.

- 2026-09-08 architectural re-review found no new design feedback
- 2026-09-08 ambiguity re-review found no new design feedback
- 2026-09-08 inconsistency re-review found no new design feedback
