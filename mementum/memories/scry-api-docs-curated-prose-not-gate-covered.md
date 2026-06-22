💡 scry's `doc/API.md` is "generated", but its README-style prologue (CLI invocations, usage examples, runner snippets) is hardcoded curated prose in the `intro` string of `bb/scry/api_docs.clj` — NOT derived from docstrings. Quickdoc only generates the per-var API sections appended after `intro`.

Consequence: running `bb api-docs` re-emits stale curated examples unchanged, so it does not fix them. Both doc gates miss stale `intro` examples too:
- `bb api-docs --check` only asserts committed `doc/API.md` == freshly generated output (source and output share the same stale text → passes).
- `scry.api-docs-test` (`generated-api-docs-curated-surface-test`) asserts only generic boilerplate prose and the exact public var surface, not the specific CLI invocation strings.

So when a user-facing CLI/usage example changes (flag rename, `-m` syntax reshape, etc.), you MUST hand-edit the `intro` string in `bb/scry/api_docs.clj`, then regenerate. Editing only `doc/API.md` will be overwritten by regeneration; relying on the gates to catch the drift will silently fail. This took a plan-review inconsistency turn to surface.

(task 021)
