# Design follow-up steps

- [x] Resolve the "Open Questions" interleaving ambiguity into a definitive
      decision and fold it into the Approach/Constraints. Confirm the proposed
      default (accept any non-flag token as a positional suite selector
      regardless of position relative to flags), and state explicitly the
      parser's positional-vs-token discrimination rule: tokens starting with `-`
      that are not recognized flags remain "Unknown option" argument errors,
      while non-`-` tokens are collected as ordered positional suite selectors.
      Remove the resolved item from "Open Questions" so the implementer has one
      unambiguous rule.
- [x] When resolving the interleaving decision above, also reconcile the word
      "trailing" in Approach step 2 ("Collect trailing non-flag tokens"), which
      currently contradicts the proposed position-agnostic default ("accept any
      non-flag token as a positional regardless of position") in Open Questions.
- [x] Correct the Constraints claim that bare positionals "were previously
      unreachable in core mode." In the current code, `parse-main-args`'s
      `default` branch rejects any unrecognized token (including bare tokens) as
      "Unknown option" at parse time, before runner mode is resolved in
      `normalize-exec-opts`; bare positionals were reachable and rejected, not
      unreachable. The conclusion ("must remain an error") is correct; only the
      stated rationale needs fixing.
- [x] Reconcile the first Constraints bullet's leading clause ("Core mode
      behavior must not change except that stray positional arguments are now an
      argument error") with its own corrected rationale and the Acceptance line.
      As written it frames positionals-as-argument-error as a new delta, but the
      same bullet then states such positionals were already rejected as an
      "Unknown option" argument error, so the `:scry.cli/argument-error`
      outcome-kind is unchanged. Restate the actual delta accurately: core-mode
      positionals remain `:scry.cli/argument-error`; what changes is only the
      rejection mechanism/message (parse-time "Unknown option" → normalize-time
      "Kaocha options require :runner :kaocha" via the existing
      `kaocha-only-keys`/`reject-keys` path, since positionals now collapse to
      `:suite`/`:suites`), not the error-ness or the outcome-kind contract.

## Plan-review follow-up (inconsistency review)

- [x] Docs-sync slice mechanism-vs-goal inconsistency: the `doc/API.md`
      Main-style example `--runner kaocha --suite unit` is curated prose
      hardcoded in `bb/scry/api_docs.clj` (the `intro` string), not regenerated
      from a runtime docstring. Plan step 6 and steps Slice 3 say only
      "regenerate `doc/API.md` (`bb api-docs`)", which re-emits the stale
      `--suite` example unchanged, so the stated goal (API example reflects the
      positional form) is unreachable by regeneration alone — and the gates do
      not catch it (`bb api-docs --check` only compares the committed file to
      regeneration; `scry.api-docs-test` does not pin this example). Update
      plan.md step 6 / the docs slice to require editing the curated example in
      `bb/scry/api_docs.clj` to the positional form before regenerating
      `doc/API.md`. (Out-of-scope note for maintainer: `SKILL.md:159` also
      carries the stale `--suite unit` example but lies outside the design's
      README/AGENTS/API doc scope — flag, do not silently expand scope.)
- [x] Plan/steps `--config`-test inconsistency: plan.md step 5 lists `--config`
      among tests to "Keep ... unchanged", but the only `--config` parse test is
      the combined `cli_test.clj:232` "accepted Kaocha suites and config EDN
      flags" case that also exercises the now-removed `--suites` flag (and which
      steps.md Slice 2 correctly directs to edit: "Keep the `--suites`/`--config`
      EDN test only for `--config`; replace the `--suites` portion"). steps.md
      pointedly omits `--config` from its own "unchanged" list. Reconcile plan.md
      step 5 so it does not claim the `--config` test is unchanged: state that
      `--config` coverage is preserved but the fused `--suites`/`--config` test
      must be split/edited to drop the removed `--suites` flag (matching steps.md
      Slice 2). Leaving the literal `--suites "[:unit]"` token in place would,
      after flag removal, hit the `-`-prefixed "Unknown option" branch.
- [ ] design.md↔plan/steps `--config`-test residual inconsistency: after the
      plan step 5 follow-up (617b3ee) reconciled plan↔steps, design.md Approach
      step 6 (lines 65-66) still says "Keep `--focus`, `--kaocha-opt`, and
      `--config` pass-through tests **as-is**", which now contradicts plan.md
      step 5 ("the `--config` test is **not left unchanged** ... split/edited to
      drop the `--suites` portion") and steps.md Slice 2. The only `--config`
      parse test is the fused `cli_test.clj` "accepted Kaocha suites and config
      EDN flags" case, so a literal reading of design.md step 6 would preserve
      the broken `--suites "[:unit]"` assertion (the same failure mode the
      plan-side review flagged). Reconcile design.md Approach step 6: keep
      `--focus`/`--kaocha-opt` tests as-is, but state that `--config` *coverage*
      is preserved while its fused `--suites`/`--config` test is split/edited to
      drop the removed `--suites` portion (matching plan step 5 / steps Slice 2).
      Wording-only correction; do NOT touch the design.md Constraints (line 91)
      or Acceptance (line 104) lines, which correctly speak of `--config`
      *behavior* (unchanged), and do not alter Interpretation A scope.
