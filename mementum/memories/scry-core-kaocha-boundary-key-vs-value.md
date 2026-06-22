🔁 Refinement of the core↛Kaocha load-time boundary (AGENTS.md): the dividing line is *key routing* vs *value-type coercion*.

Core `scry.cli` may keep Kaocha *key* awareness — it already routes keys (e.g. `:dirs`→`:test-paths`) and collects raw forwarded data into `:kaocha-extra`. That level of key knowledge in core is acceptable and does not require loading Kaocha.

Per-option *value-type* coercion is Kaocha-domain knowledge and must live in `scry.kaocha/run` (src-kaocha), under the optional `:kaocha` alias. Example: `-m` flag values arrive as raw strings while `-X` values arrive as typed EDN; `scry.kaocha/run` coerces `:focus` (string/symbol/keyword, scalar-or-collection → vector of keywords) so both paths reach Kaocha identically. Core forwards the raw values untouched.

This distinction took a design-review architecture turn to articulate and is the right test when deciding where new Kaocha-related logic belongs: does it need to know a Kaocha key name (core OK) or how to type/interpret a Kaocha value (src-kaocha)?

(task 019)
