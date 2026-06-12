#!/usr/bin/env bash
# Debug and noop must be drop-in interchangeable for Swift consumers:
# identical headers and module maps, differing only in implementation.
#
# Allowlisted differences (exit 0 despite diff):
#   KDoc comment bodies differ between debug and noop — debug carries full
#   API docs; noop carries short "No-op X logger." stubs. These are cosmetic
#   and do not affect the Swift API surface or binary compatibility.
set -euo pipefail
cd "$(dirname "$0")/.."

REAL_FW="sharingan/build/XCFrameworks/release/Sharingan.xcframework/ios-arm64/Sharingan.framework"
NOOP_FW="sharingan-noop/build/XCFrameworks/release/Sharingan.xcframework/ios-arm64/Sharingan.framework"

fail=0

# --- module.modulemap: must be byte-for-byte identical ---
f="Modules/module.modulemap"
if diff -u "$REAL_FW/$f" "$NOOP_FW/$f"; then
  echo "OK: $f identical"
else
  echo "MISMATCH: $f differs (see diff above)"
  fail=1
fi

# --- Headers/Sharingan.h: declarations must be identical; doc comments may differ ---
# Strip ObjC/C comment blocks (/* ... */) and blank lines before comparing so
# that KDoc-string differences (debug=full, noop=short) are ignored, but any
# difference in @interface, method signatures, or __attribute__ annotations
# is still caught.
strip_comments() {
  # Remove /* ... */ comment blocks (multi-line) then blank lines
  perl -0777 -pe 's|/\*.*?\*/||gs' "$1" | grep -v '^[[:space:]]*$'
}

h="Headers/Sharingan.h"
if diff -u <(strip_comments "$REAL_FW/$h") <(strip_comments "$NOOP_FW/$h"); then
  echo "OK: $h declarations identical (comment differences are allowlisted)"
else
  echo "MISMATCH: $h declarations differ (see diff above)"
  fail=1
fi

exit $fail
