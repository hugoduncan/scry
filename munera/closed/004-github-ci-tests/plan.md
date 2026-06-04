# Plan

## Approach

Add a simple GitHub Actions CI workflow under `.github/workflows/` that mirrors the maintainer verification commands documented in `AGENTS.md`. Use one Linux job to keep the workflow easy to understand and maintain.

Key decisions:

- Use the required triggers exactly: `pull_request` and `push` to `master`. A manual `workflow_dispatch` trigger may be included only if it does not complicate the workflow.
- Use `actions/checkout` with `fetch-depth: 0` so `clojure -T:build jar` computes the same Git-derived version shape as local full-history builds.
- Use `actions/setup-java@v4` with Temurin JDK 21. This uses the maintained official setup action's stable major tag, which is the pinning granularity used for first-party GitHub setup actions in this task.
- Use `DeLaGuardo/setup-clojure@13.4` to install the Clojure CLI. This uses a concrete released action tag for the third-party Clojure setup action rather than a floating branch or unversioned reference.
- Run the CI commands in the same order as local maintainer verification: core tests, Kaocha adapter tests, focused build checks, then jar build.
- Do not add publishing, deploying, artifact upload, branch protection, or a broad matrix.
- Validate the workflow locally with `mise exec -- actionlint` or an equivalent `actionlint` command. `actionlint` is required for implementation validation but is not part of the CI workflow for this task.
- Update `AGENTS.md` only if the workflow introduces maintainer CI guidance that is not already covered by existing command documentation.
- Add a `CHANGELOG.md` Unreleased note because CI coverage is developer-visible project behavior.

## Risks

- Clojure setup action names/options may drift over time; pinning action versions and keeping setup minimal reduces surprise.
- Dependency downloads can make first CI runs slower; avoid cache complexity unless the chosen setup action offers a simple conventional cache.
- The jar build depends on Git metadata; forgetting `fetch-depth: 0` would make version checks less representative.
- `actionlint` availability depends on local `mise` setup; if `mise exec -- actionlint` is unavailable, use an equivalent local `actionlint` binary and record the validation command in task notes.

## Slice order

1. **Workflow skeleton and setup** — create `.github/workflows/ci.yml` with required triggers, checkout full history, Java setup, and Clojure CLI setup.
2. **Verification commands** — add steps for core tests, Kaocha adapter tests, focused build checks, and `clojure -T:build jar`, matching `AGENTS.md` commands.
3. **Developer documentation** — update `AGENTS.md` only if needed for CI maintenance notes, and add a `CHANGELOG.md` Unreleased entry.
4. **Validation** — run `actionlint` locally, then run the same Clojure verification commands locally where practical, and record results in `implementation.md`.
