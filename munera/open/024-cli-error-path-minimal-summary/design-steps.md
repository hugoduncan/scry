# Design follow-up steps

## Architectural review

- [ ] Align documentation with the new error-path stdout contract: the CLI
      stdout/stderr behavior is documented in detail in both `README.md` and
      `AGENTS.md` (the CLI output-contract description). Adding an
      always-emitted minimal stdout summary on error/exception outcomes
      (`:scry.cli/runner-error`, `:scry.cli/load-error`,
      `:scry.cli/argument-error`, usage) is a user-facing change to that
      documented contract. Update the design's acceptance/scope to require the
      README + AGENTS.md CLI-output-contract updates so the documented
      architecture stays aligned with the implemented behavior. (No behavior
      scope change; only documenting the in-scope behavior.)
