#!/usr/bin/env bash
# Deterministic, offline gate: both modules must publish under the public Maven
# coordinate `io.github.mibrahimdev` with a POM complete enough for Maven
# Central to accept it.
#
# Central rejects deployments whose POM is missing name / description / url /
# licenses / developers / scm. Rather than trust that the build is configured,
# this actually publishes to the local Maven repo and greps the *generated*
# .pom files for each required element (and a value that proves it's really
# filled, not an empty tag).
#
# Signing is intentionally excluded: there is no GPG key locally and real
# signing is provisioned in a separate issue. `-PlocalPublishNoSign` turns off
# signAllPublications() for THIS run only — the signing config stays active in
# the build by default (and on CI).
set -euo pipefail
cd "$(dirname "$0")/.."

GROUP_PATH="io/github/mibrahimdev"
VERSION="0.1.0"
M2="${HOME}/.m2/repository/${GROUP_PATH}"

# Deterministic: wipe any prior local publication of these coordinates so a
# stale POM from an earlier run can never make this pass.
rm -rf "${M2}/sharingan" "${M2}/sharingan-noop"

echo "==> Publishing both modules to mavenLocal (signing excluded)…"
./gradlew --no-configuration-cache -PlocalPublishNoSign \
  :sharingan:publishToMavenLocal :sharingan-noop:publishToMavenLocal

# Each entry is "tag|needle": the <tag> element must exist, and if needle is
# non-empty that substring must appear somewhere in the POM (proving the
# element is actually populated with the agreed value).
COMMON_CHECKS=(
  "name|"
  "url|https://mibrahimdev.github.io/Sharingan/"
  "license|Apache-2.0"
  "license|https://www.apache.org/licenses/LICENSE-2.0.txt"
  "developer|<id>mibrahimdev</id>"
  "developer|Mohamed Ibrahim"
  "developer|mibrahim.dev@gmail.com"
  "scm|github.com/mibrahimdev/Sharingan"
)

fail=0

check_pom() {
  local pom="$1"
  local desc_needle="$2"
  echo "--- $pom"

  # <description> is module-specific, so it's checked with its own needle.
  if ! grep -q "<description>" "$pom"; then
    echo "  MISSING <description> element"; fail=1
  elif ! grep -qF "$desc_needle" "$pom"; then
    echo "  <description> present but missing expected text: $desc_needle"; fail=1
  fi

  local entry tag needle
  for entry in "${COMMON_CHECKS[@]}"; do
    tag="${entry%%|*}"
    needle="${entry#*|}"
    if ! grep -q "<${tag}>" "$pom"; then
      echo "  MISSING <${tag}> element"; fail=1; continue
    fi
    if [ -n "$needle" ] && ! grep -qF "$needle" "$pom"; then
      echo "  <${tag}> present but missing expected value: $needle"; fail=1
    fi
  done
}

# The module-root POMs are the ones a consumer resolves and Central validates.
CORE_POM="${M2}/sharingan/${VERSION}/sharingan-${VERSION}.pom"
NOOP_POM="${M2}/sharingan-noop/${VERSION}/sharingan-noop-${VERSION}.pom"

if [ ! -f "$CORE_POM" ]; then
  echo "MISSING POM: $CORE_POM (\:sharingan did not publish under io.github.mibrahimdev)"; fail=1
else
  check_pom "$CORE_POM" "On-device debug logger and HTTP/MQTT/BLE inspector"
fi

if [ ! -f "$NOOP_POM" ]; then
  echo "MISSING POM: $NOOP_POM (\:sharingan-noop did not publish under io.github.mibrahimdev)"; fail=1
else
  check_pom "$NOOP_POM" "Inert release replacement for Sharingan"
fi

if [ "$fail" -ne 0 ]; then
  echo "FAIL: POM(s) missing Central-required metadata"
  exit 1
fi
echo "PASS: both module POMs carry name/description/url/license/developer/scm under io.github.mibrahimdev"
