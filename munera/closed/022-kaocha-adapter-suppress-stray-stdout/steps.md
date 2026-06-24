# Steps: 022

- [x] Reproduce: confirm `scry.kaocha/run` leaks `"\nRandomized with --seed N"`
      to `*out*` on a failing tests.edn run (StringWriter capture).
- [x] Add `discarding-writer` helper in `scry.kaocha`.
- [x] Bind `*out*`/`*err*` to discarding sinks around `api/run`.
- [x] Surface the seed: read `:kaocha.plugin.randomize/seed` from the result and
      add it to `:summary :seed` (when randomize active).
- [x] CLI: add `write-seed!`; print the seed after the summary, gated to
      `failure-outcome-kinds`.
- [x] Update `scry.kaocha/run` docstring; regenerate `doc/API.md`; note seed in
      README result-shape section.
- [x] Verify end-to-end via CLI: failing run prints seed on its own trailing
      line; passing run omits it; per-test `:out` capture intact.
- [x] Tests: adapter no-leak + seed (failing tests.edn), seed on passing run,
      CLI seed present-on-fail / absent-on-pass.
- [x] `bb clj-fmt` + `bb clj-kondo:lint` clean; `bb api-docs --check` + content
      regression green.
- [x] Focused checks: `scry.kaocha-test`, `scry.cli-kaocha-test`,
      `scry.cli-test`, `scry.capture-test`, `scry.clojure-test-test` all green.
