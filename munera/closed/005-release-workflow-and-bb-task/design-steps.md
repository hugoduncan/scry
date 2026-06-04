# Design follow-up steps

- [x] Clarify dry-run ref/version consistency: define how the selected dispatch ref is resolved locally and remotely, require the expected version to be computed for that exact commit, and fail if the local checkout/selected ref does not match the remote ref that the workflow will check out.
- [x] Pin the changelog heading syntax and extraction rules used by stamping and the release workflow, especially whether release headings are bare (`## VERSION - DATE`) or bracketed (`## [VERSION] - DATE`) given the existing `## Unreleased` format.
- [x] Specify the accepted release tag format for publishing workflow runs, including whether tags must be exactly `v0.1.<git-count>` and how nonconforming `v*` tags should fail.
