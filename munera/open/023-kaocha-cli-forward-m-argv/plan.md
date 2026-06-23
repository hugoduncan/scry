# Plan: Forward `-m` Kaocha arguments to Kaocha's own parser

## Resolved open questions (planning decisions)

These adopt the design's stated leanings so implementation is unambiguous:

- **OQ1 — scry-owned `-m` Kaocha flags:** keep `--runner`/`-r`, `--help`/`-h`,
  `--result-format`, **and** `--config` and `--dir`/`-d` owned by scry. All
  other tokens (unknown `--flags`, their values, positional suite names) forward
  verbatim as `:kaocha-argv`. Keeping `--config`/`--dir` owned avoids widening
  scope; Kaocha's own `--config-file` etc. simply forward.
- **OQ2 — argv parse entry point:** the adapter parses forwarded `:kaocha-argv`
  using Kaocha's own CLI machinery already on the adapter classpath. Slice 0
  (spike) confirms the concrete function(s): investigate `kaocha.runner`'s
  `tools.cli` option spec and `kaocha.config/apply-cli-args` (already used by
  `select-suites`). If no clean reusable parse exists, the spike records the
  minimal assembly from Kaocha internals. Vendoring a `tools.cli` spec is a
  separate follow-up and out of scope unless trivial.
- **OQ3 — positional suite semantics:** route forwarded positionals back through
  the adapter's existing `select-suites` (exact-id then unique text match,
  task 002) so `-m` keeps the same fuzzy suite resolution as `-X`/REPL. Only
  non-positional forwarded tokens go through Kaocha's CLI option parse.

## Approach

Two-layer change preserving the core/adapter dependency boundary: core
`scry.cli` never requires Kaocha and forwards opaque strings; all Kaocha
knowledge stays in `src-kaocha/scry/kaocha.clj`.

### Core `scry.cli`

1. Make `-m` parsing runner-aware with a two-pass split: first scan argv to
   resolve the effective runner (`--runner`/`-r`, else default), since the flag
   may appear anywhere.
2. Core mode (`--runner clojure-test`): keep `parse-main-args` behaviour exactly
   — known flags parsed, unknown `--flags` rejected with
   `:scry.cli/argument-error`.
3. Kaocha mode: consume only scry-owned flags (OQ1) wherever they appear;
   collect every other token in original order into a `:kaocha-argv` string
   vector. scry uses arity knowledge only for its own flags.
4. Delete the `--focus` and `--kaocha-opt` branches and the
   `:suite-values`/`main-opts->exec-opts` positional-suite collapse for Kaocha
   mode. Thread `:kaocha-argv` through `normalize-exec-opts` to the adapter call.
5. Update `usage-for :kaocha` help text (drop `--focus`/`--kaocha-opt`; describe
   forwarding + positional suites).

### Adapter `scry.kaocha`

6. Accept new `:kaocha-argv` option (vector of raw CLI strings). Parse via
   Kaocha's CLI machinery into (a) parsed cli-options map and (b) positional
   suite selectors. Feed cli-options through the existing `apply-kaocha-extra`
   /`:kaocha/cli-options` merge and positionals through `select-suites`.
   Explicit `:config` stays authoritative on conflict (existing merge rule).

### `-X` map path — unchanged

7. `-X` keeps forwarding arbitrary top-level keys via `:kaocha-extra`.
   `:kaocha-argv` is `-m`-only.

### Docs

8. Update README, AGENTS.md, Unreleased CHANGELOG (supersede the just-added
   pass-through wording), and `doc/API.md` for the new documented
   `scry.kaocha/run` `:kaocha-argv` option.

## Risks

- **OQ2 uncertainty:** Kaocha may not expose a clean reusable argv parser; the
  Slice 0 spike de-risks this before code changes. If only internals are usable,
  isolate the dependency in one adapter function.
- **Suite-selection drift:** routing positionals through `select-suites`
  (OQ3) must preserve fuzzy resolution; covered by an explicit test.
- **Outcome-kind contract:** the only accepted reclassification is `-m` Kaocha
  unknown-flag (`argument-error` → `runner/load-error`). Core mode and all other
  outcome kinds must stay identical; covered by regression tests.
- **Boundary regression:** accidentally requiring Kaocha from core `scry.cli`.
  Guard with the existing load-time boundary expectation.

## Slice order

0. **Spike (OQ2):** confirm the Kaocha argv → (cli-options, positionals) parse
   entry point. Record findings in implementation.md. No production code yet.
1. **Core `-m` runner-aware split + forwarding:** resolve runner first; Kaocha
   mode collects `:kaocha-argv`; remove `--focus`/`--kaocha-opt`/suite-collapse;
   core mode unchanged. Thread `:kaocha-argv` through `normalize-exec-opts`.
2. **Adapter `:kaocha-argv` parsing:** parse forwarded argv, merge cli-options
   via `apply-kaocha-extra`, route positionals through `select-suites`, keep
   `:config` authoritative.
3. **Help text + docs:** `usage-for :kaocha`, README, AGENTS.md, CHANGELOG,
   `doc/API.md`.
4. **Tests:** focused CLI + adapter tests for forwarding, positional suites, a
   forwarded option affecting the run, focus-via-forwarding, typo
   reclassification, and core-mode-unchanged regression.
5. **Final verification:** focused CLI and Kaocha CLI/adapter command-line
   checks; record commands/results in implementation.md.
