# Plan

## Approach

- Use the stable design's maintainer-selected license: Eclipse Public License 2.0, SPDX id `EPL-2.0`.
- Add a top-level `LICENSE` file containing the complete unmodified SPDX License List EPL-2.0 full text from `https://spdx.org/licenses/EPL-2.0.txt`, preserving the `Eclipse Public License - v 2.0` heading and adding no project-specific header, copyright, footer, or SPDX line.
- Add only a concise README license note that names the license and links to `LICENSE`; do not add separate license notes to `SKILL.md` or `CHANGELOG.md` for this task.
- Preserve the POM metadata boundary: generated Maven/Clojars POM license metadata remains deferred to `014-add-public-pom-metadata`; this task's `implementation.md` already records the required handoff with license id/name.
- Verify by comparing the committed `LICENSE` text to the SPDX source, checking README references `LICENSE`, and confirming no generated POM/build metadata was changed.

## Risks

- The SPDX license text must be copied exactly; accidental whitespace/header/footer changes would fail acceptance.
- Documentation scope is intentionally narrow; adding license notes to other docs or POM metadata would create scope creep and overlap task `014-add-public-pom-metadata`.
- Online SPDX retrieval may be unavailable during implementation. The only allowed fallback is another SPDX-controlled source for the same license text: `https://raw.githubusercontent.com/spdx/license-list-data/main/text/EPL-2.0.txt` from the SPDX `license-list-data` repository, preferably pinned to a specific commit or release tag if direct `spdx.org` retrieval is unavailable. Do not use non-SPDX mirrors or reformatted license text.
- Verification must compare files exactly, not semantically. Save the retrieved SPDX text to a temporary file, record its source URL/ref and SHA-256 in `implementation.md`, and use a byte-for-byte comparison such as `cmp -s /tmp/EPL-2.0.txt LICENSE` (plus `git diff --no-index -- /tmp/EPL-2.0.txt LICENSE` if it fails). Do not normalize whitespace, line endings, headings, or final newlines to make the comparison pass. If the GitHub SPDX fallback is used and the direct SPDX URL later becomes reachable during the task, compare the fallback text to the direct SPDX text with the same exact comparison before treating it as canonical.

## Slice order

1. **Canonical license file** — obtain the SPDX EPL-2.0 full text and add it verbatim as top-level `LICENSE`.
2. **README license note** — add a concise user-facing license section or note in `README.md` linking to `LICENSE`.
3. **Verification and notes** — verify license text/reference/scope, record commands and outcomes in `implementation.md`, and leave POM metadata changes for task `014-add-public-pom-metadata`.
