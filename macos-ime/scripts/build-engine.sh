#!/bin/sh
# S51: builds the JS engine bundle the IME hosts in JavaScriptCore.
# Same pipeline as browser-extension/build.sh, but IIFE for JSC (no modules).
set -e
cd "$(dirname "$0")/../.."
./gradlew :shared:jsBrowserProductionLibraryDistribution
mkdir -p macos-ime/Resources/built
npx --yes esbuild@0.28.2 shared/build/dist/js/productionLibrary/banglu-engine.js \
  --bundle --format=iife --global-name=BangluNS --minify \
  --outfile=macos-ime/Resources/built/banglu-engine.bundle.js
# S108: same stale-slim gate as browser-extension/build.sh — the engine
# rejects a mismatched slim at attach time; this catches it at build time.
SLIM_V=$(head -c 100 shared/banglu-slim.json | sed -n 's/.*"version":"\([^"]*\)".*/\1/p')
REQ_V=$(sed -n 's/.*REQUIRED = "\([^"]*\)".*/\1/p' shared/src/commonMain/kotlin/com/banglu/engine/DictionaryVersion.kt)
if [ -z "$REQ_V" ] || [ "$SLIM_V" != "$REQ_V" ]; then
  echo "ERROR: banglu-slim.json version '$SLIM_V' != engine required '$REQ_V' — regenerate:" >&2
  echo "  ./gradlew :dictionary-compiler:run --args=\"slim <abs>/dictionary.sqlite <abs>/shared/banglu-slim.json\"" >&2
  exit 1
fi
cp shared/banglu-slim.json macos-ime/Resources/built/banglu-slim.json
echo "engine bundle: $(du -h macos-ime/Resources/built/banglu-engine.bundle.js | cut -f1)"
echo "slim dict:     $(du -h macos-ime/Resources/built/banglu-slim.json | cut -f1)"
