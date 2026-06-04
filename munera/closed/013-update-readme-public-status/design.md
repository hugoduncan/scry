# Update README public status

## Goal

Replace the README's stale “Early scaffold” status language with accurate public-facing project maturity wording.

## Context

`README.md` currently says the project is an early scaffold even though the core runner, CLI, scoped results, nested capture isolation, build/release automation, and optional Kaocha adapter are implemented and tested. Public readers should get an honest but not misleading status statement.

## Scope

- Update the README `Status` section.
- Use an “initial public alpha / pre-1.0” maturity label: the project is usable and tested across its documented core, CLI, build/release, nested capture, and optional Kaocha surfaces, but public wording must avoid implying 1.0 stability or long-term API/result-shape freeze.
- Mention the implemented surfaces at a high level without duplicating the rest of the README.

## Out of scope

- Changing APIs or behavior.
- Adding installation docs; that is a separate task.
- Writing marketing copy beyond a clear status note.

## Acceptance criteria

- README no longer describes the project as merely an “Early scaffold.”
- Status accurately reflects the implemented/tested documented core, CLI, build/release, nested capture, and optional Kaocha surfaces at a high level.
- The wording communicates initial public alpha / pre-1.0 expectations: useful and tested, but still conservative about API/result-shape stability before a future 1.0.
