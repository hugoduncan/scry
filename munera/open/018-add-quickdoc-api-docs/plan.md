# Plan

## Approach

Implement the API reference as a generated documentation and maintainer-tooling change. Use `borkdude/quickdoc` from a pinned docs-only dependency path, expose one maintainer command (`bb api-docs`) to regenerate `doc/API.md`, and expose `bb api-docs --check` as the deterministic no-diff verification command.

Key decisions:

- Generate and commit `doc/API.md`; treat it as generated output with no required hand edits after generation.
- Keep quickdoc and documentation tooling out of runtime dependencies and published POM dependency metadata. Use a dedicated docs alias, a Babashka task with docs-only extra deps, or equivalent non-runtime mechanism.
- Compose the documentation generation classpath so `scry.core`, `scry.cli`, and optional `scry.kaocha` can load. This must include `src`, `src-kaocha`, and the Kaocha dependency for docs generation without making Kaocha a core runtime dependency.
- Drive all generated-only prose from source-controlled generator inputs: pre-1.0/public-alpha status, the optional Kaocha classpath note, the regeneration/check commands, the relationship to README workflow docs, and CLI examples using README-aligned aliases.
- Curate the generated API surface exactly:
  - `scry.core`: include `run`, `last-result`, `failures`, `failed-test`, `output`, `report-string`, and advanced `last-run`.
  - `scry.cli`: include only `run` as the public `clojure -X` entry point. Generated docs must show only the user-facing `[opts]` arity and document the structured outcome / non-zero `ex-info` contract. Hide `io-boundary` and helper entry points from generated docs through deterministic source metadata or a small private-helper refactor.
  - `scry.kaocha`: include optional `run` and advanced `result->scry`.
  - Exclude implementation-only namespaces (`scry.capture`, `scry.clojure-test`, `scry.cli.results`) and internal helpers.
- Prefer minimal source changes: docstrings, metadata such as `^:no-doc` / `:arglists`, or private helper extraction only when needed to make the generated docs accurate.
- Update user-facing and maintainer documentation surfaces after generation: README link, AGENTS maintainer command guidance, and CHANGELOG Unreleased note.

## Risks

- Quickdoc may expose helper vars or implementation arities by default. Mitigate with an explicit namespace/var allow-list plus minimal metadata/private-helper changes, then inspect the generated file.
- `scry.cli/run` currently has an implementation/test-seam arity. The generated docs must hide that seam; changing metadata should be checked for any reflection/lint or behavior side effects.
- Loading `scry.kaocha` for docs requires the optional Kaocha dependency. Keep this in docs-only tooling and verify build/POM dependency boundaries are not changed.
- Generated prose can drift or be lost if hand-edited. Keep all intro/footer/examples in generator code/config and verify `bb api-docs --check` on a clean final tree.
- Public API docs may imply stronger stability than intended. Include generated public-alpha / pre-1.0 wording and avoid documenting internal namespaces.
- If Clojure source or build/task files are touched, formatting/linting and focused loading/tests may be needed even though the task is documentation-oriented.

## Slice order

1. **Quickdoc orientation and pinned tooling choice** — inspect quickdoc usage/options, choose a pinned version or git SHA, and decide the generator shape that supports regeneration plus no-diff checking.
2. **API surface audit and curation design** — audit public vars and arglists in `scry.core`, `scry.cli`, and `scry.kaocha`; choose the exact metadata/options/private-helper changes needed to match the stable design.
3. **Docs tooling integration** — add the docs-only quickdoc classpath and `bb api-docs` / `bb api-docs --check` task implementation, including deterministic output/check behavior and optional Kaocha classpath composition.
4. **Generated API content** — add source-controlled generator prose/config, apply minimal docstring/metadata/visibility updates, generate `doc/API.md`, and inspect included/omitted namespaces, vars, arities, examples, and stability notes.
5. **Documentation links and maintainer guidance** — link `doc/API.md` from README, document regeneration/check expectations in AGENTS, and add a CHANGELOG Unreleased note.
6. **Verification and task notes** — run `bb api-docs --check`, `git diff --check`, formatting/linting for touched Clojure/build/task files, focused load/tests if runtime-adjacent files changed, and record decisions/results in `implementation.md`.
