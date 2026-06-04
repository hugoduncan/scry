# Release workflow and Babashka release task

## Goal

Add a simple release workflow for `scry` that can publish the core library jar from a version tag, and add Babashka release tasks that prepare and push those version tags from `master`. The release path must also include a safe non-publishing dry run so maintainers can exercise release automation before cutting or publishing a real release.

The result should give maintainers a repeatable release path inspired by `psi`'s release workflow, but adapted to this much smaller library project:

1. Local maintainer can run a `bb` dry-run task to dispatch a non-publishing release workflow check.
2. Local maintainer runs a real `bb` release task.
3. The task stamps `CHANGELOG.md`, creates a release commit, tags it with the artifact version, and pushes `master` plus tags.
4. A GitHub Actions release workflow runs on `v*` tags, verifies the project, builds the jar, deploys to Clojars, and creates a GitHub Release.

## Context

Current release/build state:

- `build.clj` already builds a core-only jar for coordinate `org.hugoduncan/scry`.
- The artifact version is generated as `0.1.<git rev-list --count HEAD>`.
- The jar intentionally packages only `src`; the optional Kaocha adapter remains alias-only and is not part of the core artifact.
- CI already runs core tests, focused Kaocha adapter tests, focused build checks, and `clojure -T:build jar` on pull requests and pushes to `master`.
- There is no `bb.edn` yet.
- There is no release workflow yet.
- Local Maven `install` and remote Clojars `deploy` were explicitly deferred by the jar-build task.

Useful inspiration from `../../psi/psi-main`:

- `psi` has a `.github/workflows/release.yml` triggered by `push` tags matching `v*`, with a `workflow_dispatch` dry-run path.
- `psi` release CI checks out full history, installs Java, Clojure CLI, and Babashka, builds release artifacts, deploys to Clojars, smoke-tests the release, extracts the matching changelog section, and creates a GitHub Release.
- `psi` has Babashka release code that asserts a clean `master`, checks `[Unreleased]`, stamps the changelog, commits, tags, and pushes.

`scry` should keep the same broad shape while omitting complexity that is only needed by `psi`:

- No uberjar.
- No launcher or `bbin` smoke test.
- No Emacs, tmux, smoke, integration, format, or lint jobs unless added by separate tasks.
- No version resource file unless a future design explicitly changes version semantics.

## Required release behavior

### Local Babashka tasks

Add a minimal `bb.edn` and release implementation, likely under `bb/release.clj`, with release tasks such as:

```sh
bb release --dry-run  # dispatch the non-publishing GitHub Release workflow dry run
bb release:tag        # stamp changelog, commit, and create local vVERSION tag
bb release            # run/retry release:tag as needed, then push master and tags
```

The exact task names can be adjusted during planning, but maintainer-facing docs must make the chosen names clear.

The local dry-run task must:

- Resolve the dispatch target to one exact commit before invoking GitHub Actions. By default it uses the current local `HEAD` commit and finds a remote `origin` branch that points at that same SHA, preferring `master` when available. If an explicit dry-run ref option is supported, it must resolve both the local ref and `origin/<ref>` or the remote ref advertised by `git ls-remote` and require them to name the same commit.
- Compute the expected dry-run build version from the exact commit the workflow will check out, using the same Git-count semantics as `build/version` (`0.1.<git rev-list --count COMMIT>`). The task must pass both the selected checkout ref/SHA and that expected version to the workflow.
- Fail before dispatching if the selected local commit is not present at the selected remote ref, if the remote ref resolves to a different commit, or if the expected version cannot be computed for that exact commit.
- Not mutate the working tree, create commits, create tags, push, deploy, or create GitHub Releases.
- Validate enough local state to make the remote dry run meaningful, including a clean tree, a resolvable `origin` GitHub repository, and a dispatch ref that exists on the remote.
- Dispatch `.github/workflows/release.yml` through `gh workflow run`, `gh api`, or an equivalent documented GitHub CLI/API call.
- Pass the selected ref and expected current build version to the workflow so the dry run can verify that the built jar version matches the checked-out ref.
- Fail with clear instructions when the GitHub CLI is unavailable, unauthenticated, the workflow file cannot be found, or the selected ref has not been pushed.

The real release task must:

- Assert the working tree is clean before mutating files.
- Assert release is being cut from `master`.
- Assert `CHANGELOG.md` has a non-empty `## Unreleased` section.
- Compute the release version that the tag commit's `build/version` will produce.
  - Because `scry` uses Git commit count as the patch version, a release task that creates one changelog release commit must pre-compute the tag version as `0.1.<current-commit-count + 1>`.
  - The created tag must be `vVERSION`, and running `clojure -T:build jar` at that tag must produce `target/scry-VERSION.jar`.
- Stamp `CHANGELOG.md` by preserving a fresh empty bare `## Unreleased` section and moving the prior unreleased body under a bracketed release heading: `## [VERSION] - YYYY-MM-DD`. The task must not use bare release headings for stamped releases.
- Commit the changelog stamp with a clear release message.
- Create the `vVERSION` tag at that release commit.
- Push `master` and tags for `bb release`, with retry behavior or clear failure handling if a prior run created the local tag but failed before pushing.

Partial-failure recovery should be simple but deliberate. At minimum, the task should not silently create a second release when a matching local tag already exists; it should either push the existing unpushed tag or fail with clear instructions.

### GitHub Actions release workflow

Add `.github/workflows/release.yml`.

Required triggers:

```yaml
on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:
```

The workflow must support a non-publishing manual dispatch path for smoke-testing the release workflow from a selected ref. For this task, `workflow_dispatch` is dry-run-only by default: it must not deploy to Clojars, create a GitHub Release, or upload release assets. Publishing should happen only for pushed `v*` tags unless implementation deliberately adds a clearly guarded `workflow_dispatch` publish input with a default of `false` and explicit acceptance-test coverage.

The release workflow should define manual-dispatch inputs equivalent to:

- `ref` — branch, tag, or SHA to test; defaults to the current ref when omitted.
- `expected_version` or `release_version` — optional expected build version for dry-run validation; the `bb release --dry-run` task should pass the current checked-out build version.

The release job must:

- Check out full Git history (`fetch-depth: 0`) so the Git-derived version matches the tag commit or selected dry-run ref.
- Set up Java and the Clojure CLI consistently with the existing CI workflow unless there is a documented reason to differ.
- Install Babashka if the workflow uses `bb` tasks.
- Run the same verification commands as CI, or call documented `bb` wrappers for those commands if the task adds them.
- Build the jar.
- Confirm the tag name and built artifact version agree on tag-push publishing runs. Publishing tag pushes must use tags exactly matching `v0.1.<non-negative-integer>`; nonconforming `v*` tags must fail during validation before deploy or GitHub Release creation. The tag's version component must equal the built jar version derived from the checked-out tag commit.
- Confirm the supplied expected dry-run version and built artifact version agree on manual dry-run runs when an expected version is supplied.
- Deploy the library jar to Clojars on tag pushes using GitHub secrets, e.g. `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` or an explicitly documented Clojars token secret convention.
- Extract the release-specific `CHANGELOG.md` section and use it as the GitHub Release body. Extraction must look for the bracketed heading `## [VERSION] - YYYY-MM-DD`, collect content until the next `## ` heading or end of file, trim surrounding blank lines, and fail if the section is missing or empty.
- Create a GitHub Release for the tag and attach the built jar.

The workflow must not publish on a dry-run `workflow_dispatch` invocation. Dry-run must still validate setup, tests, build, expected-version agreement, and workflow syntax where practical. It should also verify changelog shape: for ordinary branch/SHA dry runs, `CHANGELOG.md` must have a non-empty bare `## Unreleased` section; for dry runs against an already stamped release ref, the requested version section must be extractable by the same bracketed `## [VERSION] - YYYY-MM-DD` rules used for publishing.

## Build/deploy support

Extend build support only as much as needed for release:

- Add a `build/deploy` task or equivalent that deploys the already-built or freshly-built core jar to Clojars using `deps-deploy`.
- Keep `deps-deploy` scoped to a deploy/release alias so normal core users do not gain an unnecessary runtime dependency.
- Consider adding `build/install-local` if it makes dry-run verification or focused tests clearer, but local Maven install is not required unless the final plan chooses to smoke-test a locally installed artifact.
- Preserve the core-only jar boundary: deploy must publish the same artifact shape currently built by `clojure -T:build jar`.

## Documentation expectations

Update maintainer-facing documentation:

- `AGENTS.md` should document the release workflow, required secrets, local `bb` commands, dry-run behavior, and the relationship between Git commit count and release tags.
- `CHANGELOG.md` should get an Unreleased entry for the new release automation.
- `README.md` should remain focused on public `scry` API usage unless the implementation changes user-facing installation/release information.

## Out of scope

- Changing public `scry` result shapes or runner behavior.
- Publishing the optional Kaocha adapter as a separate artifact.
- Including `src-kaocha` or Kaocha dependencies in the core jar.
- Adding an uberjar, launcher, `bbin` install, or runtime smoke test.
- Adding broad CI matrices, branch protection, or release approval environments.
- Changing the versioning scheme away from `0.1.<git commit count>` unless a separate task redesigns build versioning first.
- Automating Clojars account creation or secret provisioning.

## Design considerations

- The biggest correctness risk is an off-by-one release version caused by stamping `CHANGELOG.md` before tagging. The release task and tests should make the intended count explicit: release tagging computes `0.1.<current-count + 1>` before the changelog commit, while dry-run computes `0.1.<count at the exact remote commit being checked out>` without creating a commit.
- A release workflow should duplicate enough CI verification to protect tag pushes even if someone bypasses normal PR flow.
- Keep manual dispatch dry-run useful but non-destructive. It should be safe to run without Clojars credentials, and `bb release --dry-run` should be the normal local entry point for triggering it.
- Prefer simple shell/Clojure/Babashka code over a general-purpose release framework.
- Use `actionlint` as the local static validator for workflow changes, as in the CI task.
- Use tests around pure or injectable release helper functions where practical, especially version computation, changelog stamping, and tag/version agreement. Avoid tests that mutate the real repository unless isolated in a temporary Git repository.

## Acceptance criteria

- `bb.edn` exists with documented release task(s).
- A release helper namespace/script exists if needed to keep `bb.edn` simple.
- `bb release --dry-run` or the chosen equivalent exists, does not mutate local or remote release state, and dispatches the release workflow in non-publishing dry-run mode for a selected ref.
- The dry-run workflow path runs tests/checks, builds the jar, verifies the expected dry-run version when supplied, and skips Clojars deploy and GitHub Release creation.
- `bb release:tag` or the chosen equivalent validates clean `master`, non-empty Unreleased changelog, computes the correct tag version for the release commit, stamps `CHANGELOG.md`, commits, and creates a `vVERSION` tag.
- `bb release` or the chosen equivalent pushes `master` and tags, or clearly documents/preserves a two-step local release process.
- `.github/workflows/release.yml` exists.
- The release workflow runs on pushed `v*` tags.
- The release workflow supports a safe non-publishing manual dispatch path with documented inputs for ref and expected version.
- The release workflow checks out full history.
- The release workflow runs the required tests/checks and jar build before publishing.
- The release workflow verifies the tag version matches the built jar version.
- The release workflow deploys the core jar to Clojars only on publishing paths.
- The release workflow creates a GitHub Release with the release changelog body and attaches the jar.
- Build/deploy dependencies remain scoped to maintainer/release aliases and do not alter the core library dependency boundary.
- Tests or focused checks cover release helper behavior, especially changelog stamping and Git-count version calculation.
- `actionlint` passes locally for the release workflow.
- Existing core tests, focused Kaocha adapter tests, focused build checks, and jar build still pass.
- `AGENTS.md` and `CHANGELOG.md` are updated for the release workflow.
