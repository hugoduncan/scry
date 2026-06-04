# Plan

## Approach

Implement this as a README-only public documentation update. Add a new `Installation` section near the top of `README.md`, after the introductory/why material and before `Usage`, so public users see dependency coordinates before REPL and CLI examples.

Key decisions from the stable design:

- Show `scry` as a test/development dependency, not as a top-level application dependency. The public copyable `:test` alias should include the conventional test classpath and dependency together: `:aliases {:test {:extra-paths ["test"] :extra-deps {org.hugoduncan/scry {:mvn/version "RELEASE"}}}}`. Add a note that projects with different test/dev classpaths should adjust `:extra-paths`, and projects with an existing `:test` alias should merge the `:extra-deps` into that alias rather than replacing project-specific setup.
- Use the exact copyable version token `"RELEASE"` in dependency snippets.
- Explain that published concrete releases are Git-count versions such as `0.1.N` / `0.1.<git-count>`, and that reproducible builds should pin the latest concrete Clojars version instead of `RELEASE`.
- Show optional Kaocha support as a composable `:kaocha` alias used with `:test` (`clojure -M:test:kaocha ...` / `clojure -X:test:kaocha ...`).
- List `org.hugoduncan/scry-kaocha` explicitly at the same version token/value as core, and state that the adapter artifact brings same-version `scry` core and Kaocha transitively unless users intentionally override dependencies through normal `deps.edn` resolution.
- Preserve existing README usage, result-shape, CLI, and Kaocha behavior examples; adjust only if needed for consistency with the new installation section.

## Risks

- `RELEASE` is convenient but not reproducible; the README must clearly recommend pinning concrete `0.1.N` versions for repeatable builds.
- The Kaocha adapter dependency text could imply core users need Kaocha; keep the optional adapter boundary explicit.
- The existing Kaocha adapter section already mentions artifact coordinates; avoid contradictory or overly duplicated wording.
- No code verification is expected, but the README should be reread around Installation, Usage, CLI, and Kaocha sections to catch stale command aliases or dependency claims.

## Slice order

1. **README placement and core installation snippet** — add the `Installation` section and the core `:test` alias dependency example for `org.hugoduncan/scry`.
2. **Optional Kaocha installation snippet** — add the composable `:kaocha` alias example for `org.hugoduncan/scry-kaocha`, with same-version and transitive-dependency guidance.
3. **Command and version consistency pass** — ensure nearby command examples use `:test` and `:test:kaocha` consistently, and that `RELEASE` / concrete Git-count version wording is unambiguous.
4. **Documentation verification and task notes** — reread relevant README sections, record the documentation-only verification in `implementation.md`, and leave implementation ready for later review.
