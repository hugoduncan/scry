# Design follow-up steps

- [x] Pin the Git history/ref scope for the audit: which refs are scanned (for example HEAD, all local branches, tags, remotes, stash) and whether unreachable/reflog-only commits are in or out of scope.
- [x] Choose the automated secret-scanner expectation: preferred tool/command(s), whether installing a missing tool is expected, and the exact fallback condition for grep-only auditing.
- [x] Define how to classify and handle safe-but-confusing private references, including absolute local paths, private project names such as prior `../../psi/psi-main` references, and `.psi` extension references: fix during the task versus list as follow-up.
- [x] Specify that any sensitive finding must be recorded only with redacted metadata/remediation options, not by copying full secret values into `implementation.md` or other tracked notes.
- [x] Align the preferred gitleaks command with the declared reachable-ref audit scope, for example by documenting `--log-opts=--all` or an equivalent explicit-ref invocation that covers local branches, tags, remote-tracking refs, and `refs/stash` if present.
