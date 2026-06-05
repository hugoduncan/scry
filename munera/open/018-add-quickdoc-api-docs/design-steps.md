# Design follow-up steps

- [x] Specify that quickdoc/tooling dependencies must live only under a maintainer/docs-specific alias or bb task, not top-level `:deps` or published runtime dependency metadata, while still composing the optional Kaocha classpath for documenting `scry.kaocha`.
- [ ] Pin the exact var-level public API surface to include/exclude in generated docs for each included namespace, especially whether `scry.cli/run-cli`, `main-outcome`, `parse-main-args`, normalization helpers, and `scry.kaocha/result->scry` are documented user API or hidden/internal helpers.
- [ ] Choose the single maintainer API-docs command and its verification contract: whether it is a Babashka task or `clojure -M` alias, whether it overwrites/regenerates only or also has a check/no-diff mode, and which command implementation/final verification should record.
- [ ] Specify how required generated-doc notes such as optional `scry.kaocha` classpath requirements and pre-1.0 public-alpha status are produced reproducibly by quickdoc/source/doc-generation configuration rather than by manual edits that a rerun would discard.
