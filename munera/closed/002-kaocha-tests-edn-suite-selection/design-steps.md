# Design follow-up steps

- [x] Decide and document what happens when both `:suite` and `:suites` are supplied: `:suites` precedence or a clear exception.
- [x] Decide and document the exact suite-id matching rule for keywords, symbols, strings, namespaces, and duplicate `name` collisions.
- [x] Clarify whether `:config` input is assumed already normalized/complete, or whether `scry.kaocha/run` should normalize and/or merge scry defaults into it before running.
- [x] Clarify how scry-required Kaocha defaults (`:kaocha.plugin/capture-output`, quiet reporter, color false) are merged or overridden when loading `tests.edn`, especially when the project config already specifies plugins/reporters/color.
- [x] Make string suite-selector matching explicit: reconcile the documented support for string selectors with the later `(name selector)` fallback rule, since strings do not support `name`.
- [x] Decide and document `:suites` edge semantics: whether plural `:suites` must be a collection, whether scalar `:suites` is accepted or rejected, and whether an empty collection means all suites, no suites, or an API error.
- [x] Reconcile the single-suite convenience wording in `design.md`: either make `:suite` consistently required/supported everywhere now that option semantics depend on it, or remove/adjust the conflict and API-error text that assumes it exists.
- [x] Reconcile `src-kaocha/scry/kaocha.clj` with `design.md`'s `:suites` semantics: scalar/string/map values and empty collections must throw `ex-info` instead of being accepted as selectors or no selection.
