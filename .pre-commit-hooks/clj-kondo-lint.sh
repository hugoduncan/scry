#!/usr/bin/env bash
# Run the project clj-kondo lint task for staged Clojure changes.
set -euo pipefail

bb clj-kondo:lint
