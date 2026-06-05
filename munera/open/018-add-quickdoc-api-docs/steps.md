# Steps

## Slice 1 — Quickdoc orientation and pinned tooling choice

- [ ] Read quickdoc usage/options from the upstream documentation or installed dependency docs and record the invocation model in `implementation.md`.
- [ ] Choose the pinned quickdoc dependency version or git SHA and record the rationale in `implementation.md`.
- [ ] Identify quickdoc-supported mechanisms for namespace selection, var exclusion, custom prose, output path selection, and deterministic no-diff checking.
- [ ] Decide whether `bb api-docs --check` will generate to a temporary file, compare in memory, or use another deterministic no-diff mechanism.

## Slice 2 — API surface audit and curation design

- [ ] Audit public vars and docstrings in `src/scry/core.clj` and list which vars should appear in `doc/API.md`.
- [ ] Audit public vars, docstrings, and arglists in `src/scry/cli.clj` and list which vars/arity should appear in `doc/API.md`.
- [ ] Audit public vars and docstrings in `src-kaocha/scry/kaocha.clj` and list which vars should appear in `doc/API.md`.
- [ ] Choose the deterministic curation mechanism for hiding implementation namespaces and helper vars from generated docs.
- [ ] Choose how to hide the `scry.cli/run` `io-boundary` arity so generated docs show only `[opts]`.

## Slice 3 — Docs tooling integration

- [ ] Add the docs-only quickdoc dependency path through the plan-pinned `deps.edn` `:quickdoc` alias, adding `bb/` plus pinned quickdoc to the generator classpath and keeping tooling out of runtime dependencies.
- [x] Plan-review follow-up: align the docs-only dependency implementation with the plan-pinned `deps.edn` `:quickdoc` alias that adds `bb/` plus pinned quickdoc, rather than the older generic Babashka-task/equivalent dependency-path alternatives.
- [x] Choose and record the concrete source-controlled API-doc generator entry point/location, and ensure both `bb api-docs` and `bb api-docs --check` invoke that same generation path with any generator source path on the docs classpath.
- [ ] Add `bb api-docs` so it regenerates/overwrites `doc/API.md` from source-controlled inputs.
- [ ] Add `bb api-docs --check` so it fails non-zero when committed `doc/API.md` would change.
- [ ] Ensure the generation classpath includes `src`, `src-kaocha`, and the Kaocha dependency so `scry.kaocha` can load.
- [ ] Verify quickdoc/tooling dependencies are not added to top-level runtime deps or published core/Kaocha POM dependency metadata.
- [ ] Record the exact regeneration and check commands in `implementation.md` before final generation.

## Slice 4 — Generated API content

- [ ] Add source-controlled generator intro/footer/prose for pre-1.0 public-alpha status, optional Kaocha classpath guidance, README relationship, and regeneration/check commands.
- [ ] Update docstrings, `:arglists`, `^:no-doc` metadata, or private helper structure minimally as needed for generated docs accuracy.
- [ ] Generate `doc/API.md` with `bb api-docs`.
- [ ] Inspect `doc/API.md` and confirm it includes `scry.core/run`, `last-result`, `failures`, `failed-test`, `output`, `report-string`, and advanced `last-run`.
- [ ] Inspect `doc/API.md` and confirm it includes only user-facing `scry.cli/run` with the `[opts]` arity and README-aligned CLI examples.
- [ ] Inspect `doc/API.md` and confirm it includes optional `scry.kaocha/run` and advanced `result->scry`.
- [ ] Inspect `doc/API.md` and confirm it omits `scry.capture`, `scry.clojure-test`, `scry.cli.results`, `scry.cli` helpers, and private/internal helper vars.
- [ ] Inspect `doc/API.md` and confirm it contains generated pre-1.0/public-alpha and optional Kaocha classpath notes.

## Slice 5 — Documentation links and maintainer guidance

- [ ] Link `doc/API.md` from `README.md`.
- [ ] Update `AGENTS.md` with the API-doc regeneration/check command and final verification expectation.
- [ ] Add a `CHANGELOG.md` Unreleased note for the new generated API reference docs.

## Slice 6 — Verification and task notes

- [ ] Run `bb api-docs --check` on the final tree and confirm it passes without changing `doc/API.md`.
- [ ] Run `bb clj-fmt:check` if Clojure source, generator, build, or task files changed.
- [ ] Run `bb clj-kondo:lint` if Clojure source, generator, build, or task files changed.
- [ ] Run focused loading/tests for any touched runtime-adjacent namespace, especially if metadata/visibility changes affect `scry.core`, `scry.cli`, or `scry.kaocha`.
- [x] Pin and run a concrete docs-tooling dependency-boundary verification, such as focused build/POM checks or generated POM inspection, proving quickdoc is absent from published core/Kaocha POM dependencies and `scry.kaocha` remains absent from the core artifact.
- [ ] Run `git diff --check`.
- [ ] Record implementation decisions, verification commands/results, and any non-blocking open questions in `implementation.md`.
