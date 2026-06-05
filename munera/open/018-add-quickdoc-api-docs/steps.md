# Steps

## Slice 1 — Quickdoc and API-surface orientation

- [ ] Read quickdoc usage from `https://github.com/borkdude/quickdoc` or the installed dependency docs and record the chosen invocation model.
- [ ] Choose and record the pinned quickdoc dependency version or git SHA.
- [ ] Audit public vars in `scry.core`, `scry.cli`, and `scry.kaocha` and decide which are intended to appear in generated API docs.
- [ ] Decide the exclusion mechanism for internal namespaces and helper vars (`^:no-doc`, quickdoc options, or visibility changes).

## Slice 2 — Tooling integration

- [ ] Add a quickdoc generation alias/task with a classpath that can load `scry.core`, `scry.cli`, and optional `scry.kaocha`.
- [ ] Ensure the tooling does not add Kaocha to core runtime dependencies.
- [ ] Document the exact local regeneration command in the task notes before generating final docs.

## Slice 3 — API curation and generation

- [ ] Add any minimal docstring/metadata/visibility updates needed so quickdoc presents the intended public API only.
- [ ] Generate `doc/API.md` with quickdoc.
- [ ] Inspect `doc/API.md` and confirm it includes intended `scry.core`, `scry.cli`, and `scry.kaocha` API docs.
- [ ] Inspect `doc/API.md` and confirm it omits implementation namespaces such as `scry.capture`, `scry.clojure-test`, and `scry.cli.results`.
- [ ] Include or preserve wording that `scry.kaocha` is optional and APIs are pre-1.0/public alpha.

## Slice 4 — Documentation links and maintainer guidance

- [ ] Link the API reference from `README.md`.
- [ ] Update `AGENTS.md` with the quickdoc regeneration/check command if a new maintainer command is added.
- [ ] Add a `CHANGELOG.md` Unreleased note for the new API reference docs.

## Slice 5 — Verification and notes

- [ ] Rerun the quickdoc generation command on the final tree and confirm it leaves `doc/API.md` unchanged.
- [ ] Run `bb clj-fmt:check` if Clojure source/build/task files changed.
- [ ] Run `bb clj-kondo:lint` if Clojure source/build/task files changed.
- [ ] Run focused tests for any touched runtime-adjacent namespace if metadata/visibility/docstring changes could affect loading or public behavior.
- [ ] Run `git diff --check`.
- [ ] Record implementation decisions and verification results in `implementation.md`.
