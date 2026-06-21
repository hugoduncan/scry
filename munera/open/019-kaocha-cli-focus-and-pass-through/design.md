# 019: Kaocha CLI --focus and pass-through options

## Goal

Allow `--focus` (and other Kaocha-specific options) to be passed through from the scry CLI to the underlying Kaocha runner, so users can leverage Kaocha's full option surface without requiring scry to explicitly enumerate every Kaocha flag.

## Context

Kaocha supports CLI options like `--focus` (filter tests by `:focus` metadata), `--focus-only`, `--exclude`, `--threads`, and others. Currently, scry's CLI only recognizes a fixed set of Kaocha options (`:suite`, `:suites`, `:config`, `:dirs`, `:source-paths`, `:test-paths`, `:ns-patterns`). Unrecognized options behave differently depending on the entry path:

- On the `-X` map path, `normalize-kaocha-options` copies only known keys, so an unknown key (e.g. `:focus`) is silently dropped during normalization and has no effect.
- On the `-m` path, `parse-main-args` rejects any unknown flag with an `argument-error` ("Unknown option: <flag>"), classified as `:scry.cli/argument-error`. So `--focus` currently *errors* rather than no-ops.

Either way `--focus` has no useful effect today, but because the two paths fail differently the pass-through design must account for both.

During normalization, `normalize-kaocha-options` only copies known keys into the normalized map, so unknown keys present in the map are not carried forward. Then `scry.kaocha/run` only uses the keys it knows about (`:config`, `:suite`, `:suites`, `:source-paths`, `:test-paths`, `:ns-patterns`, `:result-format`, `:progress-callback`).

## Approach

Forward Kaocha-specific options into the Kaocha config map. Forwarding is *bounded* so the CLI's validation boundary and `:scry.cli/outcome-kind` classification are preserved (see Constraints): the `-m` path only forwards explicitly opted-in options, while the `-X` path forwards top-level keys that fall outside scry's own key set.

**Scry-managed key set (never forwarded as pass-through).** These keys are owned by scry and must always be excluded from `:kaocha-extra`, so scry-internal and mode-selector keys can never leak into the Kaocha config:

- Mode selector: `:runner`.
- Core-only keys (the `core-only-keys` set in `scry.cli`).
- Scry-internal / shared keys: `:result-format`, `:progress-callback`.
- Known Kaocha keys already handled explicitly: `:suite`, `:suites`, `:config`, `:dirs`, `:source-paths`, `:test-paths`, `:ns-patterns`.

The plan:

1. In `normalize-kaocha-options`, collect pass-through options into `:kaocha-extra` in the normalized map, always excluding the scry-managed key set above. On the `-X` map path, the remaining top-level keys (e.g. `:focus`) become `:kaocha-extra` instead of being dropped. On the `-m` path, pass-through is opt-in (see steps 3–4); unknown `-m` flags outside that opt-in surface are still rejected.

2. In `scry.kaocha/run`, merge `:kaocha-extra` into the resolved Kaocha config before running. This means options like `:focus` end up as `:focus` in the Kaocha config, which Kaocha's `kaocha.api/run` will process. Explicit `:config` stays authoritative (see Constraints).

3. `-m` flag parsing: add explicit, parsed flags so `parse-main-args` no longer rejects pass-through options as unknown. This is either named flags (`--focus`, `--focus-only`, `--exclude`) mapped to the appropriate config keys, or a generic `--kaocha-opt KEY VALUE` mechanism for arbitrary pass-through (Open Question 1). Unknown `-m` flags outside this opt-in surface continue to be rejected with `:scry.cli/argument-error`.

4. `-m` value coercion: every `-m` flag value arrives as a raw string, unlike typed `-X` EDN values (e.g. `:focus "my.ns/test-foo"`). Pass-through values parsed on the `-m` path must be coerced to the type Kaocha expects for each option before being placed in `:kaocha-extra`, so `--focus my.ns/test-foo` reaches Kaocha as the same value that `:focus "my.ns/test-foo"` produces on `-X`. The per-option coercion is tied to how each option's key/value is interpreted (see Open Question 3 for the `:focus` key mapping).

## Constraints

- Core mode (`:clojure-test`) must not be affected.
- Pass-through must preserve the CLI's structured error-classification contract (`:scry.cli/outcome-kind`):
  - On the `-m` path, pass-through is bounded/opt-in (named flags or `--kaocha-opt`); unknown `-m` flags continue to be rejected with `:scry.cli/argument-error` rather than default-forwarded to the runner.
  - On the `-X` map path there is no unknown-key rejection, so top-level keys outside the scry-managed set are forwarded as pass-through. A mistyped `-X` key is therefore forwarded and may surface as `:scry.cli/runner-error` / `:scry.cli/load-error` rather than an argument error. This is an accepted trade-off for the `-X` path and must be documented for users.
- Explicit `:config` must remain authoritative: when the user supplies `:config`, its keys win over any pass-through option on conflict. Whether pass-through is then *rejected* (pass-through disallowed when `:config` is present) or *merged* (pass-through fills only keys absent from `:config`, with `:config` winning on conflict) is an open design choice (see Open Questions). "Authoritative" means `:config` is never overridden by pass-through; it does not by itself decide reject-vs-merge.
- Must not break existing Kaocha option handling (`:suite`, `:suites`, `:dirs`, etc.).
- Generated API docs (`doc/API.md`) must be updated if the public `scry.kaocha/run` API surface changes.

## Acceptance

- `clojure -M:test:kaocha -m scry.cli --runner kaocha --focus my.ns/test-foo` runs only the focused test.
- `clojure -X:test:kaocha scry.cli/run :runner :kaocha :focus "my.ns/test-foo"` works the same way.
- Existing Kaocha CLI options (`--suite`, `--config`, `--dirs`) continue to work.
- Core mode is unaffected.
- Focused CLI tests cover the new pass-through behavior.
- Focused Kaocha adapter tests cover `:kaocha-extra` merging.

## Open Questions

- Should `--focus` be a named CLI flag, or should we add a generic `--kaocha-opt KEY VALUE` for arbitrary pass-through? Named flags are more discoverable but require maintenance per Kaocha option. A generic mechanism is more flexible but less user-friendly.
- If `:config` is supplied alongside pass-through options, should pass-through be rejected (clean boundary) or merged with `:config` winning on conflict (convenience)? (See the `:config` constraint above, which fixes that `:config` is authoritative either way.)
- Does `:focus` map directly to a Kaocha config key, or does it need special handling through `kaocha.config/apply-cli-args`?
