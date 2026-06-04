# Plan

## Approach

Treat `design.md` as stable: the remaining questions are implementation choices, not blocking product/design ambiguities. The implementation should preserve the public `scry.core/run` / `scry.clojure-test/run` result shapes while changing the capture internals to be dynamically replaceable and nullable.

Key decisions:

- Refactor `scry.capture` around an explicit capture context that wraps the existing state atom plus run metadata such as the intended executable vars. Keep compatibility helpers like `new-state`, `report-fn`, `routing-writer`, `build-result`, `current-var-result`, and `orphan-output` usable to minimize churn in existing tests.
- Add a dynamic context boundary, conceptually `*capture-context*`, plus lifecycle helpers such as `new-context`, `with-context`, and `without-context`. Report handling and routing writers should consult the current dynamic context at event/write time, so nested runners can replace or disable capture without mutating the outer run's state.
- Add owned/ignored frame tracking to the capture state. Core runs know their executable vars, so each context should carry an intended-var allow-list. Events for allow-listed vars open owned frames; events for non-owned nested vars open ignored frames. Ignored frames must not record assertions/counts or append output to the enclosing owned var.
- Update `scry.clojure-test/run` to resolve executable vars first, create a fresh context with that allow-list, bind the context while running, and build results from that context's state. CLI progress callbacks must still receive one canonical completed entry per owned test var.
- Inspect `clojure.test` fixture behavior before changing the execution loop. Prefer the smallest fixture-preserving mechanism that binds per-var `*out*` and `*err*` around each `:each` fixture plus `test-var` invocation, while leaving `:once` fixture output as orphan/run output. If that requires reproducing the small `test-vars` fixture loop, keep it local, documented, and covered by fixture-order tests.
- Wrap `kaocha.api/run` in `scry.kaocha/run` with `capture/without-context` so generated fixture events from the optional adapter are represented only in the adapter's converted result, not in an enclosing core `scry` run. Keep the optional Kaocha dependency boundary intact.
- Add focused tests for nested core-run isolation, raw non-owned nested `clojure.test` events, per-var output isolation, fixture output ownership, and optional Kaocha isolation. Use existing focused slices for final verification rather than broad discovery as the primary all-green signal.
- Document remaining limitations in task notes and/or `AGENTS.md`: same-thread/cooperative dynamic binding is supported; arbitrary parallel test runners, late async report events, and deliberately spoofed events for an allow-listed var remain out of scope.

## Risks

- `clojure.test` fixture semantics are easy to subtly change if the runner stops delegating to the standard fixture loop. Mitigation: inspect the current implementation, preserve `:once` grouping and `:each` ordering, and keep/extend fixture tests.
- Dynamic vars are thread-local/cooperative. Child work that does not carry dynamic bindings, late asynchronous report events, or arbitrary parallel runs may not be fully isolated. Mitigation: do not overpromise; document the boundary.
- Ignored-frame routing could accidentally drop outer output or attribute nested output to the wrong var. Mitigation: test nested owned/ignored frames and output before and after ignored execution.
- CLI progress currently depends on end-of-var capture state. Mitigation: adapt progress lookup to owned end events and run focused CLI tests after the core refactor.
- Kaocha reporter/capture behavior is optional-classpath-sensitive. Mitigation: keep all Kaocha changes under `src-kaocha/` and verify with `:test:kaocha`.

## Plan ambiguity follow-up decisions

### Capture helper compatibility API

Refactor `scry.capture` with new context-first helpers while keeping the current state-based tests and call sites usable:

- `new-state` remains a zero-arity constructor returning the raw state atom used by existing capture tests.
- `new-context` is the new runner-facing constructor. It accepts an option map such as `{:state state :intended-vars vars :metadata m}`; omitted `:state` creates `(new-state)`, and omitted `:intended-vars` means legacy/accept-all ownership for direct helper tests.
- `with-context` binds the dynamic current context to a context value; `without-context` binds the dynamic current context to nil for an explicit disabled boundary.
- `report-fn` supports both `([])` and `([state-or-context])`:
  - no-arg form is the preferred installed hook for runners and always dispatches through the dynamic current context at event time;
  - one-arg form is a compatibility adapter for direct tests/legacy call sites. It dispatches to the supplied state/context so capture helper tests can run inside an outer `scry` verification run without polluting or depending on that outer context; an explicit nil/disabled context still ignores events instead of falling back to the supplied state.
- `routing-writer` supports both `([stream-key])` and `([state-or-context stream-key])`:
  - one-arg form is the preferred runner form and consults the dynamic context at write time, falling back to the writer captured when the routing writer was created if the dynamic context is nil/disabled;
  - two-arg form is the compatibility adapter. Like one-arg `report-fn`, it appends to the supplied state/context for direct tests/legacy call sites while preserving explicit `without-context` semantics: disabled capture routes escaped output to the fallback writer rather than to an enclosing/supplied capture state.
- `build-result`, `canonical-results`, `current-var-result`, and `orphan-output` accept either a raw state atom or a context and normalize internally. Existing state-based assertions remain valid; runner code should pass the context where practical.

This distinction between "no dynamic binding" and "thread-bound nil disabled context" is important: compatibility fallbacks are allowed only when no dynamic binding exists. An explicit `without-context` boundary must ignore report events for scry construction and route escaped output to passthrough/no-op fallback rather than to any enclosing or supplied capture state.

### `:each` fixture output ownership and progress timing

Use a small local reproduction of the current Clojure `clojure.test/test-vars` fixture loop, based on the inspected implementation:

```clojure
(doseq [[ns vars] (group-by (comp :ns meta) vars)]
  (let [once-fixture-fn (test/join-fixtures (::test/once-fixtures (meta ns)))
        each-fixture-fn (test/join-fixtures (::test/each-fixtures (meta ns)))]
    (once-fixture-fn
     (fn []
       (doseq [v vars]
         (when (:test (meta v))
           (each-fixture-fn (fn [] (test/test-var v)))))))))
```

The local runner should preserve that grouping/order, but wrap only each individual var execution with a capture output-owner binding:

- `:once` fixture setup/teardown runs outside any per-var output owner, so its output remains orphan/non-public.
- For each executable var, the runner sets a per-var output owner before invoking the `:each` fixture function and restores the previous owner after the `:each` fixture returns. This means `:each` setup, test body output, and `:each` teardown all append to that var's buffers.
- The output-owner binding must not create report frames and must not increment test counts. Counts/progress are driven by the real `clojure.test/test-var` report events only.
- `:begin-test-var` for the same intended var initializes/resumes the existing entry and opens an owned frame; it does not reset buffers already populated by `:each` setup output.
- `:end-test-var` closes the owned frame and records the var as completed, but it must not clear the wrapper-established output owner before `:each` teardown runs.
- Invoke `:progress-callback` once per owned executable var after the `:each` fixture function returns, using the canonical completed var entry. This preserves live per-var progress without duplicate progress entries and includes `:each` teardown output in CLI result-file/progress data.

### Characterization/red-test workflow

Do not commit intentionally failing characterization tests. If a red-first check is useful, introduce it temporarily in the working tree or REPL, run it to confirm the current leak/ownership problem, then either implement the fix in the same working tree before committing or remove the temporary test. The committed test changes for this task should be regression/acceptance tests that pass with the implementation in the same commit. Record any pre-refactor red observations tersely in `implementation.md` rather than leaving the repository in a failing state between slices.

## Slice order

1. **Baseline and characterization** — run focused baseline checks, inspect `clojure.test` fixture/output behavior, and, if useful, run temporary red-first checks only in the REPL or working tree. Do not commit intentionally failing characterization tests; add nested core isolation and fixture/output ownership regression tests only together with the implementation that makes them pass.
2. **Capture context primitives** — introduce dynamic context lifecycle helpers, make report and routing writer dispatch consult the current context at event/write time, and preserve existing capture helper APIs.
3. **Owned/ignored frame semantics** — add intended-var allow-listing, owned frame stack behavior, ignored non-owned nested frames, nil/disabled context behavior, and current-result lookup that works for progress callbacks.
4. **Core runner integration and per-var output** — install a fresh context per `scry.clojure-test/run`, bind/reset the normal testing vars, preserve fixture semantics, and bind or route output so test body and `:each` fixture output belong to the correct var while `:once` output remains non-public orphan output.
5. **Nested core-run coverage** — prove nested `scry.clojure-test/run` results are inspectable by the outer test but inner vars/assertions/failures/output do not appear in the outer result.
6. **Kaocha adapter isolation** — disable enclosing scry capture while Kaocha executes and add optional tests for an outer `scry/run` invoking `scry.kaocha/run` over intentional failing fixture vars.
7. **CLI and formatting regression** — verify core CLI progress/result-file behavior and optional Kaocha CLI behavior remain unchanged, including progress callbacks and detailed failure EDN.
8. **Documentation and final verification** — record concurrency/non-cooperative limitations, update docs if user-facing behavior or agent workflow guidance changes, and run the focused command-line verification commands for the changed surfaces.
