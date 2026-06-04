# Design follow-up steps

- [x] Clarify what a nil/disabled capture context does to `clojure.test/report` events and stdout/stderr writes: ignore them, delegate to an underlying/default reporter or writer, route them to orphan buffers, or use a different rule for `without-context`.
- [x] Specify output ownership for `:once` and `:each` fixture output under per-var `*out*`/`*err*` rebinding: whether it belongs to the surrounding test var, orphan run output, omitted output, or another explicit location.
- [x] Decide whether intended-var allow-listing is required in this task; if it is, define how non-allow-listed report events/output are handled, and if it is not, make raw nested `clojure.test` leakage a documented limitation.
- [x] Clarify the thread boundary for dynamic capture context and routing writers, including whether same-thread cooperative nested runners are the only guarantee or whether child threads/futures/parallel runner events need explicit propagation or documented limitations.
- [x] Update the Planning questions in `design.md` so intended-var allow-listing is no longer presented as an undecided optional/follow-up item; it should match the required allow-list/ignored-frame behavior already specified.
