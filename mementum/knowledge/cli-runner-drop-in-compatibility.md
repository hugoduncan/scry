---
title: scry CLI runner drop-in compatibility intent
status: active
category: design-intent
tags: [cli, kaocha, cognitect-test-runner, runners, compatibility]
related: [scry-cli-bounded-asymmetric-pass-through, scry-core-kaocha-boundary-key-vs-value]
---

💡 **scry is a test runner, not a wrapper.** The CLI's guiding intent is that
each `--runner` makes `scry.cli` a **drop-in CLI replacement** for an existing
runner's command line, so users can switch to scry without changing how they
invoke tests. scry's added value is the structured, inspectable result model —
not a new CLI dialect.

Two concrete compatibility targets:

- **`--runner kaocha` ⇒ drop-in for Kaocha's CLI.** Invocations that work against
  Kaocha's own command line should work against `scry.cli --runner kaocha` and
  mean the same thing (options, suite selection, focus, etc.). scry is *not* a
  curated subset wrapper around Kaocha; it should accept Kaocha's CLI surface.
  This is the real reason to forward Kaocha arguments to Kaocha's own parser
  rather than re-implement a bounded subset in scry (task 023).

- **Default runner (`--runner clojure-test`) ⇒ drop-in for the
  cognitect test-runner CLI.** scry's core selector flags already mirror
  `cognitect.test-runner` deliberately: `-d/--dir`, `-n/--namespace`, `-v/--var`,
  and `--namespace-regex` (aliased `--ns-pattern`). The intent is that someone
  using `clojure -X:test cognitect.test-runner.api/test` style selection can
  point at scry instead. (Known gap vs full cognitect parity: include/exclude
  selector metadata `-i/--include` / `-e/--exclude` are not yet supported.)

**Implications**

- Prefer matching the upstream runner's CLI contract over inventing scry-specific
  flags. scry-specific flags (e.g. an old `--kaocha-opt` escape hatch, or a named
  `--focus`) are smells: they exist only because scry was parsing a bounded
  subset instead of delegating to the real runner's parser.
- Compatibility is about **CLI argument semantics**, not output format. scry
  intentionally returns structured results and writes `.scry-results/`; it does
  not need to reproduce a runner's human output to be a drop-in *invocation*
  replacement.
- The core↛Kaocha load boundary still holds: delegating to Kaocha's parser means
  forwarding raw argv into the `scry.kaocha` adapter (src-kaocha), never
  requiring Kaocha from core `scry.cli`.
- scry-owned flags that are *not* runner concerns (`--runner`, `--help`,
  `--result-format`) and core-only selectors rejected in the wrong mode stay
  scry-validated; "compatibility" applies to the target runner's own option
  surface.
