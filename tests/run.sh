#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 or newer}"
TEMP_TEST="$(mktemp -d)"
trap 'rm -rf -- "$TEMP_TEST"' EXIT
"$JAVA_HOME/bin/javac" --release 8 -d "$TEMP_TEST" \
    "$ROOT/src/local/audi/widgetprobe/AudiFields.java" "$ROOT/tests/AudiFieldsTest.java"
"$JAVA_HOME/bin/java" -cp "$TEMP_TEST" local.audi.widgetprobe.AudiFieldsTest "$ROOT/tests/widget-text.txt"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s "$ROOT/tests" -p 'test_*.py' -v
