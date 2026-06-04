# Plan

## Approach

Make a documentation-only update to the top-level `SKILL.md`. Use `README.md` and `AGENTS.md` as the source of truth for current behavior, then distill only the workflow-level guidance needed by a portable agent skill.

Key decisions:

- Keep `scry.core/run` and related helpers as the REPL/in-process path for interactive debugging, targeted reruns, and direct structured-map inspection through `scry/last-result`, `:results`, and `scry/failures`.
- Add `scry.cli` as the command-line path for final verification and CI-style process status, including `-m` and `-X` entry points, live progress, `.scry-results/` EDN failure files, summaries, and exit-code behavior.
- Update `SKILL.md` frontmatter as part of the body edit: revise `description`, `lambda`, and tags so the portable skill is no longer framed as in-process-only and instead covers both REPL/in-process structured inspection and CLI/final-verification usage.
- Make clear that CLI human output is for progress/status; agents should still prefer structured artifacts over scraping terminal text:
  - For `-m` runs, use the process exit code and printed summary for status, then inspect `.scry-results/*.edn` for failing/erroring var details; passing runs may leave `.scry-results/` empty.
  - For `-X` runs, inspect the successful returned outcome map directly; on non-zero outcomes catch/read the thrown `ex-info` data (`:exit-code`, `:summary`, `:error`, and `:outcome`) rather than scraping progress text.
- Keep optional Kaocha CLI examples behind the `:test:kaocha` classpath/alias so core users do not infer a required Kaocha dependency.
- Do not change runtime code, README, AGENTS, tests, or public result/CLI contracts unless a direct contradiction is discovered.

## Risks

- `SKILL.md` could become too long if it duplicates README-level detail; keep examples copyable but focused and defer exhaustive flag coverage to help/README.
- CLI documentation could accidentally imply terminal-output scraping is acceptable; explicitly distinguish progress/status text from structured result artifacts.
- Kaocha CLI documentation could imply Kaocha is required for core usage; keep the optional adapter boundary explicit.
- Because this is documentation-only, verification is consistency/reread based rather than runtime test execution.

## Slice order

1. **Orient and confirm scope** — reread `design.md`, current `SKILL.md`, and the relevant `README.md`/`AGENTS.md` workflow sections; confirm the design is complete and the change remains `SKILL.md`-only.
2. **Update REPL/API framing** — revise existing skill wording so REPL/in-process usage is clearly positioned for interactive debugging, targeted reruns, and structured result inspection while preserving current result-shape guidance.
3. **Add CLI workflow guidance** — add focused command-line examples for core `-m` and `-X`, optional Kaocha CLI examples, progress/result-file/summary behavior, and non-zero exit semantics.
4. **Verify and record** — reread the updated skill against acceptance criteria and source docs, ensure it remains concise and non-contradictory, then record documentation-only verification in `implementation.md`.
