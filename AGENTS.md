# AGENTS.md

Guidance for AI agents working in this repository.

## Project purpose

`scry` is an in-process Clojure test runner for AI agents and REPL-driven development. Its main value is returning structured, inspectable test results instead of requiring agents to scrape terminal output.

The public API is centered on `scry.core/run`, which runs tests and returns scoped structured results:

```clojure
{:summary ...
 :pass? ...
 :results ...   ;; canonical formatted collection
 :failures ...} ;; compatibility failing/erroring subset when included
```

## Orientation

At the start of a session, read:

1. `mementum/state.md` — current project memory.
2. `munera/plan.md` — open task ordering, if any.
3. This file.
4. `README.md` for user-facing behavior.

If working a Munera task, keep task notes in that task's `implementation.md` and update `steps.md` as work progresses.

## Preferred test workflow: project REPL + scry

Prefer running tests through a live project REPL with `scry`, not through one-off command-line test invocations. The REPL workflow keeps code loaded, preserves `scry.core/last-result` for follow-up inspection, and returns structured data without scraping terminal output.

In a project REPL, run the current test suite:

```clojure
(require '[scry.core :as scry])

(scry/run)
(println (scry/report-string (scry/last-result)))
```

Inspect the raw result map:

```clojure
(scry/last-result)
(:summary (scry/last-result))
(:results (scry/last-result))
(scry/failures)
```

Run targeted tests from the REPL while iterating:

```clojure
(scry/run {:namespaces ['my.project-test]})
(scry/run {:vars [#'my.project-test/specific-test]})
```

Run the Kaocha adapter from the REPL when needed:

```clojure
(require '[scry.kaocha :as k])

(k/run)
```

Start nREPL if no project REPL is available:

```sh
clojure -M:nrepl
```

Use command-line `clojure -M:test ...` forms only as a fallback when a REPL is unavailable or unsuitable:

```sh
clojure -M:test -e "(require '[scry.core :as scry]) (println (scry/report-string (scry/run)))"
clojure -M:test -e "(require '[scry.core :as scry]) (clojure.pprint/pprint (scry/run))"
```

## Result inspection guidance

Default result detail depends on invocation scope:

- Broad/discovered runs, multiple namespaces, and multiple vars use suite scope: compact failing/erroring entries only, no assertion detail or output.
- Exactly one explicit namespace uses namespace scope: all executed vars, all assertions including passes, no output keys.
- Exactly one explicit executable var uses var scope: one entry, all assertions, and captured `:out`/`:err`.

Use `:results` as the canonical result collection. Use `scry/failures` or `:failures` when you only need failing/erroring entries. Helpers tolerate custom result formats but cannot inspect collections omitted by `:top-level-keys`.

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
- `scry.capture` — low-level capture state, `clojure.test/report` hook, output routing, result construction, and result formatting.
- `scry.clojure-test` — in-process `clojure.test` runner and invocation scope classification.
- `scry.kaocha` — optional Kaocha adapter.

Dependency boundary:

- `scry.core` must not require Kaocha.
- Kaocha support belongs under `src-kaocha/` and is available only with the `:kaocha` alias.

## Testing expectations

For changes to the `clojure.test` runner or capture machinery, verify at least:

- Passing runs return `:pass? true` and no failures.
- Suite-scope broad runs are compact and omit output.
- Single namespace runs include passing vars and passing assertion detail.
- Single var runs include assertion detail and captured stdout/stderr.
- Failing assertions include expected/actual/message/file/line/testing contexts.
- Errors include stack traces.
- `:once` and `:each` fixtures still behave as normal `clojure.test` fixtures.

For changes to the Kaocha adapter, verify it still returns the same scoped result model as `scry.core/run`; note that Kaocha currently defaults to suite scope and merges stdout/stderr.

## Documentation expectations

Update `README.md` when changing user-facing behavior:

- Public API names.
- Result map shape.
- Supported runner options.

Keep development instructions, test workflow guidance, agent workflow, repo conventions, and architectural constraints in `AGENTS.md`, not `README.md`.

Update this file when changing agent workflow, repo conventions, important architectural constraints, or development/test commands.
