#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT_DIR/android-keyboard/build/outputs/apk/debug/android-keyboard-debug.apk"
ACCOUNT_APK="$ROOT_DIR/android_account/build/outputs/apk/debug/android_account-debug.apk"
PACKAGE="com.banglu.keyboard"
IME_ID="com.banglu.keyboard/.BangluIMEService"
OUT_DIR="${1:-$ROOT_DIR/build/android-keyboard-benchmark}"

mkdir -p "$OUT_DIR"

echo "Building debug APKs..."
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" \
  :android-keyboard:verifyImePrivacyBoundary \
  :android-keyboard:assembleDebug \
  :android_account:assembleDebug >/dev/null

echo "Installing $APK and account split..."
adb install-multiple -r "$APK" "$ACCOUNT_APK" >/dev/null
adb shell ime set "$IME_ID" >/dev/null

echo "Focus any editable text field on the device now; collecting in 10 seconds..."
sleep 10

STAMP="$(date +%Y%m%d-%H%M%S)"
REPORT="$OUT_DIR/benchmark-$STAMP.txt"

{
  echo "Banglu Android keyboard benchmark"
  echo "timestamp=$STAMP"
  echo
  echo "== Device =="
  adb shell getprop ro.product.manufacturer
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell getprop ro.build.version.sdk
  echo
  echo "== Memory Class / Low RAM =="
  adb shell dumpsys activity settings | grep -E "mLowRam|mLargeHeap|mOverrideMaxCachedProcesses" || true
  echo
  echo "== IME Process =="
  adb shell pidof "$PACKAGE" || true
  echo
  echo "== Installed Splits =="
  adb shell pm path "$PACKAGE" || true
  echo
  echo "== IME Memory =="
  adb shell dumpsys meminfo "$PACKAGE" || true
  echo
  echo "== Recent Banglu Logs =="
  adb logcat -d -t 300 BangluIME:D AndroidRuntime:E '*:S' || true
} > "$REPORT"

echo "Saved benchmark report: $REPORT"
echo "For a fair latency pass, focus a text field, type 20-30 Bengali words, then rerun this script and compare TOTAL PSS plus diag_latency lines."
