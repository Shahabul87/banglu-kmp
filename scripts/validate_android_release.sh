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

# S135/S136 (F-007): the upload key ACTUALLY used by the build (BANGLU_STORE_FILE
# in local.properties — the re-audit caught the S135 check inspecting the repo
# copy instead) and the file holding its passwords must be owner-only. Values
# are never printed; only the key NAME is read from local.properties.
LOCAL_PROPS="$ROOT_DIR/local.properties"
store_file="$(grep -E '^BANGLU_STORE_FILE=' "$LOCAL_PROPS" 2>/dev/null | head -1 | cut -d= -f2- | sed "s#^~#$HOME#")"
[[ -n "$store_file" ]] || store_file="$ROOT_DIR/android-keyboard/banglu-release.jks"
[[ "$store_file" = /* ]] || store_file="$ROOT_DIR/android-keyboard/$store_file"
[[ -f "$store_file" ]] || fail "Release keystore not found at the configured BANGLU_STORE_FILE path"
for secret_file in "$store_file" "$LOCAL_PROPS" "$ROOT_DIR/android-keyboard/banglu-release.jks"; do
  if [[ -f "$secret_file" ]]; then
    mode="$(stat -f '%Lp' "$secret_file" 2>/dev/null || stat -c '%a' "$secret_file")"
    [[ "$mode" == "600" || "$mode" == "400" ]] ||
      fail "$(basename "$secret_file") is mode $mode — run: chmod 600 '$secret_file'"
  fi
done
echo "signing_keystore=$(basename "$store_file") (configured path, owner-only)"
echo "signing_file_modes=owner-only"

# S136 (F-002): version ↔ tag agreement. The tag is created after this gate
# passes, so an untagged HEAD is fine; a tag that disagrees is not.
VERSION_NAME="$(grep -E 'versionName = "' "$ROOT_DIR/android-keyboard/build.gradle.kts" | head -1 | sed 's/.*"\(.*\)".*/\1/')"
VERSION_CODE="$(grep -E 'versionCode = ' "$ROOT_DIR/android-keyboard/build.gradle.kts" | head -1 | sed 's/[^0-9]//g')"
echo "version=$VERSION_NAME ($VERSION_CODE)"
head_tag="$(git -C "$ROOT_DIR" tag --points-at HEAD | grep -E '^v[0-9]' | head -1 || true)"
if [[ -n "$head_tag" ]]; then
  [[ "$head_tag" == "v$VERSION_NAME" ]] || fail "HEAD is tagged $head_tag but versionName is $VERSION_NAME"
  echo "head_tag=$head_tag (matches versionName)"
else
  echo "head_tag=<none yet> — tag v$VERSION_NAME after this gate passes"
fi
# S138 (F-002): the version's tag may exist ONLY at the build commit — a second
# 1.5.x build from a different commit would otherwise share the version.
existing_tag_commit="$(git -C "$ROOT_DIR" rev-list -n 1 "v$VERSION_NAME" 2>/dev/null || true)"
if [[ -n "$existing_tag_commit" && "$existing_tag_commit" != "$HEAD_REVISION" ]]; then
  [[ "${ALLOW_RETAG:-0}" == "1" ]] ||
    fail "tag v$VERSION_NAME already exists at ${existing_tag_commit:0:7} (HEAD is ${HEAD_REVISION:0:7}) — bump versionName/versionCode (ALLOW_RETAG=1 only to move an UNRELEASED tag)"
  echo "existing_tag=v$VERSION_NAME at ${existing_tag_commit:0:7} — ALLOW_RETAG=1, will be moved"
fi

# S136 (F-009): only the pinned dictionary may be packaged.
"$ROOT_DIR/scripts/verify_dictionary_pin.sh" "$ROOT_DIR/android-keyboard/src/main/assets/dictionary.sqlite"
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
aab_sha256="$(shasum -a 256 "$AAB" | cut -d' ' -f1)"
echo "release_aab_sha256=$aab_sha256"
# S138 (F-019): the artifact must be signed by the certificate in the
# configured keystore — the repo copy android-keyboard/banglu-release.jks is
# NOT the release key (the re-audit caught the backup instructions pointing
# at it). Passwords travel via the environment, never argv or logs.
BUILD_TOOLS_DIR="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
[[ -x "$BUILD_TOOLS_DIR/apksigner" ]] || fail "apksigner not found under build-tools — cannot verify the signing certificate"
artifact_cert="$("$BUILD_TOOLS_DIR/apksigner" verify --print-certs "$APK" | grep -m1 'SHA-256 digest' | awk '{print $NF}' | tr 'A-F' 'a-f')"
export BANGLU_KEYTOOL_PASS="$(grep -E '^BANGLU_STORE_PASSWORD=' "$LOCAL_PROPS" | head -1 | cut -d= -f2-)"
keystore_cert="$(keytool -list -v -keystore "$store_file" -alias banglu -storepass:env BANGLU_KEYTOOL_PASS 2>/dev/null | grep -m1 'SHA256:' | awk '{print $2}' | tr -d ':' | tr 'A-F' 'a-f')"
unset BANGLU_KEYTOOL_PASS
[[ -n "$artifact_cert" && -n "$keystore_cert" ]] || fail "could not read the signing certificate from the artifact/keystore"
[[ "$artifact_cert" == "$keystore_cert" ]] ||
  fail "artifact certificate $artifact_cert != configured keystore certificate $keystore_cert"
echo "signing_cert_sha256=$artifact_cert (artifact == configured keystore)"
echo

echo "== Dynamic feature checks =="
aab_listing="$(unzip -l "$AAB")"
grep -q 'base/dex/classes.dex' <<< "$aab_listing" ||
  fail "Release AAB must contain base dex"
# S136 (F-011): the launch build ships WITHOUT the dormant account/billing
# split (build.gradle.kts adds it only with -PbangluAccount=true).
if [[ "${BANGLU_ACCOUNT:-0}" == "1" ]]; then
  grep -q 'android_account/dex/classes.dex' <<< "$aab_listing" ||
    fail "BANGLU_ACCOUNT=1 but the AAB has no android_account split"
  echo "account_dynamic_feature=present (opt-in build)"
else
  if grep -q 'android_account/' <<< "$aab_listing"; then
    fail "Release AAB contains the android_account split — the launch build must not ship account/billing code"
  fi
  echo "account_dynamic_feature=absent (launch posture)"
fi
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

echo "== Optional device smoke (exact signed-AAB splits) =="
# S136 (F-006/F-014): installs the Play-style splits bundletool generates from
# THIS AAB (not a debug APK), drives a deterministic typing/accessibility
# flow, and enforces memory/jank/ANR thresholds. Needs a connected phone.
if [[ "${RUN_DEVICE_SMOKE:-0}" == "1" ]]; then
  if ! command -v adb >/dev/null 2>&1; then
    fail "RUN_DEVICE_SMOKE=1 requires adb"
  fi
  if ! adb get-state >/dev/null 2>&1; then
    fail "RUN_DEVICE_SMOKE=1 requires a connected Android device"
  fi
  BUNDLETOOL_VERSION="1.18.3"
  BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
  BUNDLETOOL_JAR="$ROOT_DIR/build/tools/bundletool-all-$BUNDLETOOL_VERSION.jar"
  if [[ ! -f "$BUNDLETOOL_JAR" ]]; then
    mkdir -p "$ROOT_DIR/build/tools"
    gh release download "$BUNDLETOOL_VERSION" --repo google/bundletool \
      --pattern "bundletool-all-$BUNDLETOOL_VERSION.jar" --dir "$ROOT_DIR/build/tools" --clobber
  fi
  [[ "$(shasum -a 256 "$BUNDLETOOL_JAR" | cut -d' ' -f1)" == "$BUNDLETOOL_SHA256" ]] ||
    fail "bundletool jar checksum mismatch"
  python3 "$ROOT_DIR/scripts/android_device_smoke.py" \
    --aab "$AAB" --bundletool "$BUNDLETOOL_JAR" --keystore "$store_file" \
    --local-properties "$LOCAL_PROPS" --expect-version "$VERSION_NAME" \
    --aab-sha256 "$aab_sha256" --revision "$HEAD_REVISION" --cert-sha256 "$artifact_cert" \
    --out "$ROOT_DIR/build/android-release-smoke" ${DEVICE_SMOKE_CLEAN_INSTALL:+--clean-install}
else
  echo "skipped; set RUN_DEVICE_SMOKE=1 to install and collect a real-device IME report"
fi

echo
echo "Release validation passed."
