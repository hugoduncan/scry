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
