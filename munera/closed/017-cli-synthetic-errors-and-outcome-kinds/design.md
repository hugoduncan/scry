# Design: CLI synthetic errors and machine-readable outcome kinds

## Goal

Make the scry CLI robust when runners return failing/erroring synthetic or suite-level result entries that do not have a concrete `:var`, and expose clearer machine-readable outcome classification for callers that need safe fallback policy decisions.

## Context

While integrating scry into psi, Scry CLI + the optional Kaocha adapter hit a rough edge: an incomplete `:test-paths` classpath caused Kaocha to produce a synthetic failure entry with `:var nil`. Scry then crashed while trying to derive the `.scry-results/` filename from the nil var:

```text
Cannot invoke "clojure.lang.Named.getNamespace()" because "x" is null
```

A caller also had to infer fallback policy from stderr text such as `scry CLI error:`. That is fragile; the CLI should expose structured classification for at least runner infrastructure failure, test load failure, normal test assertion failure, and zero tests.

## Requirements

- Result-file writing must tolerate failing/erroring canonical entries whose `:var` is nil or absent.
- CLI progress output must tolerate nil-var failing/erroring/unknown entries.
- Synthetic/suite-level result files should have deterministic, human-readable names, e.g. `suite-error-1.edn` or `suite-fail-1.edn`.
- Normal var-backed result-file names must remain unchanged.
- `run-cli` outcomes and `clojure -X` non-zero ex-data should include a machine-readable classification that distinguishes:
  - runner infrastructure failure / CLI runner error
  - test load failure / synthetic suite-level load error when representable in canonical results
  - normal test assertion failure/error
  - zero executable tests
  - argument errors on structured option-normalization surfaces
- `-m scry.cli` parser errors remain process-oriented: they return non-zero status and human stderr, but do not expose a structured outcome map or outcome kind. Machine callers that need argument-error classification should use `clojure -X scry.cli/run`, direct `scry.cli/run`, or direct option-normalization APIs rather than parsing `-m` stderr.
- The existing human-oriented stderr line can remain for compatibility, but callers should not need to parse it.
- Keep public result map shapes stable except for additive keys on CLI outcomes/ex-data.

## CLI outcome classification contract

The CLI outcome classification is an additive public CLI contract, not a change to the public `scry.core/run` result map shape.

- `run-cli` outcomes must include top-level `:scry.cli/outcome-kind` on both success and non-zero outcomes.
- `clojure -X` non-zero `ex-info` data must include top-level `:scry.cli/outcome-kind` as well as the existing `:outcome` map, whose value also includes the same key.
- The successful `clojure -X` return value is the `run-cli` outcome map and therefore also includes `:scry.cli/outcome-kind`.
- Classification keywords are stable enough for machine callers during the pre-1.0 public alpha, though new keywords may be added for newly distinguished CLI outcomes. Existing keyword meanings should not be changed without an explicit user-facing compatibility note.

Initial vocabulary:

- `:scry.cli/pass` — at least one concrete executable test-var entry ran, no higher-precedence non-zero signal applies, all aggregate assertions passed, and all concrete canonical result entries passed. Synthetic/non-var-backed entries never make a run executable.
- `:scry.cli/argument-error` — CLI option parsing or option normalization failed before runner execution.
- `:scry.cli/runner-error` — runner infrastructure failed before yielding a valid result with `:canonical-results`, including inability to load the optional Kaocha runner, runner exceptions, or malformed runner results.
- `:scry.cli/load-error` — the runner returned one or more failing/erroring canonical entries that are not attributable to a concrete test var; this covers synthetic suite-level/test-load errors such as Kaocha load failures represented as entries with nil or absent `:var`.
- `:scry.cli/test-failure` — one or more concrete var-backed canonical entries failed or errored, or aggregate runner assertion counts report failures/errors attributable to the executed run.
- `:scry.cli/unknown-result` — one or more canonical entries have `:status :unknown`, with no higher-precedence classification.
- `:scry.cli/zero-tests` — runner execution completed without runner infrastructure error, but no concrete executable canonical test-var entries were produced and no higher-precedence non-zero signal applies. Aggregate pass counts or synthetic/non-var-backed entries do not by themselves make a run executable.

## Classification precedence and recognition

Classification is a run-level aggregation. When multiple non-zero signals are present, choose the first matching kind in this order:

1. `:scry.cli/argument-error` for structured parse/normalization errors before runner execution, including `clojure -X` normalization errors converted into the non-zero ex-info contract. Main-style `-m` parser errors are intentionally outside the structured outcome contract because `main-outcome` returns only an exit code and `-main` exits the process after writing human stderr.
2. `:scry.cli/runner-error` for exceptions while resolving or invoking the selected runner, failure to create/clear `.scry-results/`, missing or non-vector `:canonical-results`, or other CLI infrastructure exceptions before a valid canonical entry collection can be inspected.
3. `:scry.cli/load-error` for any failing/erroring canonical entry whose `:var` is nil, absent, or not a concrete var symbol with both namespace and name. This is recognized structurally from canonical result data; do not parse stderr or assertion message text. If such an entry also has `:ns`, it is still synthetic/non-var-backed for classification purposes.
4. `:scry.cli/test-failure` for concrete var-backed `:fail` or `:error` entries, or for aggregate `:summary` assertion `:fail`/`:error` counts when no higher-precedence kind applies.
5. `:scry.cli/unknown-result` for canonical entries with `:status :unknown` when no higher-precedence kind applies.
6. `:scry.cli/zero-tests` when the run has no concrete executable canonical test-var entries and no higher-precedence kind applies.
7. `:scry.cli/pass` when the run has at least one concrete executable canonical test-var entry and no non-zero classification applies.

Runner infrastructure errors are distinguished from representable load errors by whether the selected runner produced an inspectable result with vector `:canonical-results`. Once a valid canonical entry collection exists, nil/absent-var failing or erroring entries are treated as `:scry.cli/load-error` rather than `:scry.cli/runner-error`.

A concrete var-backed entry is one whose `:var` is a symbol with a namespace and name. Only concrete var-backed canonical entries count as executable test vars for the `:scry.cli/pass` versus `:scry.cli/zero-tests` decision. Synthetic/non-var-backed entries never count as executable, even if their status is `:pass`; if the run has only synthetic passing entries and no higher-precedence signal, classify it as `:scry.cli/zero-tests`. Aggregate assertion `:pass` counts alone also do not make a run executable, though aggregate assertion `:fail`/`:error` counts still classify as `:scry.cli/test-failure` at the precedence step above. Ordinary assertion failures/errors keep their normal `:scry.cli/test-failure` classification even if their assertion message mentions loading or requiring code.

## Synthetic entry progress and result-file naming

Var-backed progress and result-file names remain unchanged:

- passing vars print `.` to stdout;
- failing/erroring/unknown vars print the unqualified var name to stderr;
- failing/erroring var-backed result files keep the current namespace-prefixed `<encoded-ns>__<encoded-var>.edn` shape.

For non-var-backed canonical entries, the CLI assigns a synthetic display/file token in canonical result order:

- `suite-error-N` for `:status :error`;
- `suite-fail-N` for `:status :fail`;
- `suite-unknown-N` for `:status :unknown`.

`N` is a per-status 1-based counter within the run, so the first synthetic error is `suite-error-1` and the first synthetic fail is `suite-fail-1` regardless of their relative order. The synthetic token is used as the stderr progress text. If the entry includes `:ns`, progress may prefix the namespace as `<ns>/<token>` for human context, but callers must inspect structured outcome/result data rather than parse progress text.

Synthetic failing/erroring result files use the same token with `.edn`:

- no `:ns`: `suite-error-1.edn`, `suite-fail-1.edn`;
- with `:ns`: `<encoded-ns>__suite-error-1.edn`, `<encoded-ns>__suite-fail-1.edn`.

Filename assignment must avoid collisions with unchanged var-backed filenames and with earlier synthetic filenames in the same run. The implementation should compute assigned filenames from the whole canonical entry collection using a used-filename set. If a synthetic candidate is already used, append a deterministic collision suffix such as `--2`, `--3`, etc. before `.edn` until the filename is unique. Collision suffixes are only for file paths; progress text can keep the unsuffixed synthetic display token.

## Documentation scope

Because `:scry.cli/outcome-kind` is a machine-readable CLI contract, implementation should update public/agent-facing docs when the feature is introduced:

- `README.md` command-line usage should document the key, the initial keyword vocabulary, and that machine callers should inspect `:scry.cli/outcome-kind` / `.scry-results/*.edn` instead of parsing stderr.
- Top-level `SKILL.md` command-line workflow should tell agents to inspect `:scry.cli/outcome-kind` for `-X` outcomes/non-zero ex-data and `.scry-results/*.edn` for failure detail.
- `AGENTS.md` final-verification/CLI guidance should mention the classification key when discussing structured CLI outcomes for agents.
- `CHANGELOG.md` Unreleased should mention the user-visible CLI classification and synthetic suite-level result-file handling.

## Acceptance

- A failing/erroring canonical entry with nil `:var` writes a readable EDN result file instead of crashing.
- Nil-var progress output prints a useful synthetic name rather than throwing.
- Focused tests cover nil-var result-file naming/writing and CLI end-to-end behavior through `run-cli`.
- Focused tests cover outcome classification for normal failures, zero tests, runner errors, argument errors, and synthetic nil-var load/suite errors where feasible.
- README/SKILL/AGENTS docs are updated only if user-facing CLI classification semantics are introduced.
