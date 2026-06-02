# AGENTS.md

Guidance for AI agents working in this repository.

## Project purpose

`scry` is an in-process Clojure test runner for AI agents and REPL-driven development. Its main value is returning structured, inspectable test results instead of requiring agents to scrape terminal output.

The public API is centered on `scry.core/run`, which runs tests and returns:

```clojure
{:summary ...
 :pass? ...
 :failures ...}
```

## Orientation

At the start of a session, read:

1. `mementum/state.md` — current project memory.
2. `munera/plan.md` — open task ordering, if any.
3. This file.
4. `README.md` for user-facing behavior.

If working a Munera task, keep task notes in that task's `implementation.md` and update `steps.md` as work progresses.

## Common commands

Run the current test suite through scry:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"
```

Inspect the raw result map:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (clojure.pprint/pprint (scry/run))"
```

Run the Kaocha adapter:

```sh
clojure -M:test:kaocha -e "(require '[scry.kaocha :as k]) (clojure.pprint/pprint (k/run))"
```

Start nREPL:

```sh
clojure -M:nrepl
```

## Development practices

- Prefer small, focused changes.
- Keep public result shapes stable unless a task explicitly changes them.
- Add or update tests for behavior changes.
- Use `scry` itself to inspect test failures when possible.
- Avoid parsing human-oriented terminal output when a structured result is available.
- Keep README examples synchronized with the actual API.

## Architecture notes

Important namespaces:

- `scry.core` — public entry point and convenience inspection helpers.
- `scry.capture` — low-level capture state, `clojure.test/report` hook, output routing, and result construction.
- `scry.clojure-test` — in-process `clojure.test` runner.
- `scry.kaocha` — optional Kaocha adapter.

Dependency boundary:

- `scry.core` must not require Kaocha.
- Kaocha support belongs under `src-kaocha/` and is available only with the `:kaocha` alias.

## Testing expectations

For changes to the `clojure.test` runner or capture machinery, verify at least:

- Passing runs return `:pass? true` and no failures.
- Failing assertions include expected/actual/message/file/line/testing contexts.
- Errors include stack traces.
- stdout/stderr are captured per failing test var.
- `:once` and `:each` fixtures still behave as normal `clojure.test` fixtures.

For changes to the Kaocha adapter, verify it still returns the same top-level result shape as `scry.core/run`.

## Documentation expectations

Update `README.md` when changing:

- Public API names.
- Result map shape.
- Supported runner options.
- Development/test commands.

Update this file when changing agent workflow, repo conventions, or important architectural constraints.
