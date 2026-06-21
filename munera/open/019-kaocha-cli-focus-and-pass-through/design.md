# 019: Kaocha CLI --focus and pass-through options

## Goal

Allow `--focus` (and other Kaocha-specific options) to be passed through from the scry CLI to the underlying Kaocha runner, so users can leverage Kaocha's full option surface without requiring scry to explicitly enumerate every Kaocha flag.

## Context

Kaocha supports CLI options like `--focus` (filter tests by `:focus` metadata), `--focus-only`, `--exclude`, `--threads`, and others. Currently, scry's CLI only recognizes a fixed set of Kaocha options (`:suite`, `:suites`, `:config`, `:dirs`, `:source-paths`, `:test-paths`, `:ns-patterns`). Any other option is silently dropped during normalization, so `--focus` has no effect.

The current `normalize-kaocha-options` function only copies known keys into the normalized map. Unknown keys are lost. Then `scry.kaocha/run` only uses the keys it knows about (`:config`, `:suite`, `:suites`, `:source-paths`, `:test-paths`, `:ns-patterns`, `:result-format`, `:progress-callback`).

## Approach

Pass unrecognized options through to the Kaocha runner by forwarding them into the Kaocha config map. The plan:

1. In `normalize-kaocha-options`, collect all keys from the raw opts that are not in the known scry-managed set (core-only keys, known kaocha keys, and shared keys like `:result-format`). These become `:kaocha-extra` in the normalized map.

2. In `scry.kaocha/run`, merge `:kaocha-extra` into the resolved Kaocha config before running. This means unknown options like `:focus` end up as `:focus` in the Kaocha config, which Kaocha's `api/run` will process.

3. Add `-m` CLI flag parsing for `--focus` (and potentially `--focus-only`, `--exclude`) as convenience, mapping them to the appropriate config keys. Alternatively, add a generic `--kaocha-opt KEY VALUE` mechanism for arbitrary pass-through.

## Constraints

- Core mode (`:clojure-test`) must not be affected.
- The `:config` explicit config path must still take full precedence — if the user supplies `:config`, pass-through options should either be rejected or merged (decide which).
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
- If `:config` is supplied alongside pass-through options, should they be rejected (clean boundary) or merged (convenience)?
- Does `:focus` map directly to a Kaocha config key, or does it need special handling through `config/apply-cli-args`?
