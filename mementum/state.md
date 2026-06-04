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
- Task `003-tools-build-jar` architecture review found no new actionable architectural-fit feedback. Review note is recorded in `implementation.md`; META.md and doc/architecture.md were absent, so review used AGENTS.md plus the task design.
- Task `003-tools-build-jar` ambiguity review found no new actionable ambiguity feedback. Review note is recorded in `implementation.md`; no `design-steps.md` was created because there were no follow-up items.
- Task `003-tools-build-jar` inconsistency review found no new actionable inconsistency feedback. Review note is recorded in `implementation.md`; no `design-steps.md` was created because there were no follow-up items.
- Task `003-tools-build-jar` plan and steps have been created from the stable design. The plan chooses a conservative core-only jar from `src`, with `src-kaocha` excluded to preserve the optional Kaocha boundary; implementation slices cover build alias/script, focused build checks, AGENTS.md docs, and verification.
- Task `003-tools-build-jar` plan ambiguity review found actionable follow-up: specify where focused build checks live and their command, and decide/document whether the optional `install` task is in scope with verification or deferred.
- Task `003-tools-build-jar` plan ambiguity follow-up is complete: `plan.md` and `steps.md` now pin focused build checks to `test/scry/build_test.clj` with the exact `clojure -M:test:build -e ...` command, and the optional local Maven `install` task is explicitly deferred so this task implements only `clean` and `jar`.
- Task `003-tools-build-jar` plan inconsistency follow-up is complete: `plan.md` no longer says the build alias supports optional `install`; it now lists only `clean` and `jar` as in-scope and keeps local Maven installation deferred.
- Task `003-tools-build-jar` implementation is complete: `deps.edn` has a `:build` alias, `build.clj` implements `clean` and a core-only `jar` for coordinate `org.hugoduncan/scry` with version `0.1.<git-revcount>`, `target/` is ignored, `AGENTS.md` documents maintainer build workflow, and focused build checks in `test/scry/build_test.clj` verify version, pom, jar path, included core files, and excluded repo/Kaocha files. Core, build, and Kaocha verification pass.
- Task `003-tools-build-jar` implementation review found no new actionable implementation-quality issues. Review note is recorded in `implementation.md`; core tests, focused build checks, and `clojure -T:build jar` were rerun successfully.
- Task `003-tools-build-jar` test review found one actionable test-quality issue: focused build checks do not cover required clear failure behavior for Git version computation failures/invalid counts, and a review follow-up item was added to make the process boundary injectable/nullable and test those cases without mocks/stubs.
- Task `003-tools-build-jar` test-review follow-up is complete: `build/git-rev-count` now has an optional nullable process boundary, focused build tests cover non-zero Git exits and invalid non-numeric counts with clear `ex-info` assertions, and build/core verification passes. A subsequent test review found no new actionable test-quality issues; focused build checks, core tests, and `clojure -T:build jar` pass.
- Task `003-tools-build-jar` test-shaper follow-up is complete: focused build tests now assert the generated pom excludes the optional Kaocha dependency (`lambdaisland/kaocha`) as well as excluding Kaocha source entries from the jar. Focused build checks, `clojure -T:build jar`, and core tests pass.
- Task `003-tools-build-jar` latest test-shaper review found no new actionable test-quality issues; focused build checks pass and the review note is recorded in `implementation.md`.
- Task `003-tools-build-jar` docs review found one actionable docs issue: `CHANGELOG.md` Unreleased does not mention the new tools.build jar workflow/artifact; a follow-up item was added to `steps.md`.
- Task `003-tools-build-jar` docs-review follow-up is complete: `CHANGELOG.md` Unreleased now documents the `clojure -T:build jar` workflow for `org.hugoduncan/scry`, Git-derived `0.1.<git-revcount>` versions, and core-only packaging with optional Kaocha exclusion.
- Task `003-tools-build-jar` code-shaper review found no new actionable code-quality issues; focused build checks and core tests pass, and the review note is recorded in `implementation.md`.

## Active focus

- Open task `003-tools-build-jar`: latest code-shaper review found no new actionable code-quality issues after the docs follow-up.

## Useful links

- Project README: `README.md`
- Agent guidance: `AGENTS.md`
- Munera task plan: `munera/plan.md`
- Open Kaocha suite-selection task: `munera/open/002-kaocha-tests-edn-suite-selection/`
- Closed scoped-format task: `munera/closed/001-result-format-by-invocation-scope/`
- Mementum memories: `mementum/memories/`
- Mementum knowledge: `mementum/knowledge/`
