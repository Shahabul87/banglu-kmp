#!/usr/bin/env bash
# S136 (F-009): record the current Android dictionary asset as THE approved
# release dictionary (sha256 + size + version). Run after a compiler rebuild,
# review the diff, commit it with the dictionary bump.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DICT="$ROOT_DIR/android-keyboard/src/main/assets/dictionary.sqlite"
PIN="$ROOT_DIR/android-keyboard/dictionary.sha256"
sha="$(shasum -a 256 "$DICT" | cut -d' ' -f1)"
bytes="$(wc -c < "$DICT" | tr -d ' ')"
version="$(sqlite3 "$DICT" "SELECT value FROM metadata WHERE key='version';")"
{
  echo "# S136 (F-009): the ONLY dictionary.sqlite a release may be built from."
  echo "# Regenerate with scripts/pin_dictionary.sh after every compiler rebuild."
  echo "# sha256  bytes  version"
  echo "$sha  $bytes  $version"
} > "$PIN"
cat "$PIN"
