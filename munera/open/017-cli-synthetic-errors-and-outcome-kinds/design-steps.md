# Design follow-up steps

- [x] Decide and document the exact CLI outcome classification contract: key name(s) in the `run-cli` outcome and `clojure -X` non-zero ex-data, keyword vocabulary, whether successful outcomes carry a kind, and stability expectations for machine callers.
- [x] Specify classification precedence/aggregation for mixed non-zero signals, including argument errors, runner exceptions, nil/absent-var synthetic entries, var-backed failures/errors, `:unknown` canonical entries, and zero executable tests.
- [x] Define how implementation should recognize a "test load failure / synthetic suite-level load error" from canonical results versus runner infrastructure errors or ordinary assertion failures.
- [x] Pin nil/absent-var synthetic naming for progress and result files: display text, file basename, indexing order, per-status versus global counters, behavior when `:ns` is present but `:var` is nil, and collision avoidance with var-backed filenames.
- [x] Decide which public docs must describe the new CLI classification contract and what machine callers should inspect instead of parsing stderr.
