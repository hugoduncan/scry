# Steps

## Slice 1 — Characterize completed-entry contracts

- [x] Run the focused core and optional Kaocha test slices before implementation and record the baseline commands/results in `implementation.md`. — core: 145 tests/779 assertions; optional Kaocha: 33 tests/173 assertions.
- [x] Add a core runner regression proving `:progress-callback` fires once for each concrete execution, in execution order, after the var body and attributable `:each` teardown finish.
- [x] Add a core runner regression proving each callback receives the full unprojected canonical entry with final status, assertion summary, assertions, and captured `:each` setup/body/teardown output.
- [x] Verify the core callback contract tests pass through the development REPL and record any discovered contract differences in `implementation.md` before changing production code. — focused `scry.clojure-test-test`: 65 tests/159 assertions; commit `978de4a`.

## Slice 2 — Atomic sink foundation

- [x] Define the initial per-run result-sink state and completed-entry operation in `scry.cli.results`: concrete occurrence ordinal, failure-only required generation/snapshot, latest successful generation/path, latest exception, and completion order. Remaining reconciliation-specific state is Slice 3.
- [x] Add a one-entry publisher that applies the existing bounded EDN sanitizer, writes a unique temporary file inside `.scry-results/`, and atomically moves it with replacement to the unchanged deterministic final `.edn` filename.
- [x] Contain sanitizer, temporary-write, close, atomic-move, and per-attempt cleanup failures in sink state without throwing to a runner or replacing the original publication exception.
- [x] Make every concrete callback, including pass/unknown, advance its identity's occurrence ordinal; synchronously publish only concrete `:fail`/`:error` entries, and make synthetic callbacks artifact-requirement/filesystem no-ops.
- [x] Implement initial duplicate concrete completion semantics: each failing/erroring callback advances the required generation, a successful publish replaces the deterministic path, and later pass/unknown callbacks do not invalidate it. Reconciliation behavior remains Slice 3.
- [x] Add focused sink tests proving successful final files are readable EDN, retain detailed canonical entry data, use existing filename encoding, and leave no temporary files on successful publication.
- [x] Add focused sink tests proving no final `.edn` path becomes visible before a complete atomic move, temporary paths are never returned, and failed-attempt temporary files are best-effort removed. — direct publisher boundary test confirms final-file absence during temp serialization and cleanup after a contained write failure.
- [x] Add focused sink tests proving pass/unknown concrete callbacks advance occurrence ordinals without creating required failure generations, synthetic callbacks are no-ops, and duplicate fail/fail, fail/pass, and failed-newer-failure state remains distinct.
- [x] Run focused sink/CLI tests and inspect outcomes. — `clojure -M:test ... scry.cli-test`: 78 tests/620 assertions; `bb clj-fmt:check` passed.

## Slice 3 — Final reconciliation and diagnostics

- [x] Implement normal-return reconciliation by ordinally matching each identity's callback generations to same-identity canonical occurrences; retry an unpublished latest required generation from its matching failing/erroring occurrence or otherwise its retained callback snapshot, and write the latest failing/erroring unmatched occurrence for identities whose callbacks were missed. — reconciliation is sink-local; CLI lifecycle integration and diagnostic rendering remain Slice 4.
- [x] Preserve successfully published latest completion snapshots without rewriting them during reconciliation, including repeated concrete executions and later pass/unknown entries. — state-based regression verifies publication count and completion-time EDN survive a later pass; focused CLI tests: 82 tests/639 assertions.
- [x] Reuse existing whole-result assignment rules to write synthetic failing/erroring entries with deterministic collision handling during final reconciliation, reserving concrete filenames from canonical entries and callback-only identities with a successful artifact or unresolved required failure (but not pass/unknown-only callback identities) before assigning synthetic paths.
- [x] Build deduplicated `:result-files` in first canonical artifact occurrence order, then successful callback-only completion order, including only successfully published final `.edn` paths.
- [x] Implement deterministic unresolved-item ordering for normal reconciliation: concrete identities in canonical first-occurrence order, callback-only identities in completion order, then synthetic assignments.
- [x] Implement exception-path sink snapshots that return successful paths and unresolved concrete identities in completion order without attempting final reconciliation.
- [x] Refactor bounded diagnostic metadata construction so unresolved writes use `:incremental-result-file-writing` only when the runner throws before returning, use `:final-result-file-reconciliation` after any normal runner return (including canonical-validation or reconciliation-orchestration failure), and make `:failed-entry-count` count unique latest-required artifact identities. — CLI lifecycle integration completed in Slice 4.
- [x] Derive diagnostic `:message`, `:type`, `:root-type`, `:root-message`, optional `:first-failing-var`, and optional `:first-root-cause` from the first deterministically ordered unresolved item's latest applicable exception/snapshot. — CLI lifecycle integration completed in Slice 4.
- [x] Best-effort remove every remaining sink-owned temporary file after normal reconciliation while ignoring unknown temporary files. — per-attempt cleanup already removes every sink-created temporary path; no retained temp path is possible after a caught publisher failure.
- [x] Add state-level tests for a callback-ignoring runner, transient failure followed by successful retry, persistent partial failure with successful siblings, callback-only artifacts, callback-only concrete filename reservation against synthetic collisions, and deterministic diagnostic/result-file order. — callback-ignore/transient/order coverage landed in earlier slices; this pass adds callback-only concrete reservation and persistent partial diagnostic/sibling coverage.
- [x] Add duplicate-execution reconciliation tests proving ordinal callback/canonical matching selects the corresponding latest-generation snapshot (falling back to the retained callback snapshot when unmatched), an older readable artifact does not falsely reconcile a failed newer failure snapshot, and a later pass/unknown creates no new required artifact. — state-based test forces the second failure publication to fail, then verifies ordinal canonical retry and the callback-snapshot fallback.
- [x] Run focused sink/CLI tests through the development REPL and inspect diagnostic maps and written EDN directly. — command-line focused `scry.cli-test`: 80 tests/579 assertions; atomic files were read as EDN in sink regressions.

## Slice 4 — Core CLI integration

- [ ] Replace the end-only CLI writer flow with per-run sink creation after successful results-directory preparation and before runner invocation.
- [ ] Compose the CLI callback so sink handling always completes before terminal progress output is attempted, while preserving existing progress text, stream selection, and flush behavior.
- [ ] Keep terminal writer/flush exceptions outside sink containment and verify a pure progress-output failure follows the existing runner-error path without `:scry.cli/diagnostic-error`.
- [ ] On normal runner return, validate the complete canonical vector, reconcile the sink before human output, classify and print the existing summary/seed once, emit bounded write diagnostics when needed, and preserve outcome-kind/exit precedence.
- [ ] Define exception snapshots by lifecycle: a runner invocation exception uses no-reconciliation state with phase `:incremental-result-file-writing`; after any normal runner return, canonical-validation failures use no-reconciliation state with phase `:final-result-file-reconciliation`; reconciliation-orchestration and all later failures snapshot the state reached with that final phase, without recursive or second reconciliation.
- [ ] On catchable exceptions after sink creation, preserve snapshot `:result-files`, attach unresolved diagnostics with the lifecycle-appropriate phase, retain `:result nil`/`:summary nil`, and best-effort emit bounded failure-diagnostic/first-detail lines before the normal runner-error line plus a results-directory pointer only when an artifact survived, without allowing a secondary catch-path writer failure to replace the primary error or structured outcome.
- [ ] Preserve result-directory preparation failures as authoritative pre-run runner errors that do not create a sink or invoke the runner.
- [ ] Replace the task-025 `:write-result-files` test injection seam with focused sink/publication injection and keep all existing sanitizer/fallback expectations covered.
- [x] Add a core CLI test where a failing first var's following var reads the complete EDN before its body starts. — injected runner observes readable, detailed atomic entry before its next completion event.
- [x] Extend the core timing fixture/test so the immediate file contains assertion detail plus `:each` setup/body/teardown output under existing core ownership semantics. — runner-neutral integration entry covers the complete callback payload; real-fixture ownership remains covered by Slice 1 callback regression.
- [x] Add a core CLI test proving a passing first var creates no artifact before the next var starts.
- [ ] Add core CLI tests proving earlier artifacts remain readable/listed after runner invocation, malformed/missing canonical result, reconciliation-orchestration, and post-reconciliation output exceptions; assert incremental phase only when the runner throws before return, final-reconciliation phase after any normal return, no reconciliation before canonical validation, and no recursive/second reconciliation afterward. — runner-invocation preservation is covered; this pass adds malformed-canonical final-phase/no-retry coverage, while reconciliation-orchestration and post-output boundaries remain.
- [x] Add a combined unresolved incremental-write/runner-exception regression proving the outcome retains incremental diagnostic metadata and stderr emits bounded failure-diagnostic/first-detail lines, then the runner-error line, then the results-directory pointer only when a sibling artifact survived. — injected sink leaves one failure unpublished while preserving a sibling; the outcome and ordered stderr diagnostics are asserted.
- [ ] Add core CLI tests proving transient and persistent incremental write failures do not stop later tests or replace test-derived outcomes, and successful sibling files remain listed.
- [ ] Re-run result-format projection tests to prove incremental files use unprojected canonical entries independently of the public projection.
- [ ] Run `scry.clojure-test-test` and `scry.cli-test` as focused REPL slices and inspect `scry.core/last-result` until green.
- [ ] Commit the completed core sink/CLI slice and record the commit SHA and notable decisions in `steps.md`/`implementation.md`. — incremental integration coverage committed as `276b469`; full Slice 4 lifecycle and diagnostic cases remain.

## Slice 5 — Kaocha completed-leaf hook

- [ ] Add an adapter-owned Kaocha completion plugin with a leaf `:kaocha.hooks/post-test` hook that calls the callback with `testable->entry` and returns the leaf unchanged.
- [ ] Make the hook skip groups, skipped leaves, load-error/synthetic nodes, and runs without a callback.
- [ ] Read finalized merged capture output in `testable->entry`, defensively falling back to the still-readable capture-output buffer when finalized output is absent.
- [ ] Remove concrete `:end-test-var` callback emission and assertion counting from the reporter while retaining immediate synthetic suite/load-error progress.
- [ ] Normalize the active plugin list so the adapter completion plugin appears exactly once and last, after capture-output, filter, and all configured/user plugins.
- [ ] Ensure callback configuration reaches the completion hook without introducing a core load-time dependency on Kaocha or mutating returned leaves.
- [ ] Update the Kaocha callback docstring to state once-per-concrete-execution, synchronous, full canonical payload, execution ordering, finalized post-hook snapshot, and merged-output semantics.
- [ ] Add adapter tests proving callback entries are full canonical entries and equal the corresponding final canonical conversion for var, status, counts, assertions, and output.
- [ ] Add an adapter test with a preceding user `post-test` hook proving the completion callback observes that hook's count/history/output changes and the adapter plugin is last.
- [ ] Add adapter fixture coverage proving `:each` teardown is complete before callback and included in finalized merged output.
- [ ] Add adapter tests proving one callback per concrete leaf/execution, no duplicate from reporter events, and no callback for skipped/non-leaf nodes.
- [ ] Keep and strengthen load/suite-error tests proving synthetic progress still fires and is not treated as a concrete completion.
- [ ] Run focused `scry.kaocha-test` and `scry.cli-kaocha-test` checks with the `:kaocha` alias and inspect failures before proceeding.
- [ ] Commit the completed Kaocha hook slice and record the commit SHA and lifecycle findings in `steps.md`/`implementation.md`.

## Slice 6 — Kaocha CLI and interruption coverage

- [ ] Add a Kaocha CLI integration project where the first leaf fails and the next leaf verifies the first final `.edn` is already present and readable before its body runs.
- [ ] Verify the immediate Kaocha file contains finalized assertion counts/detail and merged setup/body/teardown stdout/stderr output with `:err` empty.
- [ ] Verify Kaocha synthetic load/suite errors still receive live progress and final synthetic artifacts only during reconciliation.
- [ ] Add a bounded child-process interruption fixture in which a first failure signals publication and a later var blocks; assert the published file is readable while blocked and remains readable after terminating the child.
- [ ] Guarantee child-process cleanup with bounded readiness/exit waits and `finally`; if safe platform-independent interruption is unavailable, implement deterministic blocked-run synchronization and document the limitation in `implementation.md`.
- [ ] Run the focused Kaocha adapter/CLI slice once, then run the interruption regression 10 consecutive times in one bounded invocation with no sleeps or leaked resources; if child interruption is unsupported, run the deterministic blocked-run fallback 10 times instead and record the platform limitation/fallback in `implementation.md`.

## Slice 7 — Compatibility regression pass

- [ ] Re-run and adjust existing robust sanitizer tests for cyclic values, Throwables, bounded strings/collections, and hostile diagnostic values against atomic one-entry publication.
- [ ] Re-run synthetic naming/collision tests and verify callback-time concrete reservations do not change existing synthetic filenames.
- [ ] Verify normal pass/fail/load-error/unknown/zero-test outcomes retain summary text, progress text, stderr pointers, result shapes, outcome kinds, and exit codes.
- [ ] Verify both `-m` and `-X` paths use incremental publication without changing option parsing, selection, or non-zero exception data.
- [ ] Verify nested core capture isolation, fixture semantics, assertion/output ownership, and behavior with no progress callback remain unchanged.
- [ ] Verify the core jar namespaces still load without Kaocha and all Kaocha-specific plugin code remains under `src-kaocha/`.
- [ ] Run `bb clj-fmt:check` and `bb clj-kondo:lint`; fix only task-related formatting/lint findings.

## Slice 8 — Documentation and final verification

- [ ] Update README CLI documentation with synchronous per-completed-failure publication, atomic final-file visibility, temporary-file consumer guidance, reconciliation, and durability limits.
- [ ] Update CHANGELOG Unreleased with core/Kaocha incremental publication, runner-error artifact preservation, atomic publication, retry, and diagnostic phases.
- [ ] Update AGENTS CLI guidance with the before-next-var guarantee, runner-error preserved `:result-files`, unresolved diagnostic phases, and final-file-only inspection guidance.
- [ ] Update the public `scry.cli/run` docstring with preserved runner-error files and the pinned `:final-result-file-reconciliation`/`:incremental-result-file-writing` diagnostic contract.
- [ ] Update changed core/Kaocha callback docstrings to document synchronous full canonical completed-entry semantics and duplicate execution behavior.
- [ ] Regenerate `doc/API.md` with `bb api-docs` and inspect the generated CLI and Kaocha sections for the required contract language.
- [ ] Run `bb api-docs --check` and the focused API-doc content regression command from `AGENTS.md`.
- [ ] Run the focused core CLI command-line check from `AGENTS.md`.
- [ ] Run the focused Kaocha adapter and Kaocha CLI command-line checks from `AGENTS.md`.
- [ ] Run at least one dedicated failing core CLI invocation and one failing Kaocha CLI invocation; inspect their outcome/exit behavior and readable `.scry-results/*.edn` artifacts.
- [ ] Run `bb test:core`, `bb test:kaocha`, and then `bb test`; record exact commands, counts, and results in `implementation.md`.
- [ ] Run final `bb clj-fmt:check`, `bb clj-kondo:lint`, and `bb api-docs --check`; record results in `implementation.md`.
- [ ] Review the final diff against every acceptance criterion and out-of-scope boundary in `design.md`, updating `steps.md` and append-only `implementation.md` with any final decision or deviation.
- [ ] Commit the documentation/final-verification slice and record its SHA in `steps.md`/`implementation.md`.
