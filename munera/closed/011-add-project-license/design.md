# Add project license

## Goal

Add the Eclipse Public License before the repository is made public.

## Context

The repository currently has no `LICENSE` file. Public consumers and Clojars users need clear legal terms for use, copying, modification, and redistribution. The license choice also feeds later public artifact metadata work.

## Scope

- Use Eclipse Public License 2.0 (EPL-2.0) as the project license.
- Add the top-level `LICENSE` file with the unmodified complete Eclipse Public License 2.0 text. Use the SPDX License List EPL-2.0 text as the canonical source (`https://spdx.org/licenses/EPL-2.0.txt`), preserving the full license body under the `Eclipse Public License - v 2.0` heading and without adding project-specific copyright, author, or SPDX header lines to `LICENSE`.
- Add a concise license note to `README.md` only, linking to or naming the top-level `LICENSE`. Do not add standalone license notes to `SKILL.md` or `CHANGELOG.md` in this task; `CHANGELOG.md` does not need a historical entry for adding legal boilerplate, and `SKILL.md` is agent-oriented usage guidance rather than the canonical public license notice.
- Keep the license choice available for the later POM metadata task by recording a handoff note in this task's `implementation.md` that references `014-add-public-pom-metadata` and states the license id/name to carry into generated POM metadata. Do not edit the POM task in this design follow-up.

## Out of scope

- Publishing artifacts.
- Adding generated Maven/Clojars POM metadata, including license metadata. Task `014-add-public-pom-metadata` owns generated POM license/project metadata; this task only records the handoff note in `implementation.md`.
- Rewriting Git history.

## Acceptance criteria

- The repository contains a top-level `LICENSE` file.
- The license text is complete and matches the canonical SPDX EPL-2.0 full text, with no project-specific header/footer additions.
- `README.md` identifies the project license and links to `LICENSE`; no other docs are required to receive license notes in this task.
- `implementation.md` records the POM metadata handoff to `014-add-public-pom-metadata`, including license id `EPL-2.0` and name `Eclipse Public License 2.0`, if the POM task has not yet run.
