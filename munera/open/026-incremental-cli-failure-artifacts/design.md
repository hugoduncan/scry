# 026 — Persist CLI failure artifacts as each test var completes

## Intent

Make CLI failure diagnostics durable during a running test suite: once a concrete test var has completed with a failing or erroring status, its detailed `.scry-results/*.edn` artifact should be fully written before the runner starts the next test var.

This reduces the chance that useful failures from an early test are lost when a later test hangs, the runner aborts, or the process is terminated.

## Current problem

The CLI creates and clears `.scry-results/` before running tests, and both supported runners can report live per-var progress. However, detailed result files are currently written only after the selected runner returns its complete canonical result vector.

Consequences:

- a failure is visible in terminal progress while its detailed EDN does not yet exist;
- a later hang can leave `.scry-results/` empty indefinitely;
- an abrupt termination after completed failures can lose all structured failure detail;
- the existing robust EDN sanitizer and diagnostic fallback do not help until the final bulk-write phase is reached.

## Definitions and resolved behavioral decisions

### “Individual test”

For this task, an individual test is one concrete namespace-qualified `deftest`/test var, matching the CLI’s existing progress and test-count unit. It is not one assertion, namespace, suite, fixture, or process.

### “Completed”

A concrete test var is completed when its body and attributable `:each` fixture setup/teardown have finished and the runner knows its final per-var status. The emitted entry must not later change from pass to fail/error because of additional assertions attributed to that var.

`:once` fixture work is run-level/namespace-level and is not delayed or artificially attributed to an individual var. A runner failure that prevents a var from reaching its normal completion boundary cannot promise a per-var artifact unless that runner can still emit a completed canonical entry for it.

### What is persisted

Only concrete var-backed entries with final status `:fail` or `:error` receive incremental EDN files. This preserves the existing `.scry-results/` contract:

- passing vars still print progress but do not create files;
- unknown and synthetic/non-var-backed entries retain their existing final reconciliation behavior;
- no assertion-level or all-passing-results log is introduced.

The artifact is the same detailed canonical entry shape used by current CLI result files: var, namespace, final status, assertion summary, assertions, and captured output. It is independent of public `:result-format` projection.

### Timing guarantee

Writing is synchronous at the completed-var callback boundary. For a failing/erroring concrete var, the runner does not advance to the next var until the CLI has either:

1. atomically published the final `.edn` file; or
2. caught and recorded a diagnostic write failure.

“Immediately” means before the next test var starts, not during the failing assertion and not before `:each` teardown. The implementation does not promise persistence of a currently running/incomplete var.

### Durability boundary

Each artifact is serialized through the existing bounded EDN sanitizer, written to a temporary file in `.scry-results/`, closed, and atomically moved to its final `.edn` path. Consumers must treat only final `.edn` names as complete artifacts and ignore temporary files.

This protects readers from partially serialized final files and survives ordinary later hangs or termination after publication. It does not guarantee persistence if the JVM is killed during the individual file write, if the filesystem cannot perform the publication, or across power loss. Explicit file/directory `fsync` and crash-consistent storage guarantees are out of scope.

Temporary files left by uncatchable termination are removed by the next run’s existing clear/recreate lifecycle.

### Memory behavior

This is a durability/availability change, not a streaming-memory redesign. Both runners may continue retaining assertion events and canonical results in memory so they can return the existing complete result map. Reducing peak memory or discarding persisted entries from runner state is out of scope.

## Unified completed-entry contract

The CLI should consume one runner-neutral completed-entry stream for progress and incremental persistence.

The existing `:progress-callback` remains the runner boundary rather than adding a second competing callback. Its concrete-var contract is strengthened and made consistent:

- it fires once per concrete-var execution/completion event after that execution reaches its completion boundary; the same var identity may therefore produce more than one callback if a runner executes it more than once;
- it receives the full, unprojected canonical entry, including assertions and available captured output;
- callback order is test execution order;
- it remains synchronous;
- core and Kaocha provide equivalent canonical entry shapes, subject to the existing Kaocha merged-output rule (`:out` contains combined output and `:err` is empty).

Synthetic suite/load progress may still use a non-concrete entry because it has no test var. Such entries are not covered by the per-var completeness guarantee and are persisted during final reconciliation.

The callback is observational: runner return values, scoped formatting, and result classification remain authoritative. “CLI-side callback failure containment” applies specifically to sink serialization, temporary-file, move, and cleanup failures; those failures must not abort or reclassify the test run. Terminal progress writer or flush failures retain the existing behavior: they are not converted into artifact diagnostic metadata and may propagate through the runner into the CLI’s ordinary runner-error path.

## Core `clojure.test` behavior

The core runner already emits a full canonical entry after a var and its `:each` fixture invocation finish. This timing and payload become an explicit regression-protected contract.

Incremental persistence must not alter:

- fixture semantics;
- nested-run capture isolation;
- assertion/output ownership;
- public `scry.core/run` result shapes;
- behavior when no progress callback is supplied.

## Kaocha behavior

Kaocha 1.91.1392’s `:end-test-var` reporter event occurs after the body and `:each` teardown, but before finalized result counts/history/output have all been attached to the leaf testable. Reconstructing detailed entries in the reporter would duplicate Kaocha state and can miss hierarchy-derived assertion types.

Therefore, completed concrete entries are emitted from an adapter-owned leaf `:kaocha.hooks/post-test` hook:

- the hook runs after the leaf has final result counts and assertion history;
- the adapter ensures it runs after the capture-output plugin so finalized merged output is available;
- it defensively accepts either finalized output or the still-readable capture buffer;
- it uses the same leaf-to-canonical transformation as the final result conversion;
- the adapter-owned hook is explicitly placed last in the active plugin chain, after capture-output and all user/configured plugin `post-test` hooks, so its completion snapshot includes any preceding hook changes to counts, history, or output;
- it invokes the callback before Kaocha advances to the next leaf and returns the leaf unchanged.

“Final per-var” for Kaocha means the leaf state delivered after every active `post-test` hook except the adapter’s observational completion hook itself. The adapter hook must not mutate the leaf. If a future Kaocha execution path performs leaf mutation after all plugin `post-test` hooks, that incompatibility must be detected by parity tests rather than silently weakening the guarantee.

The existing reporter path remains responsible for synthetic suite/load-error progress because such errors may have no leaf `post-test` event. It must not duplicate concrete-var callbacks.

This task preserves existing Kaocha assertion classification/detail semantics; independently broadening canonical conversion for third-party hierarchy-derived report events is adjacent work, not required here.

## CLI-owned incremental result sink

The filesystem lifecycle remains owned by `scry.cli` / `scry.cli.results`, not by either runner.

A per-run sink is created only after `.scry-results/` has been successfully prepared and before runner execution. It owns:

- synchronous handling of completed canonical entries;
- deterministic concrete-var filenames, unchanged from today;
- bounded EDN sanitation and atomic publication;
- successful-path tracking without duplicates;
- contained write-error tracking;
- final reconciliation against the runner’s canonical entries.

The CLI composes sink handling with terminal progress. It attempts durable publication before printing the failure/error progress label; regardless of publication success, progress is still emitted and testing continues.

No opt-in/opt-out flag is added. Incremental failure publication replaces end-only publication as the default CLI behavior for both `-m` and `-X`.

## Identity, duplicate callbacks, and snapshot semantics

A concrete artifact remains keyed by its existing deterministic namespace-qualified var filename. Current runners treat concrete var identity as unique in a normal run.

If the same concrete var is nevertheless completed more than once, each failing/erroring completion callback atomically replaces the same path and the path appears only once in `:result-files`; the latest successfully published failing/erroring completion snapshot wins. A later `:pass` or `:unknown` completion does not retract, remove, or invalidate an earlier failure artifact: the file records that a completed execution of that var failed during this run, while the runner’s canonical result vector remains authoritative for the status of every execution. Non-failing callbacks perform no filesystem operation and leave successful-path state unchanged.

A successfully published completion-time failure snapshot is authoritative and is not overwritten merely because the final bulk result is available. This avoids changing a durable artifact later and ensures mutable assertion values are represented as observed at completion time. Final reconciliation may publish a missing artifact, but does not rewrite an already successful concrete artifact.

## Final reconciliation

After a runner returns normally, the CLI reconciles the final canonical result vector with sink state:

- any failing/erroring concrete identity with no successfully published callback snapshot is written from its latest failing/erroring canonical entry;
- any concrete identity whose latest required failing/erroring callback snapshot was not successfully published is retried from the matching latest failing/erroring canonical entry when present, even if an older readable snapshot remains at the path; if no matching final entry exists, the callback snapshot retained by the sink is retried directly;
- synthetic/non-concrete failing/erroring entries are assigned their existing deterministic whole-result filenames and written;
- already published concrete files are retained without rewriting when they represent the identity’s latest required successfully published failing/erroring callback snapshot, including when the canonical vector contains repeated executions; a later pass/unknown does not create a newer required artifact, while an older failure snapshot left behind by a failed later failing/erroring callback is not treated as reconciled and may be replaced by the retry;
- `:result-files` is returned by first occurrence of each artifact path in canonical result order, followed by any successfully published callback-only paths in completion order, and never contains the same path more than once; it contains only successfully published final `.edn` paths.

This fallback preserves compatibility with injected/third-party runners that return valid canonical results without honoring the callback and preserves existing synthetic filename collision handling.

If the runner aborts with a catchable runner exception after earlier completed failures, the CLI retains those published files. The runner-error outcome remains a runner error with `:result nil` and `:summary nil`, but its `:result-files` reports successfully published paths in completion order. The stderr runner-error diagnostic also points to `.scry-results/` when files survived. No final canonical reconciliation is possible in this case.

If an incremental publication is still unresolved when that runner exception occurs, the outcome additionally carries `:scry.cli/diagnostic-error`. Its phase is `:incremental-result-file-writing`, and its failed-entry count covers unique callback-known concrete identities whose latest required failure snapshot was not published; there is no retry without a final canonical result. The diagnostic details come from each path’s latest incremental exception. Stderr emits the existing bounded failure-diagnostics message and first-var/root-cause lines for the unresolved publication, then the normal `scry CLI error` line and results-directory pointer when at least one artifact survived. The stdout error-summary behavior is unchanged.

## Diagnostic write failures

Incremental file I/O and serialization are non-authoritative diagnostics, consistent with task 025’s contract:

- a write failure never stops later tests;
- it never changes a test-derived outcome into `:scry.cli/runner-error`;
- progress and final summary behavior continue;
- final reconciliation retries missing artifacts when a canonical result is available;
- successfully published files remain listed even if another entry fails.

If all missing artifacts are successfully published during reconciliation, no final `:scry.cli/diagnostic-error` is included. If one or more expected artifacts remain unavailable, the outcome includes the existing bounded top-level diagnostic metadata under the following pinned contract:

- `:phase` is `:final-result-file-reconciliation` after any normal runner return, whether the unresolved path first failed incrementally or only during reconciliation; it is `:incremental-result-file-writing` only when a runner exception prevents reconciliation;
- `:failed-entry-count` is the number of unique artifact identities whose latest required failing/erroring snapshot remains unpublished, not the number of write attempts or canonical entries; a later pass/unknown creates no required artifact and therefore does not itself leave the identity unresolved, while a readable older snapshot at the same path remains useful and listed in `:result-files` but does not erase the diagnostic for a failed newer failing/erroring snapshot;
- diagnostic ordering is deterministic: unresolved concrete paths follow first occurrence in canonical result order, synthetic paths follow their assignment order, and a no-reconciliation runner-error uses completion order; `:first-failing-var` and `:first-root-cause` describe the first unresolved item in that order when derivable;
- after a failed reconciliation retry, `:message`, `:type`, `:root-type`, and `:root-message` describe that latest reconciliation exception; without reconciliation they describe the latest incremental exception for the selected path.

The primary `:scry.cli/outcome-kind` remains unchanged.

The sink catches its own serialization, temporary-write, and atomic-move failures before returning control to either runner. It best-effort deletes the attempt’s temporary file immediately after any catchable failure, without replacing the original diagnostic if cleanup also fails. After normal reconciliation it also best-effort removes any sink-owned temporary files left by failed attempts. Unknown temporary files and files left by uncatchable termination are ignored and remain subject to the next run’s clear/recreate lifecycle.

Containment does not extend to terminal progress writer or flush failures. The composed callback invokes the sink first, then terminal progress. Sink failures are converted to diagnostic state and testing continues; a progress writer/flush exception escapes the callback and is handled as a runner error if the runner propagates it. No `:scry.cli/diagnostic-error` is attached for a pure progress-output failure, and no guarantee is made for a runner that swallows a client callback exception. This keeps filesystem diagnostics non-authoritative without silently redefining failures of the CLI’s primary output channel.

Result-directory preparation failures remain authoritative pre-run `:scry.cli/runner-error` outcomes and still prevent runner invocation.

## Ordering and compatibility invariants

- `.scry-results/` is still cleared and recreated exactly once before runner execution.
- Existing concrete filenames and final EDN entry shapes remain stable.
- Synthetic filename assignment and collision behavior remain stable.
- Existing progress text, stream selection, summary text, outcome-kind precedence, and process exit rules remain stable.
- Normal summary output still occurs once after the runner returns.
- `scry.core` and `scry.cli` retain no load-time dependency on Kaocha.
- Kaocha-only code and plugin integration remain under `src-kaocha/` and the optional adapter artifact.
- Existing nested in-process capture limitations and unsupported arbitrary parallel/late-async attribution remain unchanged.
- Result publication may add per-failure filesystem latency, but passing vars perform no artifact write.

## Scope

In scope:

- incremental, atomic publication for completed concrete failures/errors in core CLI mode;
- equivalent detailed completed entries and publication timing in Kaocha CLI mode;
- a CLI-owned per-run result sink and final reconciliation;
- preserving successfully written artifacts across later catchable runner failures;
- state-based and end-to-end regression coverage for timing, detail, fallback, and atomic-file behavior;
- user/maintainer documentation and changelog updates for the new timing guarantee;
- the public `scry.cli/run` outcome documentation and generated `doc/API.md` must describe preservation of already published `:result-files` on runner errors and the pinned diagnostic phases; callback docstrings/generated adapter API documentation must also be updated when their completed-entry contract changes.

Out of scope:

- writing files for passing vars;
- assertion-level streaming;
- reducing in-memory capture/result retention;
- attributing `:once` fixture or suite-level failures to a concrete var;
- guaranteeing an artifact for a var that never reaches a completed-entry boundary;
- changing canonical status precedence or third-party Kaocha event conversion semantics;
- arbitrary parallel runner support or late asynchronous event attribution;
- resumable runs, append-only event journals, database storage, or remote result sinks;
- `fsync`, power-loss guarantees, or making guarantees about termination during the artifact’s own write window;
- changing CLI selection, parsing, exit codes, or public result map shapes beyond the clarified callback payload and runner-error preservation of already written `:result-files`.

## Required verification scenarios

Tests must establish behavior rather than only internal call order.

### Core

- A first var fails and a following var observes that the first var’s complete readable EDN file already exists before the second var body starts.
- The file includes assertion detail and `:each` setup/body/teardown output available under existing core semantics.
- A later blocking or throwing runner condition does not remove the earlier file.
- A passing first var creates no result file.

### Kaocha

- A first leaf fails and a following leaf observes its readable detailed EDN before the following leaf starts.
- The immediate entry/file includes finalized counts, assertions, and merged capture output including `:each` teardown.
- Each concrete leaf triggers one callback/progress item, with no duplicate from the reporter path.
- Synthetic load/suite errors still receive progress and final result files without being mistaken for concrete completed vars.

### Sink and CLI outcomes

- Final filenames are never visible with partial/unreadable EDN; temporary files are not returned as result files.
- A runner that ignores the callback still gets failure files through final reconciliation.
- A transient incremental write failure is retried after normal runner completion.
- A persistent write failure preserves the primary outcome, successful sibling files, and bounded diagnostic-error metadata.
- A catchable runner exception after an earlier completed failure returns a runner-error outcome that lists and points to the preserved file.
- Existing normal pass/fail, `-m`, `-X`, result projection, synthetic naming/collision, and robust sanitizer tests continue to pass.

A controlled child-process interruption test should be used where practical to prove that a published file remains readable while a later test is blocked. It must use bounded waits and guaranteed cleanup so the test suite cannot hang. If platform-independent child-process interruption is not practical, deterministic synchronization that inspects the file while the runner is blocked is the minimum acceptable test.

## Acceptance criteria

- In core and Kaocha CLI modes, every completed concrete failing/erroring var has a complete readable final `.edn` artifact before the next concrete var starts, unless publication itself failed and was contained as a diagnostic failure.
- Incremental files carry the same detailed canonical entry data as current end-of-run files, including available captured output.
- Only final `.edn` paths are exposed; files are atomically published and temporary files are ignored by consumers/outcomes.
- End-of-run reconciliation writes missed, failed, and synthetic artifacts without rewriting successful completion snapshots.
- Earlier successful artifacts survive later hangs, abrupt external termination after publication, and catchable runner exceptions.
- Diagnostic file failures do not abort tests or replace the primary test-derived outcome; unresolved failures are represented through bounded diagnostic metadata.
- Existing CLI output, filename, outcome-kind, exit-code, scoped-result, core/Kaocha dependency, and REPL/API contracts remain stable except where explicitly clarified above.
- Focused core and optional Kaocha tests, CLI command-line checks, formatting, lint, API-doc checks when applicable, and the appropriate full test slices pass.
- README, CHANGELOG, AGENTS guidance, the `scry.cli/run` and changed callback docstrings, regenerated API docs, and task implementation notes accurately describe the completed behavior, runner-error artifact preservation, diagnostic phases, and durability limits.
