# Replaceable capture context for nested test runs

## Goal

Make `scry` capture robust when a test invokes another in-process test runner. Each `scry` runner invocation should install its own dynamically scoped capture context so nested test runs replace or disable the enclosing capture for their duration, then restore the outer context on exit.

This should prevent intentional inner fixture failures, such as generated Kaocha project tests, from being recorded as failures of the outer test var. It should also make per-test output capture more locally isolated by rebinding `*out*` and `*err*` around each test var where practical.

## Context

`scry` is an in-process runner. Its core runner currently binds `clojure.test/report` and process-local writers for the duration of a run. The bound report function and routing writers close over one capture state atom.

That works for a flat run, but nested in-process test execution can leak through the outer capture. For example:

```text
outer scry/run
  runs scry.kaocha-test/tests-edn-loading-and-suite-selection-test
    the test invokes scry.kaocha/run
      Kaocha runs a generated fixture var that intentionally fails
        clojure.test/report emits :fail
        outer scry capture can record the inner fixture failure
```

The repository currently avoids this for the optional Kaocha verification slice by using `clojure.test/run-tests` instead of outer `scry/run`. That workflow boundary is useful, but it leaves the core capture design fragile for nested runner use.

The capture machinery also currently routes output through run-level `*out*` and `*err*` bindings backed by a state atom with a single current var. Per-var buffers exist, but output routing depends on report events setting the current var correctly. Nested or overlapping test execution can attribute output to the wrong capture state/var.

## Problem

A test runner that is itself called from a test should be able to own its capture. The enclosing `scry` run should see the outer test var and the assertions made by that outer test, not the raw internal events of the nested runner's fixture vars.

The current closed-over-state design makes that hard because the outer report function/writers remain active during nested runner execution unless the nested runner completely rebinds `clojure.test/report`, `*out*`, and `*err*` in a compatible way.

The desired design is replaceable/nullable capture state:

- A dynamically scoped capture context represents the currently active `scry` capture.
- A `scry` runner invocation installs a fresh context for its duration.
- A nested `scry` runner invocation replaces the active context with its own context and restores the outer context when it exits.
- A runner that collects results some other way, such as the Kaocha adapter, can disable the enclosing `scry` capture while the foreign runner executes, then convert the foreign runner result into `scry` data.
- Output routing follows the same current capture context, so nested fixture stdout/stderr does not pollute the outer var.

## Scope

In scope:

- Refactor `scry.capture` so report handling and output routing dispatch through a dynamically replaceable capture context rather than a report function/writer closing over one immutable state reference.
- Preserve existing public result shapes and default behavior for ordinary `scry.core/run`, `scry.clojure-test/run`, CLI core runs, and result-format options.
- Ensure every core `scry.clojure-test/run` invocation installs a fresh capture context for its own run.
- Add or adjust tests proving nested `scry.clojure-test/run` calls do not leak inner events or output into the outer capture.
- Make `scry.kaocha/run` explicitly isolate itself from an enclosing `scry` capture while Kaocha executes, without adding a hard Kaocha dependency to core namespaces.
- Add or adjust optional Kaocha tests proving intentional generated fixture failures are represented only in the Kaocha adapter result and are not recorded as outer `scry` failures when an outer test invokes the adapter.
- Rebind `*out*` and `*err*` around each executed core test var where practical, so output capture has a per-test dynamic binding instead of relying only on a run-level routing writer plus mutable current-var state.
- Keep CLI progress callbacks live and preserve their existing per-var semantics.
- Document any remaining limitations around non-cooperative nested raw `clojure.test` usage or concurrent overlapping test runs in one REPL.

Out of scope:

- Changing the public `scry.core/run` API or result map schema.
- Rewriting the Kaocha adapter's result model or separating Kaocha's merged stdout/stderr stream.
- Making broad discovery across every classpath/alias combination the canonical all-green check.
- Solving arbitrary concurrent `scry/run` calls launched simultaneously from separate threads or overlapping nREPL evals, except to avoid obvious shared-state leaks introduced by this refactor.
- Introducing a mocking library or replacing real fixture tests with mocks.

## Design direction

### Replaceable capture context

Introduce a dynamic capture context in `scry.capture`, conceptually:

```clojure
(def ^:dynamic *capture-context* nil)
```

The context may wrap the existing state atom plus any run metadata needed by routing/reporting. Public-ish helper functions should make the intended lifecycle clear, for example:

```clojure
(capture/new-context opts)
(capture/report-fn)
(capture/routing-writer :out)
(capture/with-context context body)
(capture/without-context body)
```

Exact names are implementation details, but the behavior should be explicit: report and writer code consult the current dynamic context at event/write time.

### Core runner owns a fresh context

`scry.clojure-test/run` should create a fresh context after resolving executable vars and bind it during execution. Nested `scry.clojure-test/run` calls naturally install a different context and restore the outer one when they return.

Existing result construction can continue to build from the context's state atom.

### Kaocha adapter disables or replaces enclosing capture

`scry.kaocha/run` already obtains Kaocha's result tree and converts it to scry results. It does not need outer `scry.capture` to record raw fixture events. During `kaocha.api/run`, it should run with the enclosing scry capture disabled or replaced as appropriate, while preserving Kaocha's own result collection and progress reporter behavior.

Acceptance should be based on observed behavior: an outer `scry/run` over a test var that invokes `scry.kaocha/run` should record only the outer var's assertions/output, while the inner Kaocha adapter result still includes intentional fixture failures.

### Per-test output bindings

The core runner should evaluate each test var with `*out*` and `*err*` rebound to writers for that test's capture context/var where practical.

The intent is stronger isolation than a single run-level writer that consults a mutable current var. A test's stdout/stderr should be captured in that test's entry even when the surrounding run contains other vars or when a nested runner executes.

Possible implementation approaches:

1. Wrap each var invocation with bindings before delegating to `clojure.test/test-vars`, if the needed hook can be introduced without breaking `:once` and `:each` fixture semantics.
2. Keep `clojure.test/test-vars` for fixture correctness but make report-time `:begin-test-var`/`:end-test-var` establish a stack-aware output target that the routing writer uses.
3. Use a small wrapper around the var test function only if it preserves normal `clojure.test` fixture behavior and metadata semantics.

The plan should inspect fixture semantics before choosing. Preserving `:once` and `:each` behavior is mandatory.

### Stack/allow-list as defense-in-depth

Replaceable context solves cooperative nested runners. A raw nested call to `clojure.test/test-vars` that does not install a new `scry` context could otherwise emit events into the current outer context.

This task should include an intended-var allow-list in each core capture context. `scry.clojure-test/run` knows the executable vars before execution, so the context should record that set and treat only those vars as owned by the run. Report handling should be stack-aware:

- `:begin-test-var` for an intended var opens an owned frame, starts or resumes that var entry, increments the run test count, and makes the var the current output owner.
- `:end-test-var` for an owned frame closes that frame and restores the previous owned output owner, if any.
- `:begin-test-var` for a non-allow-listed var opens an ignored frame. Assertion events and `:end-test-var` events inside that ignored frame are not recorded in the enclosing run.
- stdout/stderr writes while an ignored frame is active must not be appended to the enclosing test var. They may be appended to non-public orphan/ignored buffers for debugging, but they are omitted from public `:results`/`:failures`.
- Assertion events seen with no owned current var are ignored for public result construction, aside from normal aggregate counters for owned vars.

With this rule, raw nested `clojure.test` calls for vars outside the selected run are treated as non-owned implementation detail rather than as outer failures. Remaining non-cooperative limitations should still be documented: events that do not bracket themselves with normal `:begin-test-var`/`:end-test-var`, events for the same allow-listed var deliberately emitted by user code, or asynchronous events emitted after the capture context has exited cannot be reliably attributed.

### Disabled context semantics

A nil capture context means "no active `scry` capture owns this execution." It should not create orphan public entries and should not fall back to the enclosing run's state.

When a `scry` report function is reached while the dynamic capture context is nil, it should ignore `clojure.test/report` events for scry result construction. This is the intended behavior for `capture/without-context` around foreign runners such as Kaocha: the foreign runner may bind its own reporters and collect its own result tree, while any event that still reaches the enclosing scry hook is not recorded by the outer run.

Routing writers should also consult the dynamic context at write time. If a writer is reached with nil context, it should write to the fallback writer captured when the routing writer was created, or otherwise no-op if no fallback is available. It must not append to the previous/enclosing capture state's var or orphan buffers. Foreign runners that bind their own `*out*`/`*err*` should continue to capture their output independently; escaped output should be passthrough rather than outer-result data.

### Fixture output ownership

Per-var output binding must preserve normal `clojure.test` fixture semantics. The intended ownership rules are:

- Output produced by the test body belongs to that test var.
- Output produced by that var's `:each` fixture setup or teardown also belongs to that test var, because an `:each` fixture is semantically part of one var execution.
- Output produced by `:once` fixture setup or teardown belongs to the namespace/run rather than any individual var. Since the public result schema has no namespace/run output field, this output should remain in non-public orphan output and be omitted from formatted result entries.

If the implementation keeps `clojure.test/test-vars`, equivalent stack-aware routing is acceptable only if it satisfies the ownership rules above. If the implementation reproduces the small `test-vars` fixture loop to install per-var bindings around each `:each` fixture invocation, it must preserve the normal `:once` grouping, `:each` execution order, `clojure.test/test-var` behavior, metadata handling, and exception reporting.

### Thread and concurrency boundary

The capture context is dynamically scoped and is guaranteed for same-thread execution and cooperative nested runners that install or disable their own context. Clojure mechanisms that convey dynamic bindings to child work, such as `future`/`bound-fn`, may carry the active context and routing writers, but this task should not promise full support for arbitrary Java threads, parallel test runners, or asynchronous report events emitted after the owning run has exited.

Concurrent `scry/run` calls from independent REPL evaluations should not share capture state when each call binds its own context, but arbitrary overlapping runs remain out of scope if they share global process facilities or emit late events. Documentation/task notes should state this boundary rather than implying thread-global capture isolation.

## Acceptance criteria

- Ordinary focused core runs still pass and return the same public result shapes for suite, namespace, and var scopes.
- CLI core progress/results behavior remains unchanged, including live progress callbacks and detailed `.scry-results/` data.
- A nested `scry.clojure-test/run` invoked inside an outer test does not add the inner vars, assertions, failures, errors, or output to the outer run's captured results.
- The outer test can still assert on the nested result map, and those outer assertions are captured normally.
- Output from inner nested `scry` runs does not appear in the outer test var's captured `:out`/`:err` unless the outer test explicitly prints the nested result/output itself.
- Core test output remains isolated per test var in multi-var runs.
- `:once` and `:each` fixtures continue to behave like normal `clojure.test` fixtures.
- In a Kaocha-enabled REPL, an outer `scry/run` over a focused test var that invokes `scry.kaocha/run` does not record generated fixture vars as outer failures; the adapter result still reports the generated fixture failure in its own returned result.
- Optional Kaocha CLI progress/result-file behavior remains live and unchanged.
- AGENTS.md or task notes document any remaining limitation around raw non-cooperative nested `clojure.test` or concurrent overlapping evals.

## Planning questions

- What is the smallest refactor that makes `scry.capture` report/output routing consult a dynamic replaceable context while preserving existing helper APIs and tests?
- Can per-var `*out*`/`*err*` rebinding be implemented without interfering with `clojure.test/test-vars` fixture semantics? If not, what stack-aware routing approach provides equivalent isolation?
- How should the required intended-var allow-list and ignored-frame stack be implemented with minimal changes to existing capture result construction?
- What focused tests best demonstrate nested `scry` isolation without depending on optional Kaocha?
- What optional Kaocha test should prove adapter isolation without making the whole Kaocha slice require outer `scry/run` as its canonical verification?
