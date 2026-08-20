#!/bin/sh
# S47: pulls the engine build artifacts from banglu-kmp into vendor/.
# Run after any engine change: ./gradlew :shared:jsBrowserProductionLibraryDistribution
set -e
cd "$(dirname "$0")"
ROOT=".."
cp "$ROOT"/shared/build/dist/js/productionLibrary/*.js vendor/
cp "$ROOT"/shared/build/dist/js/productionLibrary/*.d.ts vendor/ 2>/dev/null || true
# S108: refuse to bundle a stale slim — the shipped zips once carried 3.8.10
# while Android shipped 3.9.2. The engine also rejects a mismatch at attach
# time; this catches it at build time.
SLIM_V=$(head -c 100 "$ROOT"/shared/banglu-slim.json | sed -n 's/.*"version":"\([^"]*\)".*/\1/p')
REQ_V=$(sed -n 's/.*REQUIRED = "\([^"]*\)".*/\1/p' "$ROOT"/shared/src/commonMain/kotlin/com/banglu/engine/DictionaryVersion.kt)
if [ -z "$REQ_V" ] || [ "$SLIM_V" != "$REQ_V" ]; then
  echo "ERROR: banglu-slim.json version '$SLIM_V' != engine required '$REQ_V' — regenerate:" >&2
  echo "  ./gradlew :dictionary-compiler:run --args=\"slim <abs>/dictionary.sqlite <abs>/shared/banglu-slim.json\"" >&2
  exit 1
fi
cp "$ROOT"/shared/banglu-slim.json vendor/banglu-slim.json
npx --yes esbuild@0.28.2 vendor/banglu-engine.js --bundle --format=esm --minify --outfile=vendor/banglu-engine.bundle.js
echo "vendor refreshed: $(ls vendor | wc -l | tr -d ' ') files"
