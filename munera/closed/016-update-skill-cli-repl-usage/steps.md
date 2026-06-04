# Steps

## Slice 1 — Orient and confirm scope

- [x] Reread `munera/open/016-update-skill-cli-repl-usage/design.md` and confirm there are no unresolved ambiguities before implementation.
- [x] Read current `SKILL.md` REPL/result/Kaocha sections and identify where CLI/final-verification guidance is missing.
- [x] Read `README.md` command-line and optional Kaocha CLI usage sections for the authoritative CLI contract.
- [x] Read `AGENTS.md` guidance on REPL during development versus command-line final verification.
- [x] Confirm the implementation change remains limited to top-level `SKILL.md` unless a direct contradiction is discovered.

## Slice 2 — Update REPL/API framing

- [x] Revise `SKILL.md` introductory/usage wording so REPL/API usage and command-line usage have distinct purposes.
- [x] Decide whether the `SKILL.md` frontmatter (`description`, `lambda`, and tags) must be updated with the body text so the portable skill does not remain in-process-only after adding CLI/final-verification guidance.
- [x] Update the `SKILL.md` frontmatter (`description`, `lambda`, and tags) during the body edit so it reflects both REPL/in-process structured inspection and CLI/final-verification usage.
- [x] Preserve structured result shape guidance, `:results`/`:failures` guidance, and direct inspection examples using `scry/last-result`, `scry/failures`, `scry/failed-test`, and `scry/output`.
- [x] Preserve targeted REPL examples for discovered runs, directories, namespace patterns, explicit namespaces, and explicit vars.
- [x] Add guidance that REPL/in-process runs are preferred for interactive debugging and iteration.

## Slice 3 — Add CLI workflow guidance

- [x] Add core CLI examples for `clojure -M:test -m scry.cli` and `clojure -X:test scry.cli/run`.
- [x] Add at least one focused core CLI selector example, such as `--var my.project-test/specific-test` and/or `:vars '[my.project-test/specific-test]'`.
- [x] Add optional Kaocha CLI examples using the `:test:kaocha` alias and `--runner kaocha` / `:runner :kaocha`.
- [x] Document that CLI progress prints `.` for passing vars on stdout and failing/erroring unqualified var names on stderr.
- [x] Document that the CLI prints a summary after the run.
- [x] Document that `.scry-results/` is cleared/recreated at run start and contains namespace-prefixed EDN files for failing/erroring vars.
- [x] Document that CLI exits non-zero for failures, errors, unknown status, runner/argument errors, or zero executable tests.
- [x] State that command-line runs are preferred for final verification and CI-style status, while structured artifacts should be used instead of scraping progress text.
- [x] Specify how agents should inspect failure details after a CLI run without scraping terminal text, including `.scry-results/*.edn` for `-m` runs and the successful return map / non-zero `ex-info` data for `-X` runs.

## Slice 4 — Verify and record

- [x] Reread `SKILL.md` for accuracy, concision, and acceptance-criteria coverage.
- [x] Confirm the updated skill does not contradict `README.md` or `AGENTS.md` workflow guidance.
- [x] Confirm no runtime code or public API/result-shape changes were made.
- [x] Record documentation-only verification and any notable decisions in `implementation.md`.
