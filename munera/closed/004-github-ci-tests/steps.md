# Steps

## Slice 1: Workflow skeleton and setup

- [x] Create `.github/workflows/ci.yml` with the required `pull_request` trigger.
- [x] Add the required `push` trigger restricted to the `master` branch.
- [x] Define a simple single Linux job for CI verification.
- [x] Add `actions/checkout` with `fetch-depth: 0` so Git-derived build versioning has full history.
- [x] Add a Temurin LTS JDK setup step using a maintained, pinned GitHub Action version.
- [x] Add a Clojure CLI setup step using a conventional, pinned GitHub Action version.
- [x] Resolve and document the exact setup action/version pinning policy for Java and Clojure CLI setup (for example major tag, full version tag, or SHA) before adding those workflow steps.

## Slice 2: Verification commands

- [x] Add a workflow step that runs the core scry test command from `AGENTS.md`.
- [x] Add a workflow step that runs the focused Kaocha adapter test command from `AGENTS.md` with the `:kaocha` alias.
- [x] Add a workflow step that runs the focused build checks command from `AGENTS.md` with the `:build` alias.
- [x] Add a workflow step that runs `clojure -T:build jar`.
- [x] Confirm the workflow contains no publish, deploy, artifact upload, or release steps.

## Slice 3: Developer documentation

- [x] Review `AGENTS.md` and decide whether CI maintenance notes are needed beyond the existing local command documentation.
- [x] Update `AGENTS.md` if CI-specific maintainer guidance is needed.
- [x] Add a `CHANGELOG.md` Unreleased entry describing the new GitHub Actions test/build CI workflow.

## Slice 4: Validation

- [x] Run `mise exec -- actionlint` or an equivalent local `actionlint` command against the workflow.
- [x] Run the core scry test command locally, unless already covered by an equivalent verification run during implementation.
- [x] Run the focused Kaocha adapter test command locally, unless already covered by an equivalent verification run during implementation.
- [x] Run the focused build checks command locally, unless already covered by an equivalent verification run during implementation.
- [x] Run `clojure -T:build jar` locally, unless already covered by an equivalent verification run during implementation.
- [x] Record validation commands and outcomes in `implementation.md`.

## Review follow-up

- [x] Remove or update the stale opening `implementation.md` sentence that says "No implementation yet." now that implementation and validation are complete.
- [x] Make each CI test/check step exit non-zero when its Clojure result reports failures or errors, so failing core, Kaocha, or focused build tests fail the GitHub Actions job.
