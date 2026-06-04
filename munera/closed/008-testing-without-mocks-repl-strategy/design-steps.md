# Design follow-up steps

- [x] Decide and document the canonical REPL slice mechanism: documentation-only snippets, a helper namespace, Babashka tasks, or a specific combination, including why that mechanism is the smallest maintainable fit for this repository.
- [x] Pin the exact REPL classpath contexts and evaluation forms for each canonical slice, especially the build (`:test:build`) and release-helper (`:test:release-test` plus any required base alias) checks currently described only as appropriate contexts.
- [x] Decide whether CI should continue using broad `(scry/run)` as a fresh-process smoke check or switch to the same explicit focused slices, and state the expected role of any remaining broad CI/discovery run.
- [x] Choose and document the cleanup/isolation strategy for temporary Kaocha project namespaces created by tests, such as unique generated namespaces, `remove-ns`, shared-name avoidance, or an explicitly justified fresh-process boundary.
- [x] Inventory the remaining injected runner/process boundaries in tests and define which are acceptable Nullable infrastructure boundaries versus mock-like seams to remove, including the state/output assertions expected for accepted boundaries.
- [x] Reconcile the CI guidance with the current `.github/workflows/ci.yml`: either make updating CI to explicit focused slices in scope/required, or explain why the current broad core `(scry/run)` step is only an acceptable fresh-process smoke check and add a separate focused core signal.
- [x] Reconcile the AGENTS.md acceptance criterion with the current AGENTS.md REPL examples by specifying that the implementation must replace or clearly qualify broad `(scry/run)` as smoke-only and document the focused REPL slices as canonical.
