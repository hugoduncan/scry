💡 To reuse Kaocha's own CLI parser (so scry forwards raw `-m` argv instead of re-implementing a bounded flag subset), assemble the parse exactly like `kaocha.runner/-main*`:

- Base option spec is the **private** var `kaocha.runner/cli-options`, reached with `@(requiring-resolve 'kaocha.runner/cli-options)` (`requiring-resolve` resolves private vars by fully-qualified symbol).
- Plugins extend the spec via the `:kaocha.hooks/cli-options` hook: `(kaocha.plugin/run-hook* (kaocha.plugin/load-all plugins) :kaocha.hooks/cli-options base-spec)`. `:kaocha.plugin/filter` adds `--focus` (→ vector of keywords); `:kaocha.plugin/randomize` adds `--[no-]randomize`.
- `(clojure.tools.cli/parse-opts argv full-spec)` → `{:options ... :arguments ... :errors ...}`. `:arguments` are positional suite-selector strings; convert with `kaocha.runner/parse-kw` and route through the adapter's `select-suites`.
- Drop the always-present `:config-file` default from the parsed options before merging; feed the rest through the existing `apply-kaocha-extra` (config cli-options stay authoritative).
- Build the spec's plugin chain from the union of the resolved config's plugins AND Kaocha's defaults, so the standard flag surface parses even for the synthetic fallback config (no tests.edn). A flag only *takes effect* if its plugin is active at run time.

(task 023; pairs with kaocha-default-plugins-not-in-fallback-config)
