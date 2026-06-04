# Steps

## Slice 1 — README placement and core installation snippet

- [x] Choose the exact README insertion point for `Installation` after the introductory/why material and before `Usage`.
- [x] Add a core-only `deps.edn` snippet using `:aliases {:test {:extra-paths ["test"] :extra-deps {org.hugoduncan/scry {:mvn/version "RELEASE"}}}}`, with text saying projects should adjust/merge test classpath setup as needed.
- [x] Add a short command example showing the core alias shape with `clojure -M:test ...` or `clojure -X:test ...`.
- [x] Clarify whether the public `:test` alias snippet should include test classpath entries such as `:extra-paths ["test"]`, or explicitly omit them as project-specific setup, so the copyable install example and `clojure -M:test` commands are aligned.
- [x] Align `design.md` with the plan-approved core alias snippet by adding `:extra-paths ["test"]` to the design's example or explicitly marking that example as abbreviated, so the stable design no longer conflicts with `plan.md` / `steps.md`.

## Slice 2 — Optional Kaocha installation snippet

- [x] Add an optional Kaocha adapter `deps.edn` snippet with a composable `:kaocha` alias containing `org.hugoduncan/scry-kaocha` at `{:mvn/version "RELEASE"}`.
- [x] State that the adapter dependency should use the same version token/value as the core dependency.
- [x] State that the adapter artifact brings same-version `scry` core and Kaocha transitively, so users do not need to declare Kaocha separately unless they deliberately manage or override it.
- [x] Add a short optional Kaocha command example using `clojure -M:test:kaocha ...` or `clojure -X:test:kaocha ...`.

## Slice 3 — Command and version consistency pass

- [x] Ensure all new snippets use the exact version token `"RELEASE"` and do not invent a numeric version.
- [x] Add explanatory text that concrete published versions are Git-count versions such as `0.1.N` / `0.1.<git-count>` and should be pinned for reproducible builds.
- [x] Reread the existing command-line and Kaocha README sections and adjust any nearby wording that conflicts with the new installation aliases or dependency guidance.

## Slice 4 — Documentation verification and task notes

- [x] Verify the README installation, command-line, and Kaocha sections are internally consistent after editing.
- [x] Record the documentation-only verification and any noteworthy decisions in `implementation.md`.
