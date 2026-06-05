# Add quickdoc-based API docs

## Goal

Add generated API reference documentation for scry using [`borkdude/quickdoc`](https://github.com/borkdude/quickdoc), so users and agents can discover the intended public functions, options, and result helpers without reading source or relying only on README examples.

## Context

`scry` now has public artifacts on Clojars, public installation docs, CLI behavior, and an optional Kaocha adapter. The README explains common workflows, but there is no dedicated API reference generated from namespace and var docstrings. Quickdoc can turn Clojure docstrings into a maintainable Markdown reference that stays close to the source.

The source tree also contains implementation namespaces and public-looking helper vars that may not all be intended as stable user API. The docs task should curate the documented surface instead of accidentally presenting internal helper namespaces as public API.

## Scope

- Add a reproducible quickdoc generation path for this repository.
- Generate and commit a Markdown API reference at `doc/API.md`.
- Document the intended public API surface:
  - `scry.core` REPL/in-process entry points and inspection helpers.
  - `scry.cli` user-facing command-line/`clojure -X` API surface, especially `run` and the structured CLI outcome contract where appropriate.
  - `scry.kaocha` optional adapter API when the optional Kaocha classpath is present.
- Exclude implementation-only namespaces from the generated reference, including `scry.capture`, `scry.clojure-test`, and `scry.cli.results` unless implementation discovers a specific var there is intentionally public and should be documented.
- Curate internal helper vars in included namespaces using quickdoc-supported exclusion mechanisms, metadata, or minimal visibility/docstring changes as needed, so the generated docs reflect intended user-facing API rather than every implementation helper.
- Add or update a lightweight command for maintainers to regenerate the docs, preferably through `deps.edn` and/or `bb.edn` in line with the existing build/release workflow.
- Link the API reference from `README.md`.
- Update `AGENTS.md` if maintainers/agents need to know the regeneration or verification command.
- Add a `CHANGELOG.md` Unreleased note if the API docs are user-visible.

## Out of scope

- Publishing a documentation website.
- Replacing README workflow documentation with generated API docs.
- Redesigning public APIs or result shapes.
- Large docstring rewrites unrelated to making the generated API reference accurate.
- Documenting private/internal namespaces as stable public API.
- Changing runtime behavior, except for harmless metadata/docstring/visibility adjustments needed to curate documentation.

## Design notes

- Pin the quickdoc dependency to an explicit released version or git SHA; do not depend on a floating branch.
- Keep quickdoc and any documentation-generation tooling dependencies maintainer/docs-only: place them under a dedicated docs alias such as `:quickdoc`, a Babashka task that supplies docs-only `:extra-deps`, or an equivalent non-runtime mechanism. Do not add quickdoc/tooling dependencies to top-level `:deps`, the core or Kaocha published POM dependency metadata, or the runtime dependency surface of either artifact.
- Use `bb api-docs` as the single maintainer entry point for API docs. It should wrap the Clojure quickdoc invocation rather than making agents remember a long alias combination. `bb api-docs` regenerates/overwrites `doc/API.md`; `bb api-docs --check` regenerates to a temporary output or otherwise compares deterministic generated content and fails non-zero if the committed `doc/API.md` would change. Final verification should record `bb api-docs --check`; implementation may also record one explicit regeneration run.
- Implement the command with a docs-only quickdoc classpath, for example a `:quickdoc` alias invoked with the existing `:kaocha` alias (`clojure -M:quickdoc:kaocha ...`) or an equivalent docs-only mechanism. The effective generation classpath must include `src`, `src-kaocha`, and the Kaocha dependency so `scry.kaocha` can load, while keeping quickdoc/Kaocha out of core runtime deps and published core POM metadata.
- The generated API reference must be fully reproducible. `doc/API.md` should be treated as generated output with no required hand-edited sections after generation. Required prose such as the pre-1.0 public-alpha status note, the optional `scry.kaocha` adapter/classpath note, regeneration command, and relationship to README workflow docs must live in source-controlled generation configuration/code (for example a generator namespace or config map that supplies quickdoc intro/footer text, or deterministic pre/post-processing owned by the generator). A rerun of `bb api-docs` must recreate those notes exactly.
- If quickdoc cannot express the desired public surface without source annotations, use minimal `^:no-doc` metadata or private visibility changes only where they do not alter supported public behavior.

## Public API surface for generated docs

Generate documentation only for these namespaces and vars:

- `scry.core`:
  - Include `run`, `last-result`, `failures`, `failed-test`, `output`, and `report-string` as the user-facing REPL/in-process API.
  - Include `last-run` only as a documented advanced state holder because README already names it; prefer `last-result` in prose/examples.
  - Exclude private formatting/status helpers.
- `scry.cli`:
  - Include `run` as the supported `clojure -X` entry point and document the structured CLI outcome/non-zero `ex-info` contract there or in generated namespace prose.
  - Do not document `run-cli`, `main-outcome`, `parse-main-args`, `normalize-exec-opts`, `normalize-runner`, `usage`, `-main`, or other option-normalization/execution helpers as user API. They may remain callable implementation/test seams, but generated public API docs should hide them (for example with `^:no-doc`) unless a later task explicitly promotes them.
  - Document `clojure -M -m scry.cli` command usage in generated prose, not as a var-level API.
- `scry.kaocha`:
  - Include `run` as the optional adapter's public in-process runner.
  - Include `result->scry` as an advanced public conversion helper for callers that already have a raw Kaocha result and want scry's result shape.
  - Exclude all private config, suite-selection, progress, and conversion helpers.

Do not include implementation-only namespaces in generated docs, including `scry.capture`, `scry.clojure-test`, and `scry.cli.results`.

## Acceptance criteria

- `doc/API.md` exists, is generated by quickdoc, and is committed.
- A documented command regenerates `doc/API.md` reproducibly from source docstrings.
- Running the generation command on a clean tree leaves `doc/API.md` unchanged.
- The generated docs include intended public API for `scry.core`, `scry.cli`, and optional `scry.kaocha`.
- The generated docs omit implementation-only namespaces such as `scry.capture`, `scry.clojure-test`, and `scry.cli.results`.
- `README.md` links to the API reference.
- If maintainer workflow changes are added, `AGENTS.md` documents the command and final verification expectation.
- `CHANGELOG.md` Unreleased mentions the new API reference docs if user-facing docs changed.
- Appropriate verification is recorded in `implementation.md`, including quickdoc generation/check, formatting/linting for any Clojure changes, and focused tests if source visibility or runtime-adjacent code changes are made.
