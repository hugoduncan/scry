# Plan

## Approach

Implement scoped result formatting as a result-shaping layer on top of the existing `clojure.test` capture pipeline.

Key decisions:

- Keep execution/discovery behavior in `scry.clojure-test` essentially unchanged; only use the resolved executable vars plus the original run options to classify result scope.
- Extend `scry.capture` so each executed test var can retain all assertion events, including passing assertions, because namespace/var scopes need passing assertion detail.
- Build a canonical unformatted per-var representation first, then format it according to a merged default/custom `:result-format` configuration.
- Use scope keywords `:suite`, `:namespace`, and `:var`, matching the design's three invocation scopes.
- Use the concrete option shape shown in the design: `{:result-format {:suite {...} :namespace {...} :var {...}}}` with per-scope `:top-level-keys`, `:entry-keys`, `:assertions?`, and `:output?` overrides merged onto defaults.
- Treat inclusion booleans as semantic gates after key projection: `:assertions? false` removes `:assertions` even if listed in `:entry-keys`; `:assertions? true` includes `:assertions` even if omitted from `:entry-keys`; `:output? false` removes `:out`/`:err` even if listed; `:output? true` includes `:out`/`:err` even if omitted. This keeps toggles authoritative and avoids contradictory configuration.
- Treat `:results` as the canonical formatted collection and retain `:failures` as a filtered compatibility collection of failing/erroring entries whenever `:failures` is requested in `:top-level-keys`.
- Do not force compatibility collections into custom top-level projections: if a caller omits `:results` or `:failures` from `:top-level-keys`, the returned map omits them. Public helpers must tolerate this by preferring `:failures`, falling back to filtering `:results`, and returning nil/empty documented values when the selected format omits both collections.
- Keep suite/multi default compact: no stdout/stderr and no full per-assertion details; include only failing/erroring vars by default with per-var assertion counts.
- Make detailed namespace/var defaults include every executed test var, all assertion details, and output only for single var scope.
- Preserve `scry.core`'s public helpers by reading `:failures` for compatibility and falling back to `:results` where useful.
- Document any Kaocha limitation if its event data cannot provide the same passing-assertion detail as the `clojure.test` runner.

## Risks

- `clojure.test` pass reports may not include the exact same fields as fail/error reports, so passing assertion detail may be less rich in some cases.
- Capturing every passing assertion could increase memory use for broad runs; the implementation should avoid exposing huge broad-run payloads by default and may avoid retaining unnecessary detail only if doing so does not complicate the design.
- Mixed `:vars` and `:namespaces` options need careful classification so explicit executable vars take precedence as specified.
- Existing tests and docs assume `:failures` is the only collection; accessors, report rendering, and examples need coordinated updates.
- Kaocha may not expose equivalent passing assertion events/output separation, so adapter parity may require a documented limitation or a smaller follow-up.

## Slice order

1. **Baseline and API orientation** — inspect current result construction, helper assumptions, docs, and tests; choose the concrete `:result-format` map shape and defaults.
2. **Scope classification** — add invocation-scope classification from original options and resolved executable vars, including the design's tie-breakers for mixed/filtered selectors.
3. **Capture completeness** — update capture to record passing assertion events and construct per-var status/assertion summaries for all executed vars.
4. **Formatting layer** — implement default per-scope formatting plus custom top-level keys, entry keys, assertion inclusion, and output inclusion.
5. **Public helper compatibility** — update `scry.core` helpers and report rendering to work with `:results` while preserving `:failures` behavior.
6. **Kaocha adapter alignment** — reuse the formatter where practical or document/test limitations if Kaocha cannot supply full detail.
7. **Behavior tests** — add/update tests for broad, multi-namespace, single namespace, single var, tie-breakers, passing assertion detail, and custom formatting per scope.
8. **Documentation** — update README, AGENTS.md, and SKILL.md to describe scoped defaults, `:results`, compatibility `:failures`, and configuration.
9. **Verification and notes** — run the test suite, update `steps.md` completion state during implementation, and append implementation decisions/discoveries to `implementation.md`.
