# Design Steps

- [x] Clarify whether `:scry.cli/diagnostic-error` is an allowed value of `:scry.cli/outcome-kind` or only an additive top-level diagnostic key attached to a test-derived outcome; the design currently says to extend the outcome taxonomy "with `:scry.cli/diagnostic-error` alongside existing" but also requires the outcome kind to remain `:scry.cli/test-failure` when serialization fails.
- [x] Clarify what structured shape and required fields `:scry.cli/diagnostic-error` must contain so implementations and tests can assert it without overfitting to incidental exception formatting.
- [x] Clarify the required ordering and duplication rules for the "minimal test summary before detailed serialization work" relative to existing CLI stdout summary behavior, progress labels, stderr failure diagnostics, and `-X` return maps.
- [x] Clarify Layer 5's "runner-level exception that occurred after collecting entries" case: how the CLI determines entries were collected, whether the primary outcome remains runner-error or becomes test-derived, and which collected-entry/root-cause fields must appear in the fallback diagnostic.
