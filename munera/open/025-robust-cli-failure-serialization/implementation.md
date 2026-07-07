# Implementation Notes

- architectural review: AGENTS.md architecture context loaded; requested META.md and doc/architecture.md are not present in this checkout.
- no architectural review feedback
- ambiguity review added 4 new design steps
- no inconsistency review feedback
- design-step follow-up guidance: preserve the existing CLI contract from `AGENTS.md`/`src/scry/cli.clj` that `:scry.cli/outcome-kind` is the authoritative machine signal and human stdout/stderr diagnostics are supplementary; resolve wording so diagnostic serialization failures are additive metadata, not a scope change to normal test outcome semantics. Relevant implementation files: `src/scry/cli.clj`, `src/scry/cli/results.clj`, `test/scry/cli_test.clj`.
- design follow-up completed: `:scry.cli/diagnostic-error` is additive top-level outcome metadata, not an outcome-kind; entries+summary collection is the boundary after which diagnostic failures preserve the test-derived outcome. Result-file writing should move after normal summary emission to satisfy the summary-before-diagnostics requirement.
- no ambiguity review feedback
- review-slice handoff: when addressing design-step fallout, maintain the boundary that diagnostic serialization is a post-run CLI concern; consult `AGENTS.md` for the CLI contract and `src/scry/cli.clj` / `src/scry/cli/results.clj` for implementation behavior.
- ambiguity review added 3 new design steps: pin sanitizer truncation/string representation, cycle-detection path-vs-global semantics, and controlled Throwable frame shape before implementation tests lock in accidental choices.
- plan-review inconsistency pass: no new feedback
