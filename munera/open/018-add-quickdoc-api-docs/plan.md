# Plan

## Approach

Add generated API docs as a documentation/build-maintenance feature, not as a runtime behavior change. First confirm quickdoc's current invocation and exclusion mechanisms, then wire a pinned dependency and maintainer command. Generate `doc/API.md` from the intended public namespaces and link it from README.

Key decisions to preserve during implementation:

- Use quickdoc as the source-to-Markdown generator and commit the generated Markdown file.
- Treat `doc/API.md` as the canonical API reference path.
- Include `scry.core`, user-facing `scry.cli` entry points/outcome API, and optional `scry.kaocha` adapter docs.
- Exclude implementation namespaces (`scry.capture`, `scry.clojure-test`, `scry.cli.results`) from the generated reference.
- Keep the optional Kaocha dependency boundary explicit by running quickdoc with the `:kaocha` classpath only for documentation generation; core runtime deps must not start requiring Kaocha.
- Prefer a simple maintainer command (`bb api-docs` wrapping a `:quickdoc` alias, or a documented `clojure -M:quickdoc` command) and a checkable regeneration workflow.

## Risks

- Quickdoc may include public helper vars that are not intended API; mitigate by researching supported exclusion options and using minimal `^:no-doc`/visibility/docstring adjustments where appropriate.
- Loading `scry.kaocha` for docs requires the optional Kaocha dependency; ensure docs tooling includes the adapter classpath without moving Kaocha into core deps.
- Generated docs can become stale; add a regeneration/check workflow and document it for maintainers.
- Public API docs can imply stability. Include pre-1.0/public-alpha wording and avoid documenting internal namespaces.
- Source annotations for docs could accidentally change runtime/public behavior. If any non-doc source changes are needed, run focused tests for the touched surface.

## Slice order

1. **Quickdoc and API-surface orientation** — inspect quickdoc's documented CLI/API, choose the pinned dependency/version, identify supported namespace/var exclusion mechanisms, and audit current public vars in `scry.core`, `scry.cli`, and `scry.kaocha`.
2. **Tooling integration** — add the quickdoc alias/task with the optional Kaocha classpath as needed, choose the exact maintainer command, and make it easy to rerun locally.
3. **API curation and generation** — add any minimal source metadata/docstring/visibility updates needed for accurate docs, generate `doc/API.md`, and ensure internal namespaces/helpers are omitted.
4. **Documentation links and maintainer guidance** — link `doc/API.md` from README, update AGENTS.md with the regeneration/check command if a new command is added, and add a CHANGELOG Unreleased note for the user-visible docs.
5. **Verification and notes** — rerun the generation command and confirm no diff, run formatting/linting and focused tests when source files are touched, run `git diff --check`, and record commands/results and notable decisions in `implementation.md`.
