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

echo "== Source provenance preflight =="
# S135 (F-002, production audit): the staged 1.5.81 AAB embedded a revision
# that was NOT the tagged commit — it was built while later changes were
# still uncommitted. A release artifact must come from a clean, committed
# tree so the embedded version-control-info matches the tag. Untracked
# files are fine (the tree carries WIP screenshots); tracked edits are not.
# Set ALLOW_DIRTY_TREE=1 only for local dry runs — never for an upload.
if [[ "${ALLOW_DIRTY_TREE:-0}" != "1" ]] && ! git -C "$ROOT_DIR" diff --quiet HEAD --; then
  git -C "$ROOT_DIR" status --short | grep -v '^??' >&2 || true
  fail "Tracked files have uncommitted changes — commit first (or ALLOW_DIRTY_TREE=1 for a dry run)"
fi
HEAD_REVISION="$(git -C "$ROOT_DIR" rev-parse HEAD)"
echo "head_revision=$HEAD_REVISION"

# S135 (F-007): the upload key and the file holding its passwords must be
# readable by the owner only. Values are never printed.
for secret_file in "$ROOT_DIR/android-keyboard/banglu-release.jks" "$ROOT_DIR/local.properties"; do
  if [[ -f "$secret_file" ]]; then
    mode="$(stat -f '%Lp' "$secret_file" 2>/dev/null || stat -c '%a' "$secret_file")"
    [[ "$mode" == "600" || "$mode" == "400" ]] ||
      fail "$(basename "$secret_file") is mode $mode — run: chmod 600 '$secret_file'"
  fi
done
echo "signing_file_modes=owner-only"
echo

echo "== Build and test =="
# S128 (production audit): the validator passed while lintRelease failed —
# a false-green release gate. Android lint and the Android unit suites
# (debug AND release variants) are now mandatory gate members.
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" \
  :android-keyboard:verifyImePrivacyBoundary \
  :shared:allTests \
  :android-keyboard:lintRelease \
  :android-keyboard:testDebugUnitTest \
  :android-keyboard:testReleaseUnitTest \
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
# S135 (F-002): the AAB must name the commit we are standing on.
embedded_revision="$(unzip -p "$AAB" base/root/META-INF/version-control-info.textproto 2>/dev/null \
  | sed -n 's/.*revision: "\([0-9a-f]*\)".*/\1/p' | head -1)"
echo "aab_embedded_revision=${embedded_revision:-<missing>}"
[[ "$embedded_revision" == "$HEAD_REVISION" ]] ||
  fail "AAB embeds revision '${embedded_revision:-<missing>}' but HEAD is '$HEAD_REVISION'"
echo "release_aab_sha256=$(shasum -a 256 "$AAB" | cut -d' ' -f1)"
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
# S135 (F-008): the policy must describe what the launch build actually
# does — no network permission, clipboard retention, saved email addresses.
grep -qi 'does not request the INTERNET permission' "$PRIVACY_POLICY" ||
  fail "Privacy policy must state the Android app does not request the INTERNET permission"
grep -qi 'clipboard' "$PRIVACY_POLICY" && grep -qi 'one hour' "$PRIVACY_POLICY" ||
  fail "Privacy policy must disclose clipboard history and its one-hour retention"
grep -qi 'email address' "$PRIVACY_POLICY" ||
  fail "Privacy policy must disclose saved email addresses (identity assist)"
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
