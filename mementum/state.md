# Mementum State

Project: scry

## Current state

- Initial scry scaffolding has been committed.
- Munera and Mementum have been initialized for future task and project memory tracking.
- User-facing README and AI-agent guidance have been added.
- A top-level `SKILL.md` has been created so agents can copy or reference scry usage guidance in other projects.
- Task `001-result-format-by-invocation-scope` is closed. It changed `scry` results to scoped, configurable formatting: suite scope is compact, single namespace scope includes all assertions without output, and single var scope includes assertions plus output. `CHANGELOG.md`, `README.md`, `AGENTS.md`, and `SKILL.md` document the change.
- Task `002-kaocha-tests-edn-suite-selection` design follow-up from inconsistency review is complete: string suite selectors now have explicit fallback text matching semantics in `design.md`.
- Task `002-kaocha-tests-edn-suite-selection` plan-review follow-up is complete: `plan.md` now pins Kaocha adapter test location/alias command and temporary-project `user.dir` isolation strategy; corresponding review-added steps are checked in `steps.md`.
- Task `002-kaocha-tests-edn-suite-selection` plan inconsistency review found no new actionable feedback; the note is recorded in `implementation.md`.
- Task `002-kaocha-tests-edn-suite-selection` latest design inconsistency follow-up is complete: `:suite` is consistently documented as the supported single-suite form, and `src-kaocha/scry/kaocha.clj` now rejects invalid plural `:suites` values (scalar/string/map/empty) with `ex-info`.
- Task `002-kaocha-tests-edn-suite-selection` plan/steps have been refreshed from the stable design: `plan.md` includes invalid `:suites` API-error handling, and `steps.md` includes validation and test checklist items for invalid plural suite selectors.
- Task `002-kaocha-tests-edn-suite-selection` latest plan ambiguity follow-up is complete: `plan.md` now documents passing resolved suite id values directly to `kaocha.config/apply-cli-args` without stringification, and `test/scry/kaocha_test.clj` covers preservation of exact namespace-qualified keyword ids and exact string ids.
- Task `002-kaocha-tests-edn-suite-selection` implementation is complete: `scry.kaocha/run` loads `tests.edn` from `user.dir`, supports `:suite`/`:suites` selection for loaded, supplied, and fallback configs, preserves full `:config`, applies quiet runtime defaults, and ignores skipped Kaocha suites in scry summaries/results. Focused Kaocha adapter tests and core `scry/run` verification pass. Implementation review and test review found no new actionable issues. README, AGENTS.md, and SKILL.md document the REPL-first Kaocha API.
- Task `002-kaocha-tests-edn-suite-selection` docs-review follow-up is complete: `CHANGELOG.md` Unreleased documents the user-visible Kaocha adapter changes, and the review-added steps item is checked.
- Task `002-kaocha-tests-edn-suite-selection` code-shaper review found no new actionable code-quality issues; the review note is recorded in `implementation.md`.

## Active focus

- Open task `002-kaocha-tests-edn-suite-selection`: make `scry.kaocha/run` load `tests.edn` suites and support REPL suite selection while preserving full `:config` input.

## Useful links

- Project README: `README.md`
- Agent guidance: `AGENTS.md`
- Munera task plan: `munera/plan.md`
- Open Kaocha suite-selection task: `munera/open/002-kaocha-tests-edn-suite-selection/`
- Closed scoped-format task: `munera/closed/001-result-format-by-invocation-scope/`
- Mementum memories: `mementum/memories/`
- Mementum knowledge: `mementum/knowledge/`
