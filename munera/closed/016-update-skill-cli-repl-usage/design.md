# Update SKILL.md for CLI vs REPL usage

## Goal

Update the top-level `SKILL.md` so agents using the scry skill understand the difference between REPL/in-process usage and command-line usage.

## Context

`SKILL.md` currently documents `scry.core/run`, targeted REPL runs, result maps, and the Kaocha REPL adapter. It does not clearly document the command-line workflow added later: `scry.cli`, live progress output, `.scry-results/` failure files, exit-code behavior, or the intended guidance that REPL usage is best while iterating and command-line checks are best for final verification/CI-style confidence.

`AGENTS.md` and `README.md` already contain the authoritative project guidance for these workflows. The skill should remain concise and portable, but it needs enough CLI-specific information that an agent copying or using the skill will not assume all scry usage is REPL-only.

## Scope

- Update top-level `SKILL.md` only.
- Add a clear distinction between:
  - REPL/in-process API usage through `scry.core/run`, `scry/last-result`, `scry/failures`, and targeted result inspection.
  - Command-line usage through `scry.cli` for process exit behavior, progress output, `.scry-results/` files, and CI/final-verification style checks.
- Add copyable core CLI examples for `-m` and `-X` entry points.
- Add copyable optional Kaocha CLI examples using the optional `:kaocha` classpath/alias.
- Document the CLI result/output contract at skill level:
  - `.` for passing vars on stdout.
  - failing/erroring var names on stderr.
  - summary output.
  - `.scry-results/` is cleared/recreated at run start and contains EDN files for failing/erroring vars.
  - non-zero exit for failures, errors, unknown status, runner/argument errors, or zero executable tests.
- Preserve existing structured-result and targeted REPL guidance.

## Out of scope

- Changing scry runtime behavior.
- Changing `README.md` or `AGENTS.md`, unless a clear contradiction is discovered while implementing.
- Adding or changing tests for runtime behavior.
- Changing public result shapes or CLI contracts.
- Documenting every CLI flag exhaustively; the skill should point agents at the main workflows and contracts.

## Acceptance criteria

- `SKILL.md` has separate, explicit sections or bullets for REPL workflow and command-line workflow.
- The REPL section continues to tell agents to inspect structured maps directly and prefer targeted namespace/var runs while iterating.
- The command-line section includes correct `clojure -M:test -m scry.cli` and `clojure -X:test scry.cli/run ...` examples.
- The command-line section explains CLI progress/output/result-file behavior and exit-code semantics.
- Optional Kaocha CLI usage is documented without implying Kaocha is required for core users.
- The skill-level guidance says to use REPL/in-process runs for interactive debugging and command-line runs for final verification/CI-style checks.
- The updated skill remains concise enough to be useful as an agent skill and does not duplicate the full README.
