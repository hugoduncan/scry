# Steps

## Slice 1: Canonical license file

- [x] Retrieve the canonical SPDX License List EPL-2.0 full text from `https://spdx.org/licenses/EPL-2.0.txt` or, if direct retrieval is unavailable, the only allowed SPDX-controlled fallback at `https://raw.githubusercontent.com/spdx/license-list-data/main/text/EPL-2.0.txt` from `spdx/license-list-data`.
- [x] Clarify the allowed fallback/provenance and exact comparison procedure for the EPL-2.0 text if direct SPDX retrieval is unavailable, so verification still proves the committed `LICENSE` matches the SPDX full text exactly.
- [x] Align the Slice 1 retrieval checklist wording with `plan.md` by naming the direct SPDX URL and the only allowed SPDX `license-list-data` fallback, rather than the broader “another trusted SPDX source” wording.
- [x] Add top-level `LICENSE` with the complete unmodified EPL-2.0 text, preserving the `Eclipse Public License - v 2.0` heading and adding no project-specific header/footer lines.
- [x] Compare `LICENSE` against the canonical SPDX EPL-2.0 text and confirm it matches exactly.

## Slice 2: README license note

- [x] Add a concise license note or section to `README.md` naming Eclipse Public License 2.0 / `EPL-2.0` and linking to `LICENSE`.
- [x] Confirm this task did not add standalone license notes to `SKILL.md` or `CHANGELOG.md`.

## Slice 3: Verification and notes

- [x] Confirm no generated Maven/Clojars POM metadata or build metadata was changed for license information in this task.
- [x] Record implementation decisions, license source, POM metadata handoff status, and verification results in `implementation.md`.
- [x] Run `git diff --check` and inspect the final diff for only the intended `LICENSE`, `README.md`, and Munera task-note changes.
