# Design follow-up steps

- [ ] Specify that quickdoc/tooling dependencies must live only under a maintainer/docs-specific alias or bb task, not top-level `:deps` or published runtime dependency metadata, while still composing the optional Kaocha classpath for documenting `scry.kaocha`.
