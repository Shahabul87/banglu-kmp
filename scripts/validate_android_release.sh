#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT_DIR/android-keyboard/build/outputs/apk/release/android-keyboard-release.apk"
AAB="$ROOT_DIR/android-keyboard/build/outputs/bundle/release/android-keyboard-release.aab"
MERGED_RELEASE_MANIFEST="$ROOT_DIR/android-keyboard/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
PRIVACY_POLICY="$ROOT_DIR/design/play-store/PRIVACY-POLICY.md"
MAX_RELEASE_APK_BYTES="${MAX_RELEASE_APK_BYTES:-83886080}"  # 80MiB: 143MB dictionary era (S44)

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || fail "Missing required file: $path"
}

echo "== Banglu Android release validation =="
echo "root=$ROOT_DIR"
echo

echo "== Build and test =="
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" \
  :android-keyboard:verifyImePrivacyBoundary \
  :shared:allTests \
  :android-keyboard:assembleRelease \
  :android-keyboard:bundleRelease
echo

echo "== Release artifacts =="
require_file "$APK"
require_file "$AAB"
apk_bytes="$(wc -c < "$APK" | tr -d ' ')"
aab_bytes="$(wc -c < "$AAB" | tr -d ' ')"
echo "release_apk_bytes=$apk_bytes"
echo "release_aab_bytes=$aab_bytes"
if (( apk_bytes > MAX_RELEASE_APK_BYTES )); then
  fail "Release APK is too large: $apk_bytes bytes > $MAX_RELEASE_APK_BYTES bytes"
fi
echo

echo "== Dynamic feature checks =="
aab_listing="$(unzip -l "$AAB")"
grep -q 'android_account/dex/classes.dex' <<< "$aab_listing" ||
  fail "Release AAB must contain android_account dex split"
grep -q 'android_account/manifest/AndroidManifest.xml' <<< "$aab_listing" ||
  fail "Release AAB must contain android_account manifest split"
grep -q 'base/dex/classes.dex' <<< "$aab_listing" ||
  fail "Release AAB must contain base dex"
echo "account_dynamic_feature=present"
echo

echo "== Manifest/privacy checks =="
require_file "$MERGED_RELEASE_MANIFEST"
require_file "$PRIVACY_POLICY"

grep -q 'android:allowBackup="false"' "$MERGED_RELEASE_MANIFEST" ||
  fail "Merged release manifest must keep android:allowBackup=false"

if grep -q 'androidx.startup.InitializationProvider' "$MERGED_RELEASE_MANIFEST"; then
  fail "AndroidX Startup provider must not be registered in release manifest"
fi

# S44 launch posture: the public launch build must ship with ZERO network
# capability (account/billing feature disabled). Flip this check back to
# "required" only when the account feature ships.
if grep -q 'android.permission.INTERNET' "$MERGED_RELEASE_MANIFEST"; then
  fail "INTERNET permission present — launch posture requires a network-free release manifest"
fi
grep -q 'android.permission.RECORD_AUDIO' "$MERGED_RELEASE_MANIFEST" ||
  fail "RECORD_AUDIO permission missing from release manifest"
grep -qi 'internet' "$PRIVACY_POLICY" ||
  fail "Privacy policy must disclose internet/backend behavior"
grep -qi 'record_audio\|microphone\|voice' "$PRIVACY_POLICY" ||
  fail "Privacy policy must disclose voice/audio behavior"
grep -qi 'offline' "$PRIVACY_POLICY" ||
  fail "Privacy policy must describe offline keyboard behavior"
echo "manifest_privacy_checks=passed"
echo

echo "== Optional device smoke =="
if [[ "${RUN_DEVICE_SMOKE:-0}" == "1" ]]; then
  if ! command -v adb >/dev/null 2>&1; then
    fail "RUN_DEVICE_SMOKE=1 requires adb"
  fi
  if ! adb get-state >/dev/null 2>&1; then
    fail "RUN_DEVICE_SMOKE=1 requires a connected Android device"
  fi
  "$ROOT_DIR/scripts/benchmark_android_keyboard.sh" "$ROOT_DIR/build/android-release-smoke"
else
  echo "skipped; set RUN_DEVICE_SMOKE=1 to install and collect a real-device IME report"
fi

echo
echo "Release validation passed."
