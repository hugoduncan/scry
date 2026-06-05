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
