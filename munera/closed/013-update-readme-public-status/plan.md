# Plan

## Approach

This is a README-only documentation update. Replace the stale `Status` section wording with a concise public-facing maturity statement that matches the stable design:

- Use the explicit maturity label: initial public alpha / pre-1.0.
- Say the documented surfaces are usable and tested at a high level: core `clojure.test` runner/API, CLI, scoped result model, nested/reentrant capture isolation, build/release automation, and optional Kaocha adapter.
- Keep the status note short and avoid duplicating the existing README sections for installation, usage, CLI, Kaocha, nested runs, build, or release behavior.
- Avoid promising 1.0 stability, long-term API/result-shape freeze, or production maturity beyond the documented tested surfaces.

## Risks

- Overstating stability could mislead public users before a future 1.0 release.
- Listing too much implementation detail in `Status` could duplicate or drift from the rest of the README.
- Omitting implemented surfaces could leave the README still sounding like an early scaffold.

No blocking design ambiguities remain; the design pins the desired maturity label and required high-level surface coverage.

## Slice order

1. **Read current status context** — inspect the existing README `Status` section and nearby introduction so the replacement fits the surrounding tone.
2. **Replace status wording** — update only the README `Status` section with concise initial-public-alpha / pre-1.0 wording that names the implemented surfaces at a high level.
3. **Documentation verification** — check that README no longer says “Early scaffold,” that the status covers all required surfaces, and that it does not imply 1.0 stability.
4. **Record and commit** — record the implementation/verification notes in the task and commit the README/task updates after execution.
