#!/bin/sh
# S47: pulls the engine build artifacts from banglu-kmp into vendor/.
# Run after any engine change: ./gradlew :shared:jsBrowserProductionLibraryDistribution
set -e
cd "$(dirname "$0")"
ROOT=".."
cp "$ROOT"/shared/build/dist/js/productionLibrary/*.js vendor/
cp "$ROOT"/shared/build/dist/js/productionLibrary/*.d.ts vendor/ 2>/dev/null || true
cp "$ROOT"/shared/banglu-slim.json vendor/banglu-slim.json
npx --yes esbuild vendor/banglu-engine.js --bundle --format=esm --minify --outfile=vendor/banglu-engine.bundle.js
echo "vendor refreshed: $(ls vendor | wc -l | tr -d ' ') files"
