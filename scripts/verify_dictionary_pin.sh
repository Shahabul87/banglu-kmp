#!/usr/bin/env bash
# S136 (F-009/F-016): assert a dictionary.sqlite is byte-for-byte the pinned
# asset (sha256 + size) and carries the version the engine requires.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DICT="${1:-$ROOT_DIR/android-keyboard/src/main/assets/dictionary.sqlite}"
PIN="$ROOT_DIR/android-keyboard/dictionary.sha256"
[[ -f "$DICT" ]] || { echo "ERROR: dictionary missing: $DICT" >&2; exit 1; }
read -r want_sha want_bytes want_version < <(grep -v '^#' "$PIN" | grep -v '^\s*$' | head -1)
have_sha="$(shasum -a 256 "$DICT" | cut -d' ' -f1)"
have_bytes="$(wc -c < "$DICT" | tr -d ' ')"
have_version="$(sqlite3 "$DICT" "SELECT value FROM metadata WHERE key='version';")"
required="$(grep -o 'REQUIRED = "[^"]*"' "$ROOT_DIR/shared/src/commonMain/kotlin/com/banglu/engine/DictionaryVersion.kt" | cut -d'"' -f2)"
echo "dictionary_sha256=$have_sha"
echo "dictionary_bytes=$have_bytes"
echo "dictionary_version=$have_version (engine requires $required)"
[[ "$have_sha" == "$want_sha" ]] || { echo "ERROR: dictionary sha256 $have_sha != pinned $want_sha (run scripts/pin_dictionary.sh if this rebuild is intended)" >&2; exit 1; }
[[ "$have_bytes" == "$want_bytes" ]] || { echo "ERROR: dictionary size $have_bytes != pinned $want_bytes" >&2; exit 1; }
[[ "$have_version" == "$want_version" && "$have_version" == "$required" ]] || { echo "ERROR: dictionary version $have_version; pinned $want_version; engine requires $required" >&2; exit 1; }
echo "dictionary_pin=verified"
