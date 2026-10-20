#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc "$ROOT/app/src/main/java/fr/mangalens/overlay/BorderBlend.kt" "$ROOT/tests/BorderBlendTest.kt" -include-runtime -d "$TMP/test.jar"
java -jar "$TMP/test.jar"
