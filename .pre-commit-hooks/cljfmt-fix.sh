#!/usr/bin/env bash
# Run cljfmt fix on staged Clojure files and restage any that changed.
set -euo pipefail

if [[ $# -eq 0 ]]; then
  exit 0
fi

changed=()

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1"
  else
    shasum -a 256 "$1"
  fi
}

for file in "$@"; do
  before=$(checksum "$file")
  mise exec -- cljfmt fix "$file"
  after=$(checksum "$file")
  if [[ "$before" != "$after" ]]; then
    changed+=("$file")
  fi
done

if [[ ${#changed[@]} -gt 0 ]]; then
  # Suppress advisory hint for gitignored paths (e.g. .clj-kondo/imports).
  git add -- "${changed[@]}" 2>/dev/null || true
  echo "cljfmt reformatted and restaged: ${changed[*]}"
  # Exit 1 so pre-commit reports the hook modified files,
  # consistent with pre-commit convention (user sees what changed).
  exit 1
fi

exit 0
