💡 scry's Kaocha synthetic-fallback config and bare explicit `:config` maps do not carry Kaocha's default plugin chain. Config/CLI options that depend on plugins therefore have no effect unless the runtime plugin is explicitly activated.

Examples: focus needs `:kaocha.plugin/filter`; output capture needs `:kaocha.plugin/capture-output`. A forwarded `--plugin` / `:kaocha-extra :plugin` selection must be coerced and added to `:kaocha/plugins`, not merely stored under `:kaocha/cli-options`. Activate selected plugins before `ensure-runtime-plugins` normalizes the chain so user hooks execute and scry's completion observer can remain last.

For future adapter options, distinguish parser support from runtime activation. Test behavior across tests.edn, bare explicit config, and synthetic fallback—not just option-map shape—because these paths differ in plugin presence and can silently ignore otherwise valid configuration.

(tasks 019, 026)
