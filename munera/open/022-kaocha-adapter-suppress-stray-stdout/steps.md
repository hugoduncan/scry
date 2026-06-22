# Steps: 022

- [x] Reproduce: confirm `scry.kaocha/run` leaks `"\nRandomized with --seed N"`
      to `*out*` on a failing run (StringWriter capture).
- [x] Add `discarding-writer` helper in `scry.kaocha`.
- [x] Bind `*out*`/`*err*` to discarding sinks around `api/run`.
- [x] Verify no leak end-to-end via the CLI (`-m scry.cli --runner kaocha`);
      summary stands alone, per-test `:out` capture intact.
- [x] Add adapter regression test
      `does-not-leak-framework-stdout-on-failing-run-test`.
- [x] `bb clj-fmt:fix` + `bb clj-kondo:lint` clean.
- [x] Focused checks: `scry.kaocha-test`, `scry.cli-kaocha-test`,
      `scry.cli-test` all green.
