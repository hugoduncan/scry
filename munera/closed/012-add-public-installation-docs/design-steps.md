# Design follow-up steps

- [x] Specify whether README installation snippets should use a test/development alias (`:aliases {:test {:extra-deps ...}}`) or top-level `:deps`, and keep command examples aligned with that choice.
- [x] Resolve the copyable-snippet versus placeholder/current-version ambiguity by pinning the exact version text/token to use in installation examples, with explicit compatibility with Git-count `0.1.<git-count>` release versioning.
- [x] Clarify the optional Kaocha installation snippet's dependency composition: whether users should list both `org.hugoduncan/scry` and `org.hugoduncan/scry-kaocha` explicitly at the same version, and whether Kaocha itself is expected transitively via the adapter or separately declared.
