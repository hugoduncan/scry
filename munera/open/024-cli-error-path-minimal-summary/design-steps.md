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

## Ambiguity review

- [ ] Clarify what "usage paths" means for the minimal summary. The design
      lists "the argument-error and usage paths" together as `:summary nil`
      outcomes needing a minimal summary, but in `-main` the `--help`/usage path
      is a distinct success path (exit 0) that already prints usage text to
      stdout and never produces an error outcome. Specify whether `--help`/usage
      should also emit the error-style "no tests run" minimal summary (likely
      undesirable for a deliberate help invocation), or whether only the
      argument-error path is in scope. As written, "usage outcomes" in
      Acceptance is ambiguous.
- [ ] Clarify whether the deliverable is stdout text only or also the returned
      outcome map's `:summary` key. The goal says "rather than leaving
      `:summary nil` with nothing on stdout", conflating the returned
      outcome-map `:summary` value (currently `nil` on error paths) with stdout
      output. Acceptance speaks only of stdout text. State explicitly whether
      the returned `:summary` map key stays `nil` (stdout-only change) or must
      be populated for error outcomes.

## Inconsistency review

- [ ] Reconcile the design's premise about `:scry.cli/load-error` with the
      code. design.md states (Intent) that load-error "arrives via a thrown
      exception" and "produces no final summary on stdout", and lists it among
      the `:summary nil` thrown outcomes. But in `cli.clj` the catch-path
      `error-outcome-kind` only yields `:scry.cli/argument-error` or
      `:scry.cli/runner-error` — never load-error. `:scry.cli/load-error` is
      produced solely by `classify-outcome` on the normal return path, which
      already calls `write-summary!` and so already emits a stdout summary (and
      its stderr detail via `write-failure-diagnostic!`). The design's
      motivation, Goal "Concretely" list, and first Acceptance bullet should be
      corrected so load-error is not treated as a silent/thrown `:summary nil`
      outcome; otherwise the implementer may add redundant/duplicate stdout
      output for load-error or target the wrong path.
