# Plan

## Approach

This task is an audit/reporting task, not a remediation or publishing task. Execute the audit in a way that makes the exact repository scope, scanner availability, commands, findings, and final public-safety recommendation reproducible from `implementation.md`.

Key decisions:

- Treat the stable `design.md` scope as authoritative: audit the current tree at `HEAD` plus history reachable from local branches, tags, remote-tracking refs, and `refs/stash` if present; do not chase unreachable objects or reflog-only commits unless the chosen scanner reports them through the scoped reachable-ref scan.
- Prefer `gitleaks detect --source . --redact --log-opts="--all"` when `gitleaks` is already installed. If the installed version requires different syntax, use an equivalent explicit-ref invocation and record the exact command. Do not install missing scanners for this task.
- If `gitleaks` is unavailable, run and record a grep-based fallback over both the current tracked tree and reachable history. If `gitleaks` is available but errors, record the error and clearly state that any grep fallback is not equivalent to a successful automated scan.
- Classify findings into: blocking sensitive material, safe secret-name-only references, safe-but-confusing private references, and trivial current-tree public-facing cleanup candidates.
- Preserve the explicit maintainer decision that Munera and Mementum remain public; review them for secrets/private-only content, not for wholesale removal.
- If sensitive material is found, stop after documenting redacted metadata and remediation options; do not make cosmetic edits that would leave exposed history in place.
- Record sensitive findings only in redacted form. Do not copy full credential values, token strings, private key bodies, or equivalent secret material into tracked notes.

## Required recording details

### Scope inventory format

In `implementation.md`, record the audit scope before scanning with both commands and results. Include:

- `HEAD`: commit object ID from `git rev-parse HEAD`, plus a short subject from `git log -1 --oneline`.
- Local branches: each `refs/heads/*` ref name and object ID from `git for-each-ref --format='%(refname) %(objectname)' refs/heads`.
- Tags: each `refs/tags/*` ref name and object ID from `git for-each-ref --format='%(refname) %(objectname)' refs/tags`.
- Remote-tracking refs: each `refs/remotes/*` ref name and object ID from `git for-each-ref --format='%(refname) %(objectname)' refs/remotes`.
- Stash: record either the `refs/stash` object ID from `git rev-parse --verify refs/stash` or that `refs/stash` is absent.

If a ref class is empty, record it as empty rather than omitting it.

### Gitleaks exit status interpretation

When `gitleaks` is available, capture the exact command, exit status, stdout/stderr summary, and whether the run completed as a scan. Interpret status as follows:

- Exit status `0`: completed scan with no detected leaks/findings.
- Exit status matching gitleaks' leak/finding status (default `1`) with redacted finding output or a finding summary and no usage/fatal scanner message: completed scan with detected findings; classify those findings and stop for redacted remediation if any are blocking sensitive material.
- Any non-zero status caused by usage errors, unsupported flags, repository/source errors, crashes, or otherwise ambiguous scanner failure: operational scanner error. Record the error and do not present grep-only fallback as equivalent to a successful automated scan.

If the installed gitleaks version rejects `--log-opts="--all"`, try an equivalent explicit-ref invocation only if the version supports one; otherwise record an operational scanner error and continue with the grep fallback/complement under that limitation.

### Minimum manual grep fallback/complement

Run the manual grep audit even when gitleaks succeeds; when gitleaks is unavailable or errors, explicitly label it as the fallback. At minimum, use separate pattern groups for:

1. Secret/credential words: `secret`, `password`, `passwd`, `token`, `credential`, `api key`, `apikey`, `access key`, `private key`, `bearer`, `CLOJARS_USERNAME`, and `CLOJARS_PASSWORD`.
2. Private key markers and common token prefixes: `-----BEGIN ... PRIVATE KEY-----`, `-----BEGIN OPENSSH PRIVATE KEY-----`, `ghp_`, `github_pat_`, `glpat-`, `AKIA...`, `xox...`, and obvious assignment/header forms such as `Authorization: Bearer`, `password=`, `token=`, and `api_key=`.
3. Local/private reference markers: absolute local paths such as `/Users/` and `/home/`, `.psi`, `psi-main`, and private/internal project-reference terms surfaced by the current tree.

For current tracked files, use `git grep -n -I` with case-insensitive or exact-case variants appropriate to each pattern group, and record each command. For scoped reachable history, use commands shaped like either:

```sh
git rev-list --all | xargs -r -n 100 sh -c 'git grep -n -I -E "$1" "$@" -- .' sh "$PATTERN"
```

or:

```sh
git log --all -G "$PATTERN" --pickaxe-regex --pretty='format:%H %D %s' -- .
```

Use `--all` for history commands so refs under `refs/` are covered, including local branches, tags, remote-tracking refs, and `refs/stash` when present. Record any command limitations, noisy matches, and whether matches are actual secrets, secret-name-only references, or safe-but-confusing private references.

## Risks

- Scanner availability is unknown until execution; `gitleaks` may be absent or may use a version-specific option syntax for history/ref selection.
- Grep fallback can miss high-entropy or format-specific secrets that an automated scanner would catch, so fallback results must be described with that limitation.
- History scans can surface safe-but-confusing references to local paths, `.psi`, or prior private project names; these need careful classification so public-safety blockers are not confused with harmless provenance.
- Remediating an actual historical secret is intentionally out of scope without maintainer choice, because it may require history rewrite, revocation, rotation, or coordinated release decisions.
- Recording audit notes has its own leakage risk; findings must remain redacted when sensitive.

## Slice order

1. **Scope inventory and baseline recording** — identify the exact refs and stash presence that define the audit scope, confirm scanner availability, and begin `implementation.md` with scope/command recording conventions.
2. **Automated scanner slice** — run the preferred redacted `gitleaks` reachable-history scan when available, or record unavailability/error state and the planned fallback status.
3. **Manual grep fallback/complement slice** — audit current tracked files and scoped reachable history for secret-like patterns, token/key markers, private paths, private project references, and safe-but-confusing references.
4. **Munera/Mementum/.psi review slice** — review `.psi/extensions.edn`, Munera, and Mementum content under the explicit decision that they remain public.
5. **Classification and trivial cleanup slice** — classify all findings; perform only trivial current-tree edits for user-facing/public-release references when they are clearly safe and in scope, otherwise list follow-up items.
6. **Final audit report slice** — record commands/results, redacted findings or clean result, limitations, follow-ups, and a clear safe/not-safe-to-make-public recommendation in `implementation.md`.
