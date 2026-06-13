#!/usr/bin/env bash
# Test harness for check-version-tag.sh.
#
# The guard asserts that a pushed git tag (e.g. v0.1.0) matches the project
# version declared in gradle/libs.versions.toml (sharingan = "0.1.0"). This
# test drives BOTH inputs the acceptance criteria require:
#   - a matching tag           -> guard exits 0
#   - a drifted/mislabeled tag -> guard exits non-zero
#
# No bats dependency: plain bash with explicit exit-code assertions.
set -uo pipefail
cd "$(dirname "$0")/.."

GUARD="scripts/check-version-tag.sh"

# Read the catalog version the guard is expected to compare against, so the
# test stays correct across version bumps without editing literals here.
CATALOG_VERSION="$(grep -E '^sharingan[[:space:]]*=' gradle/libs.versions.toml | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
MATCHING_TAG="v${CATALOG_VERSION}"
DRIFTED_TAG="v9.9.9-does-not-match"

fail=0

# --- Case 1: matching tag -> exit 0 ---
if "$GUARD" "$MATCHING_TAG" >/dev/null 2>&1; then
  echo "PASS: matching tag '$MATCHING_TAG' -> exit 0"
else
  echo "FAIL: matching tag '$MATCHING_TAG' should exit 0 but exited $?"
  fail=1
fi

# --- Case 2: drifted tag -> non-zero ---
if "$GUARD" "$DRIFTED_TAG" >/dev/null 2>&1; then
  echo "FAIL: drifted tag '$DRIFTED_TAG' should exit non-zero but exited 0"
  fail=1
else
  echo "PASS: drifted tag '$DRIFTED_TAG' -> non-zero exit"
fi

if [ "$fail" -eq 0 ]; then
  echo "ALL PASS"
else
  echo "TESTS FAILED"
fi
exit $fail
