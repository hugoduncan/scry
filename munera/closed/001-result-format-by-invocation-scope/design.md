# Result format by invocation scope

## Goal

Change `scry`'s returned test result format so the amount of returned detail depends on how specifically the caller invoked the run, while allowing the returned keys for each case to be configured.

Assumption: the user's phrase "single vat" means a single Clojure test **var**.

## Context

`scry` currently returns one result shape from `scry.core/run` / `scry.clojure-test/run`:

- `:summary` with aggregate counts.
- `:pass?`.
- `:failures` containing failed/erroring vars.
- assertion detail only for failing/erroring assertions.
- `:out` and `:err` for failed/erroring vars.

This is useful for detailed debugging, but it is too verbose or not specific enough depending on the invocation:

- Broad suite runs should remain compact.
- Single namespace runs should expose assertion detail without output noise.
- Single var runs should expose assertion detail and captured output.

## Invocation scopes

Define a small set of result scopes from the run options after resolving the tests to execute:

1. **Suite / multi scope**
   - Running all discovered tests, or
   - running multiple namespaces, or
   - running multiple vars.

2. **Single namespace scope**
   - Caller requested exactly one namespace and did not request explicit vars.

3. **Single var scope**
   - Caller requested exactly one test var.

If discovery happens to find only one namespace, it should still be treated as suite / multi scope unless the caller explicitly requested a single namespace. The scope should reflect invocation intent, not just accidental discovery size.

Scope tie-breakers:

- Explicit `:vars` is the most specific selector and takes precedence over `:namespaces` for scope classification when both are supplied.
- Non-test vars that are filtered out before execution do not count toward single-var or multi-var classification.
- If explicit `:vars` resolves to exactly one executable test var, use single var scope, even if `:namespaces` is also supplied.
- If explicit `:vars` resolves to more than one executable test var, use suite / multi scope.
- If explicit `:vars` resolves to no executable test vars, fall back to namespace-based classification when an explicit namespace selector remains; otherwise use suite / multi scope for the empty run result.

## Required default behavior

All scopes return entries under the canonical top-level key `:results` by default.

- `:failures` remains available as a compatibility accessor/key for failing or erroring result entries during migration, but new formatting and documentation should treat `:results` as canonical.
- Broad suite defaults may keep `:failures` in the default top-level keys only if needed for backward compatibility; if both keys are present, `:results` is the complete canonical result collection for the selected format and `:failures` is the filtered failing/erroring subset.

Each result entry represents an executed test var unless a custom result format requests a narrower projection.

- Detailed scopes include one result entry for every executed test var, including fully passing vars, so passing assertion details have a stable location.
- Compact suite / multi scope may include only failing/erroring vars by default to preserve the existing compact broad-run payload; when it does, each entry still includes enough per-var assertion summary information to identify the failure/error.
- When assertion details are included, passing, failing, and erroring assertions are included in the entry's `:assertions` collection.

Result entry status semantics:

- `:status :pass` means the test var executed and all recorded assertions passed, with no failures or errors.
- `:status :fail` means the test var had one or more failing assertions and no errors.
- `:status :error` means the test var had one or more errors; errors take precedence over failures for status.
- `:status :unknown` may be used only when an integration cannot reliably classify the entry, and should be documented if exposed.

### Suite / multi scope

For testing everything or multiple namespaces:

- Do not include stdout or stderr.
- Do not include per-assertion details.
- Include a summary of assertion pass/fail/error counts.
- Include enough information to identify failing/erroring vars without returning every assertion form.

### Single namespace scope

For testing a single namespace:

- Do not include stdout or stderr.
- Do include entries for all executed vars in that namespace, including fully passing vars.
- Do include details of all assertions, including passing, failing, and erroring assertions.
- Preserve assertion context such as message, file, line, and `testing` contexts where available.
- Fully passing vars should have `:status :pass` and their passing assertions in `:assertions` when assertion details are included.

### Single var scope

For testing a single var:

- Do include stdout and stderr.
- Do include one entry for the executed var, including when it fully passes.
- Do include details of all assertions, including passing, failing, and erroring assertions.
- Preserve assertion context such as message, file, line, and `testing` contexts where available.
- A fully passing var should have `:status :pass` and its passing assertions in `:assertions` when assertion details are included.

## Configurability

Returned keys must be configurable independently for each invocation scope.

The implementation should provide a clear option for overriding the default keys or inclusions per scope. The exact API can be chosen during implementation, but it should support at least:

- Configuring top-level result keys per scope.
- Configuring per-var/result-entry keys per scope.
- Configuring whether assertion details are included per scope.
- Configuring whether stdout/stderr are included per scope.

A possible shape is:

```clojure
{:result-format
 {:suite     {:top-level-keys [:summary :pass? :results]
              :entry-keys [:var :ns :status :assertion-summary]
              :assertions? false
              :output? false}
  :namespace {:top-level-keys [:summary :pass? :results]
              :entry-keys [:var :ns :status :assertions]
              :assertions? true
              :output? false}
  :var       {:top-level-keys [:summary :pass? :results]
              :entry-keys [:var :ns :status :assertions :out :err]
              :assertions? true
              :output? true}}}
```

This is illustrative, not a required final API. Prefer an API that is simple for REPL users and easy for agents to reason about.

## Scope

In scope:

- Update capture so assertion detail can be retained for passing assertions when the selected result format needs it.
- Add result formatting based on invocation scope.
- Add or update public options for per-scope returned-key configuration.
- Update convenience accessors if their assumptions change.
- Update README, AGENTS.md, and SKILL.md for the new result behavior.
- Add tests covering default behavior and configurable behavior.

Out of scope:

- Changing how tests are discovered or executed beyond what is necessary to classify invocation scope.
- Adding a new command-line interface.
- Persisting result history beyond the existing `last-run` atom.

## Design considerations

- Keep `scry.core` free of a hard dependency on Kaocha.
- Preserve normal `clojure.test` fixture semantics.
- Avoid returning huge payloads for broad suite runs by default.
- Make the default shape predictable from invocation intent.
- Keep REPL ergonomics high: `(scry/run {:namespaces [...]})` and `(scry/run {:vars [...]})` should naturally return the useful level of detail.
- Consider whether `scry.kaocha` can reuse the same formatter, or document any limitations if Kaocha cannot provide equivalent all-assertion detail.

## Acceptance criteria

- A broad/discovered run returns aggregate assertion counts and no stdout/stderr keys in result entries by default.
- A run with multiple namespaces returns aggregate assertion counts and no stdout/stderr keys in result entries by default.
- A run with exactly one namespace returns all assertion details and no stdout/stderr keys in result entries by default.
- A run with exactly one var returns all assertion details and includes stdout/stderr in the result entry by default.
- Passing assertion details are available for single namespace and single var runs.
- Failing/erroring assertion details still include expected, actual, message, file, line, testing contexts, and stack traces for errors.
- The returned keys/inclusions can be customized independently for suite/multi, single namespace, and single var scopes.
- Tests cover each default scope and at least one custom configuration per scope.
- `scry.core/last-result`, `failures`, `failed-test`, `output`, and `report-string` either continue to work or are deliberately updated with tests and documentation.
- User-facing documentation reflects the new scoped result behavior.
