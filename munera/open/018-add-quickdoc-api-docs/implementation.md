# Implementation notes

Task created from user request to add `borkdude/quickdoc` based API docs. No implementation yet.

## 2026-06-04 architecture review

Reviewed `design.md` against AGENTS.md/README.md architecture guidance; `META.md` and `doc/architecture.md` are absent. The docs direction fits the public API/docs workflow and optional Kaocha boundary, but one architectural follow-up is needed: keep quickdoc/tooling dependencies out of top-level runtime deps and published artifact dependency surfaces.
