# Design follow-up steps

- [x] Decide and document the exact CLI outcome classification contract: key name(s) in the `run-cli` outcome and `clojure -X` non-zero ex-data, keyword vocabulary, whether successful outcomes carry a kind, and stability expectations for machine callers.
- [x] Specify classification precedence/aggregation for mixed non-zero signals, including argument errors, runner exceptions, nil/absent-var synthetic entries, var-backed failures/errors, `:unknown` canonical entries, and zero executable tests.
- [x] Define how implementation should recognize a "test load failure / synthetic suite-level load error" from canonical results versus runner infrastructure errors or ordinary assertion failures.
- [x] Pin nil/absent-var synthetic naming for progress and result files: display text, file basename, indexing order, per-status versus global counters, behavior when `:ns` is present but `:var` is nil, and collision avoidance with var-backed filenames.
- [x] Decide which public docs must describe the new CLI classification contract and what machine callers should inspect instead of parsing stderr.
- [x] Resolve the `-m` argument-error classification contract: either expose a structured main-style outcome/outcome-kind for parser errors, or state that main-style parser errors only return non-zero/human stderr while `run-cli`/`clojure -X` provide machine-readable classification.
- [x] Align the `:scry.cli/pass` / `:scry.cli/zero-tests` / exit-code wording so only concrete var-backed canonical entries count as executable test vars, and synthetic/non-var-backed entries cannot make an otherwise zero-executable run classify as pass.
