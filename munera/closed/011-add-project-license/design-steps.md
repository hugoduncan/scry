# Design follow-up steps

- [x] Decide exactly which public-facing docs receive the license note: README only, or README plus any other public docs such as `SKILL.md` or `CHANGELOG.md`, so implementation does not over- or under-update documentation.
- [x] Specify where the POM-metadata follow-up is recorded before closing this task (for example, a note in this task's `implementation.md` referencing `014-add-public-pom-metadata`, or an edit to that task), so acceptance can be verified without adding POM metadata now.
- [x] Pin the canonical EPL-2.0 license text source and expected top-level `LICENSE` contents (for example SPDX/OSI/Eclipse full text, with no project-specific copyright/header additions) to make “complete and matches EPL-2.0” mechanically verifiable.
- [x] Align the out-of-scope POM metadata wording with the rest of `design.md` by making generated POM license metadata fully deferred to `014-add-public-pom-metadata`, with task 011 only recording the handoff note.
