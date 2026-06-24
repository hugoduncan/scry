💡 scry's Kaocha synthetic-fallback config (`build-fallback-config`) and bare explicit `:config` maps do NOT carry Kaocha's default plugin chain. So config-driven features that depend on default plugins have no effect on those paths unless the plugin is explicitly ensured.

Concretely: `:focus` filtering needs `:kaocha.plugin/filter`, and output capture needs `:kaocha.plugin/capture-output`. `scry.kaocha/run` ensures both via `ensure-plugin` / `ensure-runtime-plugins` (generalized from the older capture-output-only helper).

Implication for future work: any new Kaocha option routed through `:kaocha/cli-options` that relies on a default Kaocha plugin must add that plugin to the ensure list, and must be tested across all three config paths — tests.edn, explicit bare `:config`, and synthetic fallback (no tests.edn) — because they differ in plugin presence. The tests.edn path tends to carry the default chain; the other two do not.

Test the behaviour (assert tests are actually filtered), not just that the cli-option key is set: a wrong coercion shape or missing plugin causes Kaocha to silently match nothing and only warn.

(task 019)
