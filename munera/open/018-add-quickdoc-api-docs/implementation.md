# Implementation notes

Task created from user request to add `borkdude/quickdoc` based API docs. No implementation yet.

## 2026-06-04 architecture review

Reviewed `design.md` against AGENTS.md/README.md architecture guidance; `META.md` and `doc/architecture.md` are absent. The docs direction fits the public API/docs workflow and optional Kaocha boundary, but one architectural follow-up is needed: keep quickdoc/tooling dependencies out of top-level runtime deps and published artifact dependency surfaces.


## 2026-06-04 architecture follow-up

Completed the review-added design follow-up. `design.md` now requires quickdoc/tooling dependencies to remain docs-only through a dedicated alias, Babashka task, or equivalent non-runtime mechanism, explicitly forbids adding them to top-level `:deps` or published core/Kaocha dependency metadata, and requires the regeneration command to compose the optional Kaocha classpath when documenting `scry.kaocha`. Marked the corresponding `design-steps.md` item complete.

## 2026-06-04 ambiguity review

Reviewed `design.md` against README/AGENTS guidance and the public namespaces (`scry.core`, `scry.cli`, `scry.kaocha`) without reviewing `plan.md` or `steps.md`. Found new actionable ambiguities around the exact var-level API surface to expose (especially CLI helper fns and `scry.kaocha/result->scry`), the single regeneration/check command contract, and how generated-only API.md notes/intro text should be preserved across quickdoc reruns.

## 2026-06-04 ambiguity follow-up

Completed the three ambiguity-review design follow-up items added by the preceding review pass. `design.md` now pins the generated var-level API surface: `scry.core` documents the REPL/in-process API plus advanced `last-run`; `scry.cli` documents only the supported `clojure -X` `run` var while keeping parser/normalization/shared runner helpers out of generated public API docs; and `scry.kaocha` documents optional `run` plus advanced `result->scry`. The design now chooses `bb api-docs` as the single regeneration command and `bb api-docs --check` as the no-diff verification contract. It also requires all generated-only notes, including pre-1.0 public-alpha and optional Kaocha classpath guidance, to be emitted from source-controlled generation configuration/code rather than hand edits to `doc/API.md`. Marked the corresponding review-added `design-steps.md` items complete.
