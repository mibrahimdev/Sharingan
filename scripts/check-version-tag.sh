#!/usr/bin/env bash
# Release guard: assert the pushed git tag matches the project version.
#
# The version source of truth is gradle/libs.versions.toml (key `sharingan`);
# a release is `v<version>`. CI pushes tag v0.1.0 -> this must equal the
# catalog version, or we'd publish mislabeled artifacts to Maven Central.
#
# The tag is read from $1 (for tests) and falls back to $GITHUB_REF_NAME
# (the tag name GitHub Actions exposes on a tag push). The catalog is parsed
# directly with a regex — no gradle invocation — so the guard is fast and
# runs offline in the checkout step before any JVM is set up.
#
# Exit 0 when tag == version (leading `v` stripped); non-zero on any drift.
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="${1:-${GITHUB_REF_NAME:-}}"
if [ -z "$TAG" ]; then
  echo "ERROR: no tag provided (pass as \$1 or set GITHUB_REF_NAME)" >&2
  exit 2
fi

# Strip a single leading 'v': v0.1.0 -> 0.1.0
TAG_VERSION="${TAG#v}"

CATALOG="gradle/libs.versions.toml"
CATALOG_VERSION="$(grep -E '^sharingan[[:space:]]*=' "$CATALOG" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
if [ -z "$CATALOG_VERSION" ]; then
  echo "ERROR: could not parse 'sharingan' version from $CATALOG" >&2
  exit 2
fi

if [ "$TAG_VERSION" = "$CATALOG_VERSION" ]; then
  echo "OK: tag '$TAG' matches project version '$CATALOG_VERSION'"
  exit 0
fi

echo "MISMATCH: tag '$TAG' (version '$TAG_VERSION') != project version '$CATALOG_VERSION'" >&2
echo "Bump the 'sharingan' key in $CATALOG or retag to v$CATALOG_VERSION." >&2
exit 1
