#!/bin/sh
# S51: builds the JS engine bundle the IME hosts in JavaScriptCore.
# Same pipeline as browser-extension/build.sh, but IIFE for JSC (no modules).
set -e
cd "$(dirname "$0")/../.."
./gradlew :shared:jsBrowserProductionLibraryDistribution
mkdir -p macos-ime/Resources/built
npx --yes esbuild shared/build/dist/js/productionLibrary/banglu-engine.js \
  --bundle --format=iife --global-name=BangluNS --minify \
  --outfile=macos-ime/Resources/built/banglu-engine.bundle.js
cp shared/banglu-slim.json macos-ime/Resources/built/banglu-slim.json
echo "engine bundle: $(du -h macos-ime/Resources/built/banglu-engine.bundle.js | cut -f1)"
echo "slim dict:     $(du -h macos-ime/Resources/built/banglu-slim.json | cut -f1)"
