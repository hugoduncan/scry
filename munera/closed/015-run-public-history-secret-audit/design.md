# Run public history secret audit

## Goal

Audit the repository contents and Git history for secrets or private-only material before making the repository public.

## Context

A current-tree grep found only secret names such as `CLOJARS_PASSWORD`, not actual credentials. Before public release, the Git history should also be checked because public visibility exposes all reachable commits, not just the current tree. The maintainer has decided Munera and Mementum should remain included, so the audit should focus on secrets and genuinely private content rather than removing those systems.

## Scope

- Audit current tracked files and Git history for credentials, tokens, private keys, personal/private paths that should not be public, and confusing private references.
- Audit the current tree at `HEAD` and Git history reachable from local public-release-relevant refs: all local branches (`refs/heads/*`), tags (`refs/tags/*`), remote-tracking refs (`refs/remotes/*`), and `refs/stash` if present. Unreachable objects, reflog-only commits, and orphaned local object database contents are out of scope unless a scanner reports them through the chosen reachable-ref scan.
- Use `gitleaks detect --source . --redact --log-opts="--all"` as the preferred automated secret scan when `gitleaks` is already available on `PATH`. The `--log-opts="--all"` option is required so the gitleaks Git-history scan explicitly matches this task's reachable-ref scope: `HEAD` plus all refs under `refs/`, including local branches, tags, remote-tracking refs, and `refs/stash` if present. If the installed gitleaks version requires different syntax, use an equivalent explicit-ref invocation and record it. Installing missing tools is not required for this task. If `gitleaks` is unavailable, use and record a grep-based fallback over the current tree plus reachable history. If `gitleaks` is available but errors, record the error and do not treat grep-only fallback as equivalent without noting the scanner failure.
- Review `.psi/extensions.edn`, Munera, and Mementum content with the explicit decision that Munera/Mementum stay public.
- Classify safe-but-confusing private references separately from secrets. Examples include absolute local paths, private project names such as prior `../../psi/psi-main` references, and `.psi` extension references. Fix them during this task only when they are trivial current-tree edits in user-facing/public release documentation or metadata; otherwise list them as follow-up with the reason they are safe from a secrets perspective.
- Produce a concise audit result in `implementation.md` with commands run and findings. Sensitive findings must be recorded only as redacted metadata and remediation options: do not copy full secret values, private key bodies, or token strings into `implementation.md` or other tracked notes.
- If sensitive material is found, stop and document remediation options instead of making cosmetic edits that leave history exposed.

## Out of scope

- Removing Munera or Mementum wholesale.
- Rewriting Git history unless the audit finds sensitive content and the maintainer explicitly chooses that remediation.
- Auditing unreachable objects, reflog-only commits, or local-only object database contents that are not reachable from the scoped refs.
- Installing new secret-scanning tools.
- Publishing or changing repository visibility.

## Acceptance criteria

- Current tree and scoped reachable Git history have been audited with recorded commands/results and the exact ref/history scope used.
- The preferred automated scanner was run, or the `gitleaks`-unavailable fallback condition and grep-based audit commands are recorded.
- No actual secrets, credentials, or private keys remain in scoped reachable history, or remediation is explicitly documented as required before public release using only redacted finding metadata.
- Any confusing private references that are safe but worth cleaning are either fixed when trivial and current-tree user-facing/public-release material, or listed as follow-up items with a brief safe/not-secret classification.
- The final audit note clearly states whether the repo is safe to make public from a secrets/history perspective.
