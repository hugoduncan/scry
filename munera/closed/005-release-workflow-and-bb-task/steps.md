# Steps

## Slice 1: Release helper design and tests

- [x] Inspect `build.clj`, `deps.edn`, `.github/workflows/ci.yml`, `CHANGELOG.md`, and any release-related patterns in `../../psi/psi-main` needed for a minimal scry adaptation.
- [x] Choose the concrete release helper namespace/script layout under `bb/` and the focused test namespace/location for release helper checks.
- [x] Plan-review follow-up: pin the exact focused release helper/task test command and any required `deps.edn`/`bb.edn` alias or classpath changes before implementing the tests.
- [x] Add helper logic for computing build versions from an exact Git commit count and for computing the next release version as `0.1.<current-count + 1>` before the changelog commit.
- [x] Add helper logic for validating publishing tags exactly matching `v0.1.<non-negative-integer>` and extracting the version component.
- [x] Add helper logic for detecting a clean working tree, current branch `master`, local `HEAD`, remote refs, and local/remote commit agreement through an injectable command boundary.
- [x] Add helper logic for stamping `CHANGELOG.md` by preserving a fresh empty `## Unreleased` section and moving the previous non-empty body under `## [VERSION] - YYYY-MM-DD`.
- [x] Add helper logic for extracting a bracketed release changelog section by version and failing when the section is missing or empty.
- [x] Add focused tests for release version math, including the off-by-one release commit case and dry-run exact-commit case.
- [x] Add focused tests for tag validation, including valid `v0.1.N` tags and nonconforming `v*` tags that must fail before publishing.
- [x] Add focused tests for changelog stamping and release-section extraction, including missing/empty Unreleased and missing/empty release section failures.
- [x] Add focused tests using a temporary Git repository or injectable process boundary for local/remote ref agreement and partial-failure existing-tag handling.

## Slice 2: Babashka task wiring

- [x] Add a minimal `bb.edn` that exposes the chosen release tasks and keeps task bodies small.
- [x] Implement `bb release --dry-run` or the chosen equivalent as a non-mutating workflow-dispatch path.
- [x] Make the dry-run task resolve the dispatch target to one exact local commit and matching remote ref, preferring `origin/master` when appropriate.
- [x] Make the dry-run task compute and pass the expected version for the exact checked-out commit to the release workflow dispatch.
- [x] Make the dry-run task fail clearly before dispatch if `gh` is unavailable, unauthenticated, the workflow file is missing, the tree is dirty, no GitHub `origin` is resolvable, the selected ref is not pushed, or local/remote commits differ.
- [x] Implement `bb release:tag` or the chosen equivalent to validate clean `master`, require non-empty `## Unreleased`, stamp `CHANGELOG.md`, commit the stamp, and create the local `vVERSION` tag.
- [x] Ensure `bb release:tag` computes the tag version as `0.1.<current commit count + 1>` before creating the changelog commit.
- [x] Implement `bb release` or the chosen equivalent to create/reuse the local release tag as needed and push `master` plus tags.
- [x] Add clear handling for partial failures where a matching local tag already exists but has not been pushed.
- [x] Run the focused release helper/task tests and record the command in `implementation.md`.

## Slice 3: Build deploy support

- [x] Add `deps-deploy` only under a maintainer/release/build alias so it is not a core runtime dependency.
- [x] Plan-review follow-up: specify the `build/deploy` interface for credentials and artifact coordinates (env/property names, jar/pom path inputs, and whether it builds first) so the workflow, docs, and focused checks use the same deploy contract.
- [x] Add a `build/deploy` task or equivalent that deploys the existing core-only jar artifact to Clojars.
- [x] Ensure deploy either builds the jar or clearly requires/uses the freshly built jar path produced by `clojure -T:build jar`.
- [x] Add focused build/deploy tests or checks verifying deploy support preserves the existing core-only jar boundary and does not include `src-kaocha` or Kaocha dependencies.
- [x] Verify existing focused build checks still pass after alias/build changes.

## Slice 4: Release workflow skeleton and dry-run path

- [x] Create `.github/workflows/release.yml` with `push` tags matching `v*` and `workflow_dispatch` triggers.
- [x] Add manual dispatch inputs for selected `ref`, exact `sha`, and optional `expected_version` or equivalent, matching the plan's separate dry-run dispatch inputs.
- [x] Plan-review follow-up: decide and document whether workflow dispatch uses one `ref` input or separate `ref`/`sha` inputs, and add a dry-run workflow check that the checked-out commit equals the exact SHA resolved by `bb release --dry-run`.
- [x] Configure full-history checkout for tag pushes and manual dry runs so Git-count versioning is accurate.
- [x] Add Java setup using `actions/setup-java@v4` with Temurin JDK 21 unless a documented incompatibility is found.
- [x] Add Clojure CLI setup using `DeLaGuardo/setup-clojure@13.4` unless a documented incompatibility is found.
- [x] Install Babashka only if release workflow steps call `bb` tasks or scripts.
- [x] Add workflow steps for the same core tests, focused Kaocha adapter tests, focused build checks, and jar build used by CI.
- [x] Add dry-run expected-version verification that compares the supplied expected version with the built jar version.
- [x] Add dry-run changelog-shape verification for non-stamped refs with non-empty `## Unreleased` and for stamped refs with extractable bracketed release sections.
- [x] Ensure every dry-run path skips Clojars deploy, GitHub Release creation, and release asset upload.

## Slice 5: Publishing path

- [x] Add publishing-path validation that fails nonconforming `v*` tags unless they exactly match `v0.1.<non-negative-integer>`.
- [x] Add publishing-path validation that the tag version equals the jar version produced from the checked-out tag commit.
- [x] Add a workflow step that deploys the core jar to Clojars only on validated tag-push publishing runs.
- [x] Use and document the chosen Clojars secret names, such as `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` or token equivalents.
- [x] Add a workflow step that extracts the bracketed `CHANGELOG.md` section for the published version and fails if missing or empty.
- [x] Add a workflow step that creates a GitHub Release for the tag using the extracted changelog body.
- [x] Attach the built jar artifact to the GitHub Release.
- [x] Confirm workflow conditions prevent deploy/release creation on all `workflow_dispatch` dry runs.

## Slice 6: Maintainer documentation

- [x] Update `AGENTS.md` with the maintainer release workflow, required Clojars/GitHub secrets, local `bb` commands, dry-run behavior, and Git-count tag/version relationship.
- [x] Add a `CHANGELOG.md` Unreleased entry for the new Babashka release tasks and GitHub Actions release workflow.
- [x] Review `README.md` and leave it unchanged unless implementation changes public installation or API behavior.

## Slice 7: Validation and handoff

- [x] Run the focused release helper/task tests and record results in `implementation.md`.
- [x] Run `mise exec -- actionlint` or an equivalent `actionlint` command against `.github/workflows/release.yml` and record results in `implementation.md`.
- [x] Run the core scry test command from `AGENTS.md` and record results in `implementation.md`.
- [x] Run the focused Kaocha adapter test command from `AGENTS.md` and record results in `implementation.md`.
- [x] Run the focused build checks command from `AGENTS.md` and record results in `implementation.md`.
- [x] Run `clojure -T:build jar` and record results in `implementation.md`.
- [x] Review `git diff` to confirm no out-of-scope public API, runner behavior, or core jar boundary changes were introduced.
- [x] Implementation-review follow-up: fix `bb.edn` task wiring so `bb release --dry-run`, `bb release:tag`, and `bb release` can resolve and execute the `scry.release` entry points under Babashka; validate at least one non-mutating execution path, not just `bb tasks`.
- [x] Implementation-review follow-up: make `bb release --dry-run` fail with a clear GitHub `origin` guidance message when `origin` is missing or not a GitHub remote, instead of surfacing a generic `Git command failed` from `git remote get-url origin`.
- [x] Implementation-review follow-up: tighten release workflow dry-run changelog validation so dry runs against stamped release refs/tags require the matching bracketed release section instead of falling back to a non-empty `## Unreleased` section when the requested version section is missing or empty.
- [x] Test-review follow-up: add a focused non-mutating test for `dispatch-dry-run!`/`release-dry-run-plan` through an injectable command boundary that exercises a successful dry-run dispatch and asserts the `gh workflow run .github/workflows/release.yml --ref REF -f ref=REF -f sha=SHA -f expected_version=VERSION` contract.
- [x] Test-review follow-up: extract the release workflow dry-run changelog-shape decision into testable helper logic and cover stamped refs/tags requiring the matching bracketed `## [VERSION] - YYYY-MM-DD` section, while ordinary branch/SHA dry runs may fall back to non-empty `## Unreleased`.
- [x] Test-review follow-up: add a focused isolated test for the high-level release-tag path (`create-release-tag!` and/or `bb release:tag`) that uses a temporary workdir/repository plus injectable command boundary to verify off-by-one `0.1.<current-count + 1>` versioning, `CHANGELOG.md` stamping, and the expected `git add`/`commit`/`tag` sequence without mutating the real repository.
- [x] Test-review follow-up: add a focused non-mutating test for the high-level `push-release!`/`bb release` existing-tag recovery path that uses an injectable command boundary to verify an existing unpushed release tag at `HEAD` pushes `master` and that tag without creating a second release tag.
- [x] Test-review follow-up: make the release workflow file presence check injectable/nullable and add a focused non-mutating test proving `release-dry-run-plan` fails clearly before dispatch when `.github/workflows/release.yml` is missing.
- [x] Test-review follow-up: pass the injectable workflow-file presence guard through `dispatch-dry-run!` and add focused non-mutating tests for the high-level dry-run dispatch entry point, including missing-workflow failure before `gh` dispatch without depending on the real repository workflow file.
- [x] Test-review follow-up: add a focused static workflow check covering release publishing gates, asserting dry-run `workflow_dispatch` cannot reach Clojars deploy, GitHub Release creation, or artifact upload steps, and those steps remain limited to validated `push` `refs/tags/v*` publishing runs.
- [x] Code-shaper follow-up: make release Git state checks fail closed by validating `git status --porcelain` and branch-resolution command exits before treating output as clean/master; add focused coverage for failed Git state commands.
- [x] Code-shaper follow-up: make release tag-at-HEAD detection fail clearly when multiple valid `v0.1.N` tags point at `HEAD` instead of silently choosing the first sorted tag for `bb release` retry/push behavior.
- [x] Code-shaper follow-up: make `bb release --ref REF` without `--dry-run` fail clearly (or otherwise reject ignored options) so maintainer CLI arguments are never silently ignored.
