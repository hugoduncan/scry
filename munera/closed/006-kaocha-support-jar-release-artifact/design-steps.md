# Design follow-up steps

- [x] Decide and document whether the existing `clojure -T:build:deploy deploy` command changes to deploy both artifacts or remains core-only with a separate clearly named deploy-all/release deploy task.
- [x] Pin the adapter pom's `lambdaisland/kaocha` dependency version source (for example, reuse the current `deps.edn` `:kaocha` alias version or declare a separate explicit build constant) so published metadata cannot drift ambiguously.
- [x] Specify whether core and adapter jar builds must use separate class/output directories or otherwise retain both generated pom files safely for the combined build/deploy path.
