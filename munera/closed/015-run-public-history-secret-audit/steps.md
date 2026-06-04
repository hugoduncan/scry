# Steps

## Slice 1: Scope inventory and baseline recording

- [x] Record the task scope in `implementation.md`: current `HEAD` plus history reachable from local branches, tags, remote-tracking refs, and `refs/stash` if present.
- [x] Run and record commands that identify `HEAD`, local branches, tags, remote-tracking refs, and whether `refs/stash` exists.
- [x] Check whether `gitleaks` is available on `PATH` and record the version or unavailability.
- [x] Establish the audit note format in `implementation.md`, including the rule that sensitive findings are recorded only as redacted metadata and remediation options.

## Slice 2: Automated scanner

- [x] If `gitleaks` is available, run `gitleaks detect --source . --redact --log-opts="--all"` or an equivalent explicit-ref command required by the installed version.
- [x] Record the exact gitleaks command, exit status, and redacted result summary in `implementation.md`.
- [x] If gitleaks is unavailable, record that no installation was performed and that the grep-based audit is the fallback.
- [x] If gitleaks is available but errors, record the error and note that any grep fallback is not equivalent to a successful automated scan.

## Slice 3: Manual grep fallback/complement

- [x] Audit current tracked files for secret-like strings, credential names, private key markers, tokens, local absolute paths, `.psi` references, and private project references.
- [x] Audit scoped reachable Git history with grep/log commands for secret-like strings, credential names, private key markers, tokens, local absolute paths, `.psi` references, and private project references.
- [x] Record the exact grep/history commands and concise results in `implementation.md`.
- [x] Confirm that any matched credential names without values are classified separately from actual secrets.

## Slice 4: Munera/Mementum/.psi review

- [x] Review `.psi/extensions.edn` for secrets, credentials, private-only endpoints, and safe-but-confusing private references.
- [x] Review Munera task files for secrets, credentials, private-only content, and safe-but-confusing private references.
- [x] Review Mementum state/memory/knowledge files for secrets, credentials, private-only content, and safe-but-confusing private references.
- [x] Record the review result while preserving the explicit decision that Munera and Mementum remain public.

## Slice 5: Classification and trivial cleanup

- [x] Classify each finding as blocking sensitive material, safe secret-name-only reference, safe-but-confusing private reference, or trivial current-tree public-facing cleanup candidate.
- [x] Stop and document redacted remediation options if any blocking sensitive material is found.
- [x] Apply only in-scope trivial current-tree cleanup edits for clearly safe user-facing/public-release references, if any are found.
- [x] Record any safe-but-confusing references left as follow-up with a brief safe/not-secret rationale.

## Slice 6: Final audit report and verification

- [x] Record the final command/result summary, limitations, findings classification, and follow-up list in `implementation.md`.
- [x] State clearly in `implementation.md` whether the repository is safe to make public from a secrets/history perspective.
- [x] Verify `plan.md`, `steps.md`, and `implementation.md` are internally consistent with the stable `design.md` scope.
- [x] Commit the task planning files and any audit updates made during execution.

## Plan ambiguity review follow-up

- [x] Pin in `plan.md` the scope-inventory recording format, including ref names and object IDs for local branches, tags, remote-tracking refs, and `refs/stash` when present.
- [x] Pin in `plan.md` how to interpret and record gitleaks exit statuses so a completed scan with detected leaks/findings is distinguished from an operational scanner error.
- [x] Pin in `plan.md` the minimum manual grep fallback/complement command shapes and pattern groups required for current tracked files and scoped reachable history.

## Implementation review follow-up

- [x] Rerun and record the preferred scoped gitleaks audit using the project-configured mise tool (`mise exec -- gitleaks ...`) or explicitly justify why mise-provided tools are out of scope; resolve/classify the redacted findings currently reported in the audit note's scanner-pattern code blocks, then update the final recommendation against current `HEAD`.

## Test-shaper review follow-up

- [x] Add false-positive management for the three known gitleaks `private-key` audit-pattern findings (for example scanner config/baseline) or rewrite/de-trigger the recorded command examples, so the preferred scoped gitleaks verification can produce a clean, meaningful signal without relying on manual task-note context.
