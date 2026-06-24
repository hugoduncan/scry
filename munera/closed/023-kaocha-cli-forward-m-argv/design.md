# 023: Forward `-m` Kaocha arguments to Kaocha's own parser

## Intent (why this task exists)

scry is a test runner, **not a wrapper around Kaocha**. The CLI's guiding intent
is that `scry.cli --runner kaocha` is a **drop-in replacement for Kaocha's own
command line**: invocations that work against Kaocha's CLI should work against
scry and mean the same thing. scry's value is its structured result model, not a
new, curated CLI dialect. (See mementum/knowledge/cli-runner-drop-in-compatibility.md.)

The bespoke `-m` Kaocha parsing (a bounded subset, plus scry-specific `--focus`
and `--kaocha-opt`) is a symptom of treating scry as a Kaocha *wrapper*. Those
flags exist only because scry re-implemented part of Kaocha's CLI instead of
deferring to it. Drop-in compatibility means deferring to Kaocha's own parser.

## Goal

In Kaocha mode (`--runner kaocha`), have `scry.cli` consume only the small set of
scry-owned flags and forward **all remaining `-m` arguments verbatim** to
Kaocha's own CLI parsing in the `scry.kaocha` adapter — so scry accepts Kaocha's
full CLI surface as a drop-in invocation replacement.

This removes the bespoke `--focus`, `--kaocha-opt`, and positional-suite-collapse
machinery and makes the `-m` Kaocha path behave like Kaocha's own CLI.

## Context

Today the `-m` path (`scry.cli/parse-main-args`) is a single hand-rolled `case`
loop that knows a bounded slice of Kaocha's surface:

- `--focus SYM` (repeatable) → `:kaocha-extra :focus`.
- `--kaocha-opt KEY VALUE` (generic) → `:kaocha-extra <kw key>`.
- Positional non-flag tokens → `:suite-values` → collapsed by
  `main-opts->exec-opts` to `:suite`/`:suites` (task 021).
- Any unrecognised `--flag` → `:scry.cli/argument-error` ("Unknown option").

The `-X` map path already forwards arbitrary keys: `normalize-kaocha-options`
collects every top-level key outside `scry-managed-keys` into `:kaocha-extra`,
which the adapter merges into `:kaocha/cli-options`. The adapter also resolves
`:suite`/`:suites` selectors itself (exact-id, then unique text match; task 002).

Two problems motivate this task:

1. **`--kaocha-opt` exists only because `-m` cannot fall through.** To reach any
   Kaocha option beyond `--focus` (e.g. `--focus-only`, `--exclude`, `--threads`,
   `--no-randomize`) a `-m` user must spell it as `--kaocha-opt KEY VALUE` or
   drop to `-X`/`--config`. That is an awkward, scry-specific shape.

2. **A naive "forward unknown flags" fix is incorrect.** scry's loop cannot
   decide whether the token after an unknown `--flag` is its value or a
   positional suite, because Kaocha options have mixed arity (`--focus X` takes a
   value; `--no-randomize` takes none). There is no arity-agnostic rule. Only
   Kaocha knows its own option arities (its `tools.cli` spec).

The clean resolution is therefore **not** to teach scry more Kaocha options, but
to forward the raw remaining argv to Kaocha and let Kaocha parse it.

Maintainer decisions already taken (this conversation):

- Accept that `-m` Kaocha typos now surface as `:scry.cli/runner-error` /
  `:scry.cli/load-error` instead of `:scry.cli/argument-error`. The clean
  argument-error classification for unknown `-m` flags is given up **in Kaocha
  mode only**.
- The project is pre-1.0 (`0.1.x`); a breaking change to the `-m` Kaocha surface
  is acceptable, consistent with task 021.

## Approach

### Core `scry.cli` (must not require Kaocha at load time)

1. Make `-m` parsing runner-aware via a small two-pass split, because `--runner`
   may appear anywhere in argv and the parser cannot know the runner mid-loop:
   - First resolve the runner from argv.
   - In **core** mode (`--runner clojure-test`), keep today's behaviour exactly:
     parse the known core flags and **reject** unknown flags with
     `:scry.cli/argument-error`. Nothing changes for core mode.
   - In **Kaocha** mode, consume only the scry-owned flags (see step 2) wherever
     they appear, and collect every other token — unknown `--flags`, their
     values, and positional suite names — **in original order** into a raw
     `:kaocha-argv` string vector to forward. scry needs arity knowledge only for
     its *own* flags (which it has); it never interprets Kaocha tokens.

2. Decide the fixed set of scry-owned `-m` Kaocha flags (see Open Question 1).
   Proposed: `--runner`, `--help`, `--result-format`. Everything else forwards.

3. Delete the `--focus` and `--kaocha-opt` flag branches and the
   `:suite-values`/`main-opts->exec-opts` positional-suite collapse for Kaocha
   mode. Pass `:kaocha-argv` through `normalize-exec-opts` to the adapter call.

### Adapter `scry.kaocha` (owns all Kaocha knowledge)

4. Accept a new `:kaocha-argv` option: a vector of raw CLI strings. Parse it with
   Kaocha's own CLI machinery (see Open Question 2) into (a) a parsed
   cli-options map and (b) positional suite selectors, then feed those through
   the existing resolution paths (`:kaocha/cli-options` merge via
   `apply-kaocha-extra`; suite selection via `apply-cli-args`/`select-suites`).
   Reuse `coerce-*`/merge logic already present.

### `-X` map path — unchanged

5. The `-X` path keeps forwarding arbitrary top-level keys via `:kaocha-extra`.
   `:kaocha-argv` is `-m`-only. `:kaocha-extra` remains the `-X` container.

### Surface and docs

6. Update `usage-for :kaocha` help text (drop `--focus`/`--kaocha-opt`; describe
   forwarding + positional suites). Update README, AGENTS.md, and the Unreleased
   CHANGELOG entry (this supersedes the just-added pass-through wording).

## Scope

In scope:

- Reshape `-m` Kaocha argument handling to forward raw argv.
- Adapter parsing of forwarded argv via Kaocha's own CLI parser.
- Removal of `--focus`, `--kaocha-opt`, and `-m` positional-suite collapse.
- `--help` Kaocha usage text, README/AGENTS/CHANGELOG, focused CLI + adapter
  tests.

Out of scope / explicitly unchanged:

- Core (`--runner clojure-test`) mode: unknown flags still rejected.
- The `-X` map path and its `:kaocha-extra` pass-through.
- The programmatic `scry.kaocha/run` `:suite`/`:suites`/`:config` API used from
  the REPL and `-X`.
- scry's result model, scope formatting, outcome classification, exit codes, and
  `.scry-results/` writing.

Adjacent (note, do not do here):

- If Open Question 2 shows Kaocha lacks a clean reusable argv parser, a follow-up
  may be needed to vendor a minimal `tools.cli` spec; keep that out of this task
  unless trivial.
- **Default-runner cognitect compatibility.** The same drop-in intent says the
  default `--runner clojure-test` CLI should be a drop-in replacement for the
  cognitect test-runner CLI. scry's core flags already mirror it (`-d/--dir`,
  `-n/--namespace`, `-v/--var`, `--namespace-regex`), but include/exclude
  selector metadata (`-i/--include` / `-e/--exclude`) is unsupported. Closing
  that gap is a **separate task**, not part of 023.

## Constraints

- Core `scry.cli` must not require Kaocha at namespace load time; argv parsing
  lives in `src-kaocha`. Core forwards `:kaocha-argv` as opaque strings.
- Preserve the `:scry.cli/outcome-kind` contract for everything except the
  accepted `-m` Kaocha unknown-flag reclassification (argument-error →
  runner/load-error).
- Explicit `:config` must remain authoritative over forwarded options, matching
  the current merge rule.
- Keep `doc/API.md` current if the public `scry.kaocha/run` option surface
  changes (a new documented `:kaocha-argv` option).

## Acceptance

- `clojure -M:test:kaocha -m scry.cli --runner kaocha --focus my.ns/test-foo`
  runs only the focused test (now via forwarding, not a named flag).
- A previously-unsupported Kaocha option reaches Kaocha on `-m`, e.g.
  `--runner kaocha --no-randomize` (or another boolean/valued option chosen
  during planning) demonstrably affects the run.
- Positional suite selectors still select suites on `-m`
  (`--runner kaocha unit [integration ...]`).
- `--focus` and `--kaocha-opt` are gone; the Kaocha `--help` no longer lists
  them.
- Core mode is unaffected: unknown core flags still produce
  `:scry.cli/argument-error`.
- A malformed `-m` Kaocha option surfaces as `:scry.cli/runner-error` /
  `:scry.cli/load-error` (the accepted trade-off), not an argument error.
- The `-X` path and programmatic adapter API behave exactly as before.
- Focused CLI and Kaocha adapter tests cover the new forwarding, including
  positional suites, a forwarded option, and the typo reclassification.

## Open Questions

1. **Which flags stay scry-owned on `-m` Kaocha mode?** `--runner`, `--help`,
   `--result-format` are scry concerns (not Kaocha's) and stay owned. Do
   `--config` (scry EDN map) and `--dir` (→ `:test-paths` fallback) stay owned,
   or are they dropped in favour of Kaocha's own `--config-file PATH`? Leaning:
   keep `--config`/`--dir` owned for now to avoid widening scope.

   **Decided — `--namespace`/`--var`/`--ns-pattern` (core-only selectors):**
   keep them scry-owned and **rejected with a clean `:scry.cli/argument-error`**
   in Kaocha mode, exactly as today (`normalize-kaocha-options` →
   `reject-keys core-only-keys`). They are not Kaocha concepts and must not be
   forwarded: the Kaocha equivalent of "run this namespace/var" is Kaocha's own
   `--focus my.ns` / `--focus my.ns/test-foo`, reached by plain forwarding. This
   keeps their clean classification even though *unrecognised* `-m` tokens now
   reclassify to runner/load errors — a deliberate scry validation is not an
   "unknown flag".
2. **What is the reusable Kaocha argv→(cli-options, suites) entry point?**
   Investigate `kaocha.runner`'s `tools.cli` spec and `kaocha.config/apply-cli-args`
   (already used by `select-suites` for positional ids). Determine whether a
   public/usable function exists or whether the adapter must assemble the parse
   from Kaocha internals.
3. **Does forwarding positionals to Kaocha change suite-selection semantics?**
   Today the adapter does fuzzy selector resolution (exact-id then unique text;
   task 002). If `-m` positionals go through Kaocha's own suite matching instead,
   that fuzzy behaviour no longer applies on `-m` (it remains on `-X`/REPL).
   Confirm this is acceptable, or route forwarded positionals back through
   `select-suites` to preserve it.

## Alternatives Considered

- **Keep `--kaocha-opt`, add more named flags.** Rejected: per-option
  maintenance, still no parity with Kaocha's surface, keeps two mechanisms.
- **Naive scry-side "forward unknown flags".** Rejected: arity ambiguity makes it
  incorrect for boolean Kaocha flags adjacent to positional suites.
- **Remove `--kaocha-opt` only, leaving `-m` Kaocha at `--focus` plus suites.**
  Rejected by the maintainer: narrows `-m` rather than unifying it.
