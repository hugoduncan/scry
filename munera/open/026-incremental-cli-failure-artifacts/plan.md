# Plan: incrementally publish CLI failure artifacts

## Approach

Implement the durability guarantee as a CLI-owned stateful sink that consumes the
same completed canonical entries used for terminal progress. Keep test execution
and final result classification authoritative: sink failures are recorded and
retried, never thrown into a runner or used to change the primary outcome.

### Runner-neutral callback boundary

- Keep `:progress-callback` as the single runner boundary. The CLI will compose a
  callback that sends every entry to the sink first and terminal progress second.
- Treat concrete callbacks as synchronous completion events. A failing/erroring
  callback must return only after atomic publication succeeds or the sink has
  recorded a contained failure. Passing/unknown concrete entries and synthetic
  entries do not trigger incremental filesystem writes.
- Regression-protect the core runner's existing post-`:each` timing and full,
  unprojected `capture/var-result` payload rather than changing its execution
  loop.
- Replace Kaocha's concrete `:end-test-var` reporter callback with an
  adapter-owned `:kaocha.hooks/post-test` plugin. Store the callback in the test
  plan/config, transform finalized leaves with the same `testable->entry`
  function used by final conversion, and return each leaf unchanged. Keep the
  reporter only for synthetic suite/load errors.
- Normalize the Kaocha plugin chain by removing any prior occurrence of the
  adapter completion plugin and appending it last after configured/user plugins
  and the ensured capture-output/filter plugins. Read finalized capture output,
  with the capture buffer as a defensive fallback.

### Stateful result sink and atomic publication

- Add a per-run sink in `scry.cli.results`, created only after
  `prepare-results-dir!` succeeds. Give it explicit operations for handling a
  callback, reconciling final canonical entries, and taking an exception-path
  snapshot; use a narrow injected construction/publication boundary in tests
  instead of global redefinition.
- Reuse `failure-entry?`, concrete identity/filename rules,
  `result-file-assignments`, and `edn-readable-data`. Publish one entry by
  sanitizing it, writing and closing a uniquely named sink-owned temporary file
  in `.scry-results/`, then moving it with `ATOMIC_MOVE` and
  `REPLACE_EXISTING` to the final `.edn` path. Unsupported or failed atomic moves
  are contained as diagnostic write failures; do not fall back to exposing a
  non-atomic final file.
- Track concrete identities independently from paths: first completion order,
  latest failing/erroring callback snapshot, a monotonically advancing required
  failure generation, latest successfully published generation, latest
  exception, successful final path, and sink-owned temporary paths. This makes a
  later failed failure snapshot remain unresolved even when an older readable
  artifact exists, while pass/unknown callbacks neither retract the file nor
  create a new requirement.
- Make duplicate concrete executions replace the same deterministic path. The
  latest successful failing/erroring completion snapshot wins; final
  reconciliation must not rewrite it merely because equivalent final canonical
  data is available.

### Reconciliation, ordering, and diagnostics

- On normal runner return, validate canonical entries and reconcile in canonical
  order. For each concrete identity whose latest required failure generation is
  unpublished, retry from its latest failing/erroring canonical entry when one
  exists; otherwise retry the retained callback snapshot. Write concrete
  failures missed because a runner ignored the callback, and assign/write
  synthetic failures with the existing whole-result collision rules.
- Build `:result-files` from the first occurrence of each successfully published
  artifact path in canonical assignment order, then append successful
  callback-only concrete paths in completion order. Deduplicate paths and never
  include temporary or failed paths.
- Preserve an already successful concrete completion snapshot during normal
  reconciliation. A failed newer failure generation is retried and may replace
  the older file; a later pass/unknown does not create a newer required
  generation.
- Refactor CLI diagnostic construction to consume the sink's deterministically
  ordered unresolved identities. After a normal return use phase
  `:final-result-file-reconciliation` and the latest retry exception. If a
  runner exception prevents reconciliation, use phase
  `:incremental-result-file-writing` and each identity's latest incremental
  exception. Count unique unresolved artifact identities and derive the first
  var/root cause from the first ordered unresolved item.
- Best-effort remove the current attempt's temporary file after a contained
  failure and all remaining sink-owned temporary files after reconciliation.
  Cleanup failures must not replace the publication diagnostic.

### CLI lifecycle and compatibility

- Restructure `run-cli` so the prepared directory and sink remain available to
  both the normal and runner-exception paths. Keep result-directory preparation
  authoritative and before runner invocation.
- On a normal return, retain canonical validation, outcome classification,
  summary/seed output, final reconciliation, failure-directory diagnostics, and
  outcome throwing semantics. Preserve the existing summary/output ordering
  while replacing the end-only bulk writer with sink reconciliation.
- On a catchable runner exception, return any successful incremental paths in
  completion order, report unresolved incremental diagnostics when present,
  and print the results-directory pointer only when at least one artifact
  survived. Keep `:result nil`, `:summary nil`, the existing stdout error-summary
  line, runner-error classification, and exit behavior.
- A sink failure is always contained before terminal progress runs. A terminal
  writer/flush failure remains outside sink containment and may follow the
  existing runner-error path; a pure progress failure adds no artifact
  diagnostic metadata.
- Keep canonical entry/file shapes, sanitizer bounds, filenames, synthetic
  collision behavior, result-format independence, runner loading boundaries,
  and the `-m`/`-X` public outcome shape stable.

### Verification strategy

Test observable boundaries rather than private call order:

- Core fixtures will let a following var inspect the prior var's readable file,
  including body and `:each` setup/teardown assertions/output, and prove passing
  vars do not write files.
- Sink/CLI state tests will cover atomic visibility, temporary-file exclusion and
  cleanup, duplicate generations, callback-only paths, callback-ignoring
  runners, transient retry, persistent partial failure, deterministic ordering,
  projection independence, synthetic collisions, and catchable runner errors.
- Kaocha tests will compare callback entries with final canonical entries, use a
  preceding user `post-test` hook to prove the adapter hook is last, verify
  finalized merged teardown output, and guard against reporter duplication while
  retaining synthetic load-error progress.
- Use a bounded child process with explicit readiness/cleanup to prove an
  already-published file remains readable while a later test blocks and after
  process termination. If the host cannot support safe child interruption, use
  the design's deterministic blocked-run synchronization fallback and record the
  limitation in `implementation.md`.

## Risks

- **Duplicate-execution reconciliation is stateful.** Path existence alone cannot
  distinguish an older successful artifact from a failed newer snapshot.
  Explicit generations and tests for fail/fail, fail/pass, and failed-rewrite
  sequences prevent false reconciliation.
- **Kaocha lifecycle coupling.** The guarantee depends on Kaocha 1.91.1392
  running leaf `post-test` hooks after result/history finalization and before the
  next leaf. Pin behavior with callback/final parity, teardown-output, and
  user-hook ordering tests so a future lifecycle change fails visibly.
- **Plugin ordering can drift during config processing.** Append the adapter
  plugin only after all runtime/config/CLI option transformations that affect
  the active plugin list, and assert it is present exactly once at the end.
- **Filesystem portability.** Some filesystems do not support atomic move. Such a
  failure must be reported through diagnostic metadata rather than weakened to a
  non-atomic publish; tests should avoid assuming POSIX-only behavior except in
  explicitly guarded cases.
- **Exception-path reporting can mask the original runner error.** Snapshotting
  and rendering sink state must be bounded and non-throwing where the existing
  diagnostic helpers are bounded; retain the runner exception as the primary
  `:error`.
- **Existing diagnostic tests inject the end-only writer.** Replace that private
  test seam deliberately with sink/publication injection while preserving all
  task-025 sanitizer and non-authoritative-diagnostic regressions.
- **Interruption tests can hang or leak processes.** Use readiness signals,
  bounded waits, forced cleanup in `finally`, and unique temporary projects.
- **Public docs are generated.** Update source docstrings first, regenerate
  `doc/API.md`, and run the generated-content check to avoid hand-edited drift.

No implementation blocker or unresolved design decision is known. Temporary-file
name details and the private sink API are implementation choices; only final
`.edn` names and the behavior pinned by `design.md` are contractual.

## Slice order

1. **Characterize completed-entry contracts.** Add focused core tests for full
   post-`:each` callback payload/timing and establish the current focused-suite
   baseline.
2. **Atomic sink foundation.** Implement one-entry atomic publication,
   sink-owned temporary cleanup, per-identity generation state, and focused
   sink tests.
3. **Final reconciliation and diagnostics.** Add missed-callback/synthetic
   reconciliation, duplicate-aware ordering, retries, unresolved diagnostic
   records, and state-level tests.
4. **Core CLI integration.** Compose sink-before-progress, replace end-only
   writing, preserve projection/output behavior, and retain incremental state on
   runner exceptions; add core CLI timing and failure-path regressions.
5. **Kaocha completed-leaf hook.** Add and order the adapter-owned final
   `post-test` plugin, reduce the reporter to synthetic progress, and verify
   callback/final parity and no duplicates.
6. **Kaocha CLI integration and interruption coverage.** Prove before-next-leaf
   publication, detailed merged fixture output, synthetic behavior, and durable
   artifacts during a later blocked/interrupted run.
7. **Compatibility regression pass.** Re-run sanitizer, synthetic collision,
   duplicate execution, result projection, `-m`, `-X`, pass/fail, and pure
   progress-failure coverage; fix only task-scoped regressions.
8. **Documentation and final verification.** Update README, CHANGELOG, AGENTS,
   callback and `scry.cli/run` docstrings; regenerate API docs; run formatting,
   lint, focused/core/Kaocha/full command-line checks; record results in
   `implementation.md`.
