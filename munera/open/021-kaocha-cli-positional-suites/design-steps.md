# Design follow-up steps

- [ ] Resolve the "Open Questions" interleaving ambiguity into a definitive
      decision and fold it into the Approach/Constraints. Confirm the proposed
      default (accept any non-flag token as a positional suite selector
      regardless of position relative to flags), and state explicitly the
      parser's positional-vs-token discrimination rule: tokens starting with `-`
      that are not recognized flags remain "Unknown option" argument errors,
      while non-`-` tokens are collected as ordered positional suite selectors.
      Remove the resolved item from "Open Questions" so the implementer has one
      unambiguous rule.
