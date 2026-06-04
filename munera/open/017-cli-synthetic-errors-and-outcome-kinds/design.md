# Design: CLI synthetic errors and machine-readable outcome kinds

## Goal

Make the scry CLI robust when runners return failing/erroring synthetic or suite-level result entries that do not have a concrete `:var`, and expose clearer machine-readable outcome classification for callers that need safe fallback policy decisions.

## Context

While integrating scry into psi, Scry CLI + the optional Kaocha adapter hit a rough edge: an incomplete `:test-paths` classpath caused Kaocha to produce a synthetic failure entry with `:var nil`. Scry then crashed while trying to derive the `.scry-results/` filename from the nil var:

```text
Cannot invoke "clojure.lang.Named.getNamespace()" because "x" is null
```

A caller also had to infer fallback policy from stderr text such as `scry CLI error:`. That is fragile; the CLI should expose structured classification for at least runner infrastructure failure, test load failure, normal test assertion failure, and zero tests.

## Requirements

- Result-file writing must tolerate failing/erroring canonical entries whose `:var` is nil or absent.
- CLI progress output must tolerate nil-var failing/erroring/unknown entries.
- Synthetic/suite-level result files should have deterministic, human-readable names, e.g. `suite-error-1.edn` or `suite-fail-1.edn`.
- Normal var-backed result-file names must remain unchanged.
- `run-cli` outcomes and `clojure -X` non-zero ex-data should include a machine-readable classification that distinguishes:
  - runner infrastructure failure / CLI runner error
  - test load failure / synthetic suite-level load error when representable in canonical results
  - normal test assertion failure/error
  - zero executable tests
  - argument errors if applicable
- The existing human-oriented stderr line can remain for compatibility, but callers should not need to parse it.
- Keep public result map shapes stable except for additive keys on CLI outcomes/ex-data.

## Acceptance

- A failing/erroring canonical entry with nil `:var` writes a readable EDN result file instead of crashing.
- Nil-var progress output prints a useful synthetic name rather than throwing.
- Focused tests cover nil-var result-file naming/writing and CLI end-to-end behavior through `run-cli`.
- Focused tests cover outcome classification for normal failures, zero tests, runner errors, argument errors, and synthetic nil-var load/suite errors where feasible.
- README/SKILL/AGENTS docs are updated only if user-facing CLI classification semantics are introduced.
