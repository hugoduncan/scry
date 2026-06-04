# Plan

## Slice 1: Characterize nil-var result entries

- Add focused tests around `scry.cli.results` for nil-var failing/erroring entries.
- Add focused CLI tests using an injected runner boundary that returns canonical nil-var entries to reproduce the previous crash without depending on a particular Kaocha synthetic shape.

## Slice 2: Robust synthetic names

- Add a helper in `scry.cli.results` to derive a display/result identity for an entry.
- Preserve existing namespace-prefixed filenames for var-backed entries.
- For entries without `:var`, write deterministic synthetic files using entry status and per-status/index ordering, e.g. `suite-error-1.edn`.
- Make CLI progress use the same fallback display identity or an equivalent helper.

## Slice 3: Outcome classification

- Add additive machine-readable classification to CLI outcomes, likely `:kind` or `:failure-kind`, with stable keywords.
- Classify successful runs as passing/success.
- Classify non-zero aggregate outcomes in priority order, roughly:
  1. runner/argument infrastructure errors caught by the CLI
  2. zero executable tests
  3. synthetic nil-var load/suite error entries
  4. ordinary test failures/errors/unknown statuses
- Ensure `scry.cli/run` (`clojure -X`) propagates the classification in `:scry.cli/non-zero` ex-data via the embedded outcome.

## Slice 4: Docs and verification

- Document the new CLI outcome classification where appropriate for machine callers.
- Run focused core CLI tests during development.
- Final verification should include the documented focused CLI command-line check and/or core command-line suite depending on touched surfaces.
