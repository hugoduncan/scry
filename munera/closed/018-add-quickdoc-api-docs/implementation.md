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

## 2026-06-04 inconsistency review

Reviewed `design.md` against README/AGENTS guidance and the public source namespaces (`scry.core`, `scry.cli`, `scry.kaocha`) without reviewing `plan.md` or `steps.md`. Found two actionable inconsistencies: the intended generated CLI API surface says `scry.cli/run` should be documented only as the public `clojure -X` entry point, but its current public arglist includes the `io-boundary` test seam that quickdoc would expose; and the design/source wording for main-style CLI usage uses alias-less `clojure -M -m scry.cli`, while README installation and CLI examples consistently require the test/classpath alias form such as `clojure -M:test -m scry.cli`.

## 2026-06-04 inconsistency follow-up

Completed the two design follow-up items added by the inconsistency review. `design.md` now keeps `scry.cli/run` as the only generated CLI var but explicitly requires quickdoc to show only the user-facing `[opts]` arity, treating the `io-boundary` arity as an implementation/test seam hidden via deterministic source-controlled generation input such as `:arglists '([opts])` metadata or a private helper refactor. It also now requires generated main-style CLI examples to use README-aligned alias forms such as `clojure -M:test -m scry.cli` and optional `clojure -M:test:kaocha -m scry.cli --runner kaocha ...`, with any source docstrings/generation config updated rather than hand-editing generated output. Marked both review-added `design-steps.md` items complete.

## 2026-06-04 plan ambiguity review

Reviewed `plan.md` and `steps.md` against the stable `design.md`, prior implementation notes, README/AGENTS/CHANGELOG, `deps.edn`/`bb.edn`, and the public namespaces `scry.core`, `scry.cli`, and `scry.kaocha`. Found two new actionable ambiguities: the plan does not pin the concrete source-controlled generator entry point/classpath that both `bb api-docs` and `bb api-docs --check` will share, and the docs-only dependency-boundary verification is not concrete enough to prove quickdoc stays out of published POM/runtime surfaces. Added follow-up steps for both.

## 2026-06-04 plan-ambiguity follow-up

Completed the two review-added actionable follow-up items in `steps.md`.

- Pinned the source-controlled API-doc generator entry point to `bb/scry/api_docs.clj` (`scry.api-docs`). The eventual docs-only `:quickdoc` alias should add `bb/` and pinned quickdoc to the classpath, and both `bb api-docs` and `bb api-docs --check` should invoke the same path via `clojure -M:quickdoc:kaocha -m scry.api-docs`, forwarding `--check` for no-diff verification.
- Pinned the concrete dependency-boundary verification to the focused build/POM check command: `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.build-test)] (when-not (ct/successful? r) (System/exit 1)))"`. This proves the published POM dependency surfaces remain curated and that the core artifact excludes `scry.kaocha`; the eventual quickdoc implementation should also inspect the `deps.edn` diff to confirm `borkdude/quickdoc` appears only under the docs-only `:quickdoc` alias and not top-level `:deps`.

Verification run now:

- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.build-test)] (when-not (ct/successful? r) (System/exit 1)))"` — pass, 6 tests, 176 assertions, 0 failures, 0 errors.

Marked both newly added follow-up items complete in `steps.md`. Older unchecked implementation steps predate the preceding review pass and remain pending for the implementation pass.

## 2026-06-04 plan inconsistency review

Reviewed `plan.md` and `steps.md` against the stable `design.md`, prior implementation notes, `deps.edn`/`bb.edn`, README/AGENTS/CHANGELOG, and the relevant public source/build tests. Found one new actionable inconsistency: after the plan-ambiguity follow-up, `plan.md` pins a specific docs-only `deps.edn` `:quickdoc` alias that adds `bb/` plus pinned quickdoc and drives `clojure -M:quickdoc:kaocha -m scry.api-docs`, but `steps.md` Slice 3 still allowed the older generic dependency-path alternatives (`deps.edn` alias, Babashka task, or equivalent). Added an unchecked follow-up item to align the implementation step with the plan-pinned `:quickdoc` alias.

## 2026-06-04 plan-inconsistency follow-up

Completed the newly added actionable follow-up item from the plan inconsistency review. `steps.md` now aligns Slice 3 with the plan-pinned docs-only dependency implementation: the eventual tooling integration should add a `deps.edn` `:quickdoc` alias that places `bb/` plus pinned quickdoc on the generator classpath, rather than leaving the older generic Babashka-task/equivalent dependency-path alternatives in the implementation checklist. Marked the review-added follow-up item complete. No code/docs implementation was otherwise performed because older unchecked steps predate the preceding review pass.

## 2026-06-04 implementation pass

Implemented the quickdoc API docs slice end to end.

Quickdoc orientation and tooling decisions:

- Upstream quickdoc supports Clojure CLI/Babashka invocation via `quickdoc.api/quickdoc`; the API accepts `:source-paths`, `:outfile`, `:github/repo`, `:git/branch`, `:source-uri`, `:toc`, `:var-links`, `:overrides`, and filename transform options, and returns generated markdown under `:markdown`.
- Chose the upstream documented pinned dependency `io.github.borkdude/quickdoc` at git tag `v0.2.6`, SHA `ce86780`, because it is the latest release shown in upstream quickdoc docs and has the needed static-analysis override support.
- Curation uses source-controlled quickdoc options in `bb/scry/api_docs.clj`: explicit file `:source-paths` limit generation to `src/scry/core.clj`, `src/scry/cli.clj`, and `src-kaocha/scry/kaocha.clj`; `:overrides` hides all public `scry.cli` helper vars except `run`; a metadata override supplies `scry.cli/run` `:arglists '([opts])` so the implementation/test `io-boundary` arity is not documented.
- `bb api-docs --check` compares generated markdown in memory against committed `doc/API.md`, failing non-zero without rewriting the file.

API surface audit:

- `scry.core`: generated docs include `run`, `last-result`, `failures`, `failed-test`, `output`, `report-string`, and advanced `last-run`.
- `scry.cli`: generated docs include only `run`, with README-aligned `clojure -X:test ...` and `clojure -M:test ...` examples and structured non-zero `ex-info` contract prose. Helpers such as `run-cli`, `main-outcome`, parsers, normalizers, `usage`, and `-main` are intentionally hidden from the generated reference.
- `scry.kaocha`: generated docs include optional `run` and advanced `result->scry`.
- Generated docs omit implementation-only namespaces (`scry.capture`, `scry.clojure-test`, `scry.cli.results`) and all CLI helper vars.

Changes made:

- Added docs-only `deps.edn` `:quickdoc` alias with `bb/` and pinned quickdoc; `bb api-docs` composes `:quickdoc:kaocha` so `scry.kaocha` can be documented without changing core runtime deps.
- Added `bb/scry/api_docs.clj` as the single generator/check entry point and `bb.edn` task wrapper.
- Generated and committed `doc/API.md` with reproducible intro prose covering pre-1.0 status, README relationship, regeneration/check commands, and optional Kaocha classpath guidance.
- Adjusted a few public docstrings to keep generated docs user-facing and avoid promoting implementation namespaces; no runtime behavior was changed.
- Linked `doc/API.md` from README, documented maintainer API-doc commands in AGENTS.md, and added a CHANGELOG Unreleased note.

Verification:

- `bb api-docs` — pass, regenerated `doc/API.md`.
- `bb api-docs --check` — pass, no changes required.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `clojure -M:test:kaocha -e "(require '[scry.core] '[scry.cli] '[scry.kaocha]) (println :loaded)"` — pass, all touched runtime-adjacent namespaces load.
- `clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run {:namespaces ['scry.capture-test 'scry.clojure-test-test 'scry.cli-test]})] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"` — pass, 49 tests, 480 pass, 0 fail, 0 error.
- `clojure -M:test:kaocha -e "(require '[clojure.test :as ct] '[scry.kaocha-test] '[scry.cli-kaocha-test]) (let [adapter-result (ct/run-tests 'scry.kaocha-test) cli-result (ct/run-tests 'scry.cli-kaocha-test)] (when-not (and (ct/successful? adapter-result) (ct/successful? cli-result)) (System/exit 1)))"` — pass, adapter 11 tests/58 assertions and CLI 4 tests/29 assertions.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.build-test)] (when-not (ct/successful? r) (System/exit 1)))"` — pass, 6 tests, 176 assertions, 0 failures, 0 errors. This keeps the published core/Kaocha POM dependency boundary verified.
- Dependency-boundary inspection: `grep -n "quickdoc" deps.edn` shows quickdoc only under `:quickdoc`; `grep -n "quickdoc" build.clj` and `grep -R "quickdoc" -n target/classes target/*.pom` found no runtime/build/POM quickdoc references.
- `git diff --check` — pass.

No non-blocking open questions remain for this task.

## 2026-06-04 implementation review

Reviewed the quickdoc generator, generated `doc/API.md`, public docstring changes, dependency/tooling boundary, README/AGENTS/CHANGELOG updates, and task artifacts against the stable design and plan. Re-ran `bb api-docs --check`, touched namespace loading, and focused build/POM checks successfully. Found one actionable issue: generated `scry.cli/run` prose omits the `:error` key from the documented non-zero `ex-info` data even though README and `scry.cli/non-zero-exception` include it. Added a follow-up step.

## 2026-06-04 implementation-review follow-up

Completed the review-added documentation-contract follow-up. Updated `bb/scry/api_docs.clj` so the generated `scry.cli/run` non-zero `ex-info` contract lists `:error` alongside `:summary` and `:outcome`, then regenerated `doc/API.md` with `bb api-docs`. Marked the follow-up step complete.

Verification:

- `bb api-docs --check` — pass, generated API docs are up to date.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `git diff --check` — pass.

## 2026-06-04 follow-up implementation review

Reviewed the completed quickdoc follow-up plus the original generator/tooling/docs changes against the task design and plan. `scry.cli/run` generated prose now matches the non-zero `ex-info` data contract, the generated API surface remains curated, quickdoc remains docs-only, and no new actionable implementation-quality issues were found. No follow-up steps were added.

Verification rerun:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:kaocha -e "(require '[scry.api-docs] '[scry.core] '[scry.cli] '[scry.kaocha]) (println :loaded)"` — pass.
- `clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run {:namespaces ['scry.capture-test 'scry.clojure-test-test 'scry.cli-test]})] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"` — pass, 49 tests, 480 pass, 0 fail, 0 error.
- `clojure -M:test:kaocha -e "(require '[clojure.test :as ct] '[scry.kaocha-test] '[scry.cli-kaocha-test]) (let [adapter-result (ct/run-tests 'scry.kaocha-test) cli-result (ct/run-tests 'scry.cli-kaocha-test)] (when-not (and (ct/successful? adapter-result) (ct/successful? cli-result)) (System/exit 1)))"` — pass, adapter 11 tests/58 assertions and CLI 4 tests/29 assertions.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.build-test)] (when-not (ct/successful? r) (System/exit 1)))"` — pass, 6 tests, 176 assertions, 0 failures, 0 errors.
- Dependency-boundary grep — quickdoc appears only under `deps.edn` `:quickdoc`; no quickdoc references found in `build.clj` or generated target POM/class outputs.
- `bb clj-fmt:check` — pass.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `git diff --check` — pass.

## 2026-06-04 test review

Reviewed the task test/verification surface against the design, generated API docs, generator, public namespaces, and dependency-boundary checks. Found one actionable test-quality issue: `bb api-docs --check` proves `doc/API.md` is reproducible, but no automated focused test asserts the curated API-doc content contract (included public vars/arities/prose and omitted implementation namespaces/helpers), so a generator/source/doc change could preserve reproducibility while regressing the documented public surface.

## 2026-06-04 test-review follow-up

Completed the review-added API-doc content regression follow-up. Added `test-quickdoc/scry/api_docs_test.clj` plus a focused `deps.edn` `:quickdoc-test` alias for generator/content contract tests that load the source-controlled generator through the docs/optional-Kaocha classpath and assert:

- committed `doc/API.md` matches `scry.api-docs/generated-markdown`;
- the generated reference includes the curated `scry.core` public vars (`run`, `last-result`, `failures`, `failed-test`, `output`, `report-string`, and advanced `last-run`);
- `scry.cli` documents only the user-facing `run` entry point with the `[opts]` arity, README-aligned `-X`/`-M:test` examples, and the structured non-zero contract keys;
- optional `scry.kaocha` docs include `run` and advanced `result->scry`; and
- implementation namespaces, CLI helper vars, and the hidden `io-boundary` arity are omitted.

Updated `bb.edn` formatting/lint tasks to include the new focused test path.

Marked the test-review follow-up step complete.

Verification:

- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? r) (System/exit 1)))"` — pass, 1 test, 37 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `bb api-docs --check` — pass, generated API docs are up to date.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.build-test)] (when-not (ct/successful? r) (System/exit 1)))"` — pass, 6 tests, 176 assertions, 0 failures, 0 errors.
- `git diff --check` — pass.

## 2026-06-04 follow-up test review

Reviewed the API-doc content regression tests after the prior test-review follow-up. The focused test now covers reproducibility, curated public vars/arities, CLI examples/non-zero keys, optional Kaocha surface, and omitted helper namespaces; `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? r) (System/exit 1)))"` and `bb api-docs --check` pass. Found one new actionable test-quality gap: the focused content regression does not assert the generated intro/prose contract for the pre-1.0 public-alpha note, README relationship, and regeneration/check commands, so those required notes could be removed from the generator and docs while the current test still passes.

## 2026-06-04 follow-up test-review implementation

Completed the newly added follow-up test-review item. Extended `test-quickdoc/scry/api_docs_test.clj` so the focused API-doc content regression now asserts the generated intro/prose contract for:

- the initial public alpha / pre-1.0 stability note;
- the README relationship for installation, workflow examples, and CLI usage;
- the `bb api-docs` regeneration command; and
- the `bb api-docs --check` committed-doc check command.

The existing optional Kaocha classpath prose assertion remains covered by the same focused content test. Marked the follow-up step complete.

Verification:

- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [r (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? r) (System/exit 1)))"` — pass, 1 test, 44 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `bb api-docs --check` — pass, generated API docs are up to date.
- `git diff --check` — pass.


## 2026-06-04 latest test review

Reviewed the focused API-doc content regression, generator/check path, docs workflow guidance, and CI/maintainer verification surface. Existing API-doc tests are well-formed, avoid mocks/stubs, and cover the curated public vars/arities/prose plus omitted implementation namespaces; focused API-doc content tests, `bb api-docs --check`, and lint pass. Found one new actionable test-quality issue: the focused `:quickdoc-test` regression is not wired into CI or maintainer API-doc verification guidance, so curated API-doc surface regressions can pass the normal documented/CI checks unless a reviewer remembers the bespoke command.

## 2026-06-04 latest test-review follow-up

Completed the newly added follow-up item from the latest test review. Wired the focused API-doc content regression into both the automated and documented maintainer verification paths:

- `.github/workflows/ci.yml` now has a `Check generated API docs` step after formatting/linting that runs `bb api-docs --check` and the focused `clojure -M:quickdoc:quickdoc-test:kaocha ... scry.api-docs-test` regression command.
- `AGENTS.md` CI guidance now names the API-doc check/content regression as part of CI.
- `AGENTS.md` Maintainer API docs workflow now requires both `bb api-docs --check` and the focused API-doc content regression before handing off changes affecting public API docs, and explains that the regression protects the curated public surface, required generated prose, and omitted helpers.

Verification:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 44 assertions, 0 failures, 0 errors.
- `mise exec -- actionlint .github/workflows/ci.yml` — pass.
- `git diff --check` — pass.

## 2026-06-04 follow-up test review

Reviewed the API-doc content regression, CI wiring, focused build/POM checks, `deps.edn`, and generator/task integration against the task design. The focused API-doc tests are well-formed, run through real quickdoc generation, avoid mocks/stubs, and cover the curated surface/prose omissions now wired into CI. Found one new actionable test-quality issue: the docs-only dependency boundary for quickdoc is still protected only by manual diff/grep plus generic build/POM checks that assert the Kaocha boundary, not by an automated focused assertion that quickdoc remains only under the `:quickdoc` alias and absent from runtime deps/published POMs/artifacts.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 44 assertions, 0 failures, 0 errors.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 6 tests, 176 assertions, 0 failures, 0 errors.

## 2026-06-04 dependency-boundary test-review follow-up

Completed the review-added dependency-boundary regression item. Added focused coverage to `test/scry/build_test.clj` that asserts:

- `io.github.borkdude/quickdoc` appears in `deps.edn` only at `[:aliases :quickdoc :extra-deps io.github.borkdude/quickdoc]`, keeping it out of top-level runtime deps and other aliases; and
- generated core and Kaocha release artifacts omit quickdoc from filesystem POMs, jar-embedded POMs, and packaged jar entries.

Marked the follow-up step complete in `steps.md`.

Verification:

- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 183 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `bb api-docs --check` — pass, generated API docs are up to date.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 44 assertions, 0 failures, 0 errors.
- `git diff --check` — pass.

## 2026-06-04 follow-up test review

Reviewed the post-follow-up API-doc test surface against the task design, generator, `deps.edn`, CI wiring, and focused build/POM boundary tests. Existing tests are well-formed and cover reproducibility, curated generated content, required prose, CI/maintainer integration, and quickdoc absence from runtime/POM/artifact surfaces without mocks or stubs. Found one new actionable test-quality issue: the design requires quickdoc to be pinned to an explicit released version or git SHA, but the automated boundary regression only checks where quickdoc appears, not that the dependency coordinate remains non-floating/pinned.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 44 assertions, 0 failures, 0 errors.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 183 assertions, 0 failures, 0 errors.

## 2026-06-04 quickdoc pinning test-review follow-up

Completed the review-added quickdoc pinning regression item. Extended `test/scry/build_test.clj` so the focused quickdoc dependency-boundary test now also asserts the `:quickdoc` alias dependency spec is pinned to either an explicit non-floating Maven release version or a git tag plus SHA, and rejects branch-only/floating-style coordinates. The current quickdoc spec remains docs-only at `[:aliases :quickdoc :extra-deps io.github.borkdude/quickdoc]` with `:git/tag "v0.2.6"` and `:git/sha "ce86780"`. Marked the follow-up step complete in `steps.md`.

Verification:

- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `bb api-docs --check` — pass, generated API docs are up to date.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 44 assertions, 0 failures, 0 errors.
- `git diff --check` — pass.

## 2026-06-04 exact-surface test review

Reviewed the focused API-doc content regression, generator allow-listing, `deps.edn` quickdoc pin/boundary checks, CI wiring, and current generated `doc/API.md`. Existing tests are well-formed, use real generation/build paths without mocks/stubs, and cover reproducibility, required prose, CLI arity/helper omissions, optional Kaocha presence, CI wiring, and quickdoc dependency boundaries. Found one new actionable test-quality issue: the content regression asserts that the required `scry.core` and `scry.kaocha` vars are present, but does not assert the exact allowed var-anchor set for those included namespaces, so a newly public helper in either namespace could be accidentally published by quickdoc without failing the focused regression.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 44 assertions, 0 failures, 0 errors.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.

## 2026-06-04 exact-surface test-review follow-up

Completed the review-added exact included namespace API surface regression item. Strengthened `test-quickdoc/scry/api_docs_test.clj` with namespace-section extraction and exact var-anchor set assertions for the included `scry.core` and `scry.kaocha` namespaces. The focused API-doc content regression still asserts required anchors are present, and now also fails if quickdoc publishes an additional public helper var in either namespace without an intentional test update. Marked the follow-up step complete in `steps.md`.

Verification:

- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 48 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `bb api-docs --check` — pass, generated API docs are up to date.
- `git diff --check` — pass.

## 2026-06-04 latest implementation review

Reviewed the post exact-surface follow-up implementation against the stable design and plan, including the quickdoc generator, generated API reference, focused API-doc regression, CI wiring, dependency-boundary checks, and public docs guidance. Found no new actionable implementation-quality issues; no follow-up steps were added.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 48 assertions, 0 failures, 0 errors.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `mise exec -- actionlint .github/workflows/ci.yml` — pass.
- `git diff --check` — pass.

## 2026-06-04 latest test review

Reviewed the current API-doc test surface against the stable design, generator, generated `doc/API.md`, CI wiring, and focused dependency-boundary checks. Verification during review passed: `bb api-docs --check`; focused API-doc content regression (1 test, 48 assertions); focused build/POM checks (7 tests, 184 assertions). Found one new actionable test-quality issue: the focused content regression now enforces exact var-anchor sets for `scry.core` and `scry.kaocha`, but not for `scry.cli`; a newly documented public helper in the included `scry.cli` namespace could slip through unless it matches the current explicit omission list.

## 2026-06-04 exact CLI surface test-review follow-up

Completed the newly added exact CLI API surface follow-up. Strengthened `test-quickdoc/scry/api_docs_test.clj` so the focused API-doc content regression now asserts the exact var-anchor set for the included `scry.cli` namespace is only `scry.cli/run`, matching the existing exact-surface checks for `scry.core` and `scry.kaocha`. This fails if quickdoc ever publishes a newly public CLI helper var without an intentional test update. Marked the review-added step complete in `steps.md`.

Verification:

- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 50 assertions, 0 failures, 0 errors.
- `bb api-docs --check` — pass.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `git diff --check` — pass.

## 2026-06-04 final test review

Reviewed the current API-doc test surface against the stable design, generated reference, generator, CI wiring, and focused dependency-boundary checks. The tests are well-formed, use real quickdoc/build paths without mocks or stubs, cover reproducibility, required generated prose, exact documented API surfaces for `scry.core`, `scry.cli`, and `scry.kaocha`, omitted implementation helpers/namespaces, CI/maintainer wiring, and quickdoc docs-only pin/dependency/artifact boundaries. Found no new actionable test-quality issues; no follow-up steps were added.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 50 assertions, 0 failures, 0 errors.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.

## 2026-06-04 test-shaper review

Reviewed the API-doc regression tests, build dependency-boundary checks, CI wiring, and generated-doc reproducibility checks through the test-shaper lens. Tests are focused on observable generated content and artifact/dependency boundaries, use real quickdoc/build paths, have meaningful exact-surface failures, and are wired into maintainer/CI verification. Found no new actionable test-quality issues; no follow-up steps were added.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 50 assertions, 0 failures, 0 errors.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.

## 2026-06-04 docs review

Reviewed `README.md`, `doc/API.md`, `CHANGELOG.md`, generated-doc generator/source docstrings, and AGENTS API-doc workflow guidance against the implemented task. Found one actionable documentation consistency issue: `README.md` still names `run-cli` outcomes as user-facing, while this task's curated public CLI API docs intentionally expose only `scry.cli/run` / `clojure -X` and hide `run-cli`; README should describe structured CLI / `-X` outcomes without promoting that helper.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 50 assertions, 0 failures, 0 errors.

## 2026-06-04 docs-review follow-up

Completed the newly added docs-review follow-up. Updated `README.md` CLI outcome wording so it no longer names `run-cli` as user-facing API; the public docs now describe structured CLI outcomes and `:scry.cli/outcome-kind`, keeping README aligned with generated `doc/API.md` where the curated CLI surface is `scry.cli/run` for `clojure -X`.

Marked the docs-review follow-up step complete in `steps.md`.

Verification:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 50 assertions, 0 failures, 0 errors.
- `git diff --check` — pass.

## 2026-06-04 follow-up docs review

Reviewed `README.md`, generated `doc/API.md`, `CHANGELOG.md`, AGENTS API-doc workflow guidance, the generator source, and public source docstrings against the implemented quickdoc API-doc task. Found one new actionable documentation issue: the generated `scry.core` namespace prose says `scry.kaocha` is loaded only when the `:kaocha` alias is present, which is true for repository development but too narrow for public users who load the optional adapter artifact/classpath. Added a follow-up step to update the source-controlled generated-doc input so the API reference uses artifact/classpath-oriented wording.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 50 assertions, 0 failures, 0 errors.

## 2026-06-04 optional Kaocha classpath docs-review follow-up

Completed the newly added docs-review follow-up. Updated the source-controlled `scry.core` namespace prose so generated `doc/API.md` says the optional Kaocha adapter is available when the adapter artifact or equivalent optional Kaocha classpath is present, instead of saying it is loaded only when the repository-local `:kaocha` alias is present. Regenerated `doc/API.md` with `bb api-docs` and strengthened the focused API-doc content regression to assert the artifact/classpath-oriented wording remains present.

Marked the follow-up step complete in `steps.md`.

Verification:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 51 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass, all source files formatted correctly.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `git diff --check` — pass.

## 2026-06-04 latest docs review

Reviewed `README.md`, generated `doc/API.md`, `CHANGELOG.md`, `AGENTS.md` API-doc guidance, source docstrings, and `bb/scry/api_docs.clj` against the implemented quickdoc task. Found no new actionable documentation issues; no follow-up steps were added.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 51 assertions, 0 failures, 0 errors.

## 2026-06-04 code-shaper review

Reviewed the quickdoc generator, generated API reference, API-doc content regression, build dependency-boundary checks, CI/task wiring, and touched public docstrings through the code-shaper lens. The implementation is locally comprehensible, keeps docs generation/config/test concerns separated, preserves the runtime/dependency boundaries, and uses focused exact-surface tests to make the curation invariant enforceable. Found no new actionable code-quality issues.

Verification during review:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 51 assertions, 0 failures, 0 errors.

## 2026-06-05 conservative public API follow-up

User requested a more conservative public API declaration and noted tests can use the `#'` idiom for private vars. Tightened public/private boundaries instead of relying on quickdoc allow-listing for public helper vars:

- Made `scry.cli` implementation/test seams private: `usage`, `normalize-runner`, `normalize-exec-opts`, `parse-main-args`, `run-cli`, and `main-outcome`.
- Extracted private `run-with-boundary` for tests of the `clojure -X` path's injected IO boundary, leaving public `scry.cli/run` with only the user-facing `[opts]` arity.
- Kept `scry.cli/-main` callable for `clojure -M:test -m scry.cli` but marked it `^:no-doc`, since entrypoint resolution still needs the var but generated API docs should not promote it.
- Made `scry.core/last-run` private and updated source/README/generated docs to present `last-result` as the public inspection helper.
- Made `scry.kaocha/result->scry` private so the optional adapter API docs now expose only `scry.kaocha/run`.
- Removed obsolete quickdoc `:no-doc` overrides for CLI helper vars that are now private; retained only the `scry.cli/run` doc/arglist override.
- Updated focused API-doc content regression exact-surface expectations and regenerated `doc/API.md`.
- Updated SKILL.md and AGENTS.md references from `run-cli` to structured CLI / `-X` outcomes.

Verification:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 51 assertions, 0 failures, 0 errors.
- `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 43 tests, 308 assertions, 0 failures, 0 errors.
- `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 4 tests, 29 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass after formatting `test/scry/cli_test.clj` with `bb clj-fmt:fix`.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run {:namespaces ['scry.capture-test 'scry.clojure-test-test 'scry.cli-test]})] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"` — pass, 49 tests, 480 pass, 0 fail, 0 error.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.
- `git diff --check` — pass.

## 2026-06-05 boundary hoisting follow-up

User asked whether CLI boundary creation could be hoisted into top-level entry points instead of passing nil/partial boundaries down. Simplified the CLI seam accordingly:

- `scry.cli/run-cli` is now single-arity and requires a complete boundary map.
- `run-with-boundary` and `main-outcome` are responsible for expanding nil/partial boundary overrides with defaults at entry/test-seam boundaries.
- Renamed lower-level parameters from `io-boundary` to `boundary` where they now require complete boundaries.
- Updated CLI and optional Kaocha CLI tests to construct complete boundaries before calling private `run-cli` via `#'`.
- Regenerated `doc/API.md` because source line anchors changed.

Verification:

- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 51 assertions, 0 failures, 0 errors.
- `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 43 tests, 308 assertions, 0 failures, 0 errors.
- `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 4 tests, 29 assertions, 0 failures, 0 errors.
- `bb clj-fmt:check` — pass.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run {:namespaces ['scry.capture-test 'scry.clojure-test-test 'scry.cli-test]})] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"` — pass, 49 tests, 480 pass, 0 fail, 0 error.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.
- `git diff --check` — pass.

## 2026-06-05 complete-boundary follow-up

User suggested hoisting boundary construction out of `run-with-boundary` as well. Completed that refinement:

- Renamed private boundary constructor to `complete-boundary` to distinguish construction from the complete boundary value.
- `run-with-boundary` now takes an already complete boundary and no longer accepts nil/partial overrides.
- `main-outcome` now takes an already complete boundary and no longer accepts nil/partial overrides.
- Public `run` and `-main` construct the default complete boundary at the true top-level entry points.
- Tests now call `complete-boundary` explicitly before invoking private `run-cli`, `run-with-boundary`, or `main-outcome` via `#'`.

Verification:

- `bb clj-fmt:check` — pass after formatting `test/scry/cli_test.clj` with `bb clj-fmt:fix`.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 43 tests, 308 assertions, 0 failures, 0 errors.
- `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 4 tests, 29 assertions, 0 failures, 0 errors.
- `bb api-docs --check` — pass after regenerating `doc/API.md` for source anchor changes.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 51 assertions, 0 failures, 0 errors.
- `clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run {:namespaces ['scry.capture-test 'scry.clojure-test-test 'scry.cli-test]})] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"` — pass, 49 tests, 480 pass, 0 fail, 0 error.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.
- `git diff --check` — pass.

## 2026-06-05 remove complete-boundary follow-up

User observed that `complete-boundary` was only needed for non-nil override merging in tests. Removed it from implementation code:

- Deleted private `scry.cli/complete-boundary`.
- Public `run` and process `-main` now call `default-boundary` directly.
- Tests own the override-merging convenience through local `test-boundary` helpers that merge `#'scry.cli/default-boundary` with overrides before invoking private seams.
- Regenerated `doc/API.md` for source anchor changes.

Verification:

- `bb clj-fmt:check` — pass after formatting `test/scry/cli_test.clj` with `bb clj-fmt:fix`.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 43 tests, 308 assertions, 0 failures, 0 errors.
- `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 4 tests, 29 assertions, 0 failures, 0 errors.
- `bb api-docs --check` — pass after regenerating `doc/API.md`.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 51 assertions, 0 failures, 0 errors.
- `clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run {:namespaces ['scry.capture-test 'scry.clojure-test-test 'scry.cli-test]})] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"` — pass, 49 tests, 480 pass, 0 fail, 0 error.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.
- `git diff --check` — pass.
- `grep -R "complete-boundary\\|defn- boundary" -n src test` — no matches.

## 2026-06-05 direct private var test-call follow-up

User pointed out that defining test vars with `@#'cli/...` snapshots the root value and can go stale if the implementation var is redefined. Fixed the tests to call private vars directly through the var invocation idiom instead of defining dereferenced aliases:

- Removed `def` aliases such as `(def run-cli @#'cli/run-cli)` and `(def normalize-exec-opts @#'cli/normalize-exec-opts)` from CLI tests.
- Replaced private helper calls with direct var invocation, e.g. `(#'cli/run-cli ...)`, `(#'cli/normalize-exec-opts ...)`, `(#'cli/run-with-boundary ...)`, and `(#'cli/main-outcome ...)`.
- Kept local `test-boundary` helpers, but they call `(#'cli/default-boundary)` at use time before merging overrides.
- Avoided comparing a private var value for help text; the parser test now checks returned `:help?` plus `:usage` content.

Verification:

- `bb clj-fmt:check` — pass after formatting `test/scry/cli_test.clj` and `test/scry/cli_kaocha_test.clj` with `bb clj-fmt:fix`.
- `bb clj-kondo:lint` — pass, 0 errors, 0 warnings.
- `clojure -M:test -e "(require '[scry.cli-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 43 tests, 309 assertions, 0 failures, 0 errors.
- `clojure -M:test:kaocha -e "(require '[scry.cli-kaocha-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.cli-kaocha-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 4 tests, 29 assertions, 0 failures, 0 errors.
- `bb api-docs --check` — pass.
- `clojure -M:quickdoc:quickdoc-test:kaocha -e "(require '[scry.api-docs-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.api-docs-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 1 test, 51 assertions, 0 failures, 0 errors.
- `clojure -M:test -e "(require '[scry.core :as scry]) (let [r (scry/run {:namespaces ['scry.capture-test 'scry.clojure-test-test 'scry.cli-test]})] (println (scry/report-string r)) (when-not (:pass? r) (System/exit 1)))"` — pass, 49 tests, 481 pass, 0 fail, 0 error.
- `clojure -M:test:build -e "(require '[scry.build-test :as t] '[clojure.test :as ct]) (let [result (ct/run-tests 'scry.build-test)] (when-not (ct/successful? result) (System/exit 1)))"` — pass, 7 tests, 184 assertions, 0 failures, 0 errors.
- `grep -R "@#'cli\\|^(def .*#'cli\\|complete-boundary" -n test/scry/cli_test.clj test/scry/cli_kaocha_test.clj src/scry/cli.clj` — no matches.
- `git diff --check` — pass.
