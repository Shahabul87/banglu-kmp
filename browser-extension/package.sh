#!/bin/sh
# S47 P3: builds store-ready zips. Chrome: banglu-chrome.zip. Firefox:
# banglu-firefox.zip (event-page background + gecko id).
set -e
cd "$(dirname "$0")"
./build.sh
rm -f banglu-chrome.zip banglu-firefox.zip
zip -qr banglu-chrome.zip manifest.json background.js content.js \
  popup.html popup.css popup.js options.html options.js icons \
  vendor/banglu-engine.bundle.js vendor/banglu-slim.json
# Firefox variant: swap manifest
python3 - <<'PY'
import json
m=json.load(open("manifest.json"))
m["browser_specific_settings"]={"gecko":{"id":"banglu@banglu.app","strict_min_version":"121.0"}}
m["background"]={"scripts":["background.js"],"type":"module"}
json.dump(m,open("manifest.firefox.json","w"),ensure_ascii=False,indent=2)
PY
mkdir -p .ff && cp -r background.js content.js popup.html popup.css popup.js \
  options.html options.js icons .ff/ && mkdir -p .ff/vendor \
  && cp vendor/banglu-engine.bundle.js vendor/banglu-slim.json .ff/vendor/ \
  && cp manifest.firefox.json .ff/manifest.json
(cd .ff && zip -qr ../banglu-firefox.zip .)
rm -rf .ff manifest.firefox.json
ls -la banglu-*.zip
