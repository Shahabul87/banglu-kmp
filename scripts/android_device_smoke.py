#!/usr/bin/env python3
"""
S136 (production re-audit, F-006/F-014): exact-AAB device certification.

Generates the Play-style split APKs for the CONNECTED device from the signed
release AAB with bundletool, installs them, binds the IME, and drives a
deterministic flow through the accessibility tree (never fixed coordinates):

  1. open the app's try-it editor, focus it
  2. tap the keys a, m, i by their accessibility labels
  3. assert the editor shows আমি (engine + IME + splits all working)
  4. assert the keyboard exposes ≥ MIN_CLICKABLE_KEYS clickable nodes
  5. enforce memory (PSS), jank and ANR thresholds

Exit code 0 = certified; 1 = a step or threshold failed. A JSON report with
every measurement is written to --out. No typed text beyond the fixed probe
word is ever read or stored.
"""
import argparse
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.banglu.keyboard"
IME_ID = f"{PACKAGE}/.BangluIMEService"
PROBE_KEYS = ["a", "m", "i"]
PROBE_EXPECTED = "আমি"
MIN_CLICKABLE_KEYS = 40
# Calibrated 2026-08-26 on SM-S901W / Android 16 from the release splits:
# heap 278 MB right after a cold install + 26-key burst (sqlite page cache
# + Compose/Skia buffers), frame p95 32.7 ms / p50 4.6 ms over 74 frames.
# The caps leave headroom for device variance while still catching a real
# regression (a leak or a 100 ms frame class), which is their purpose.
MAX_HEAP_PSS_MB = 320.0        # Dalvik + native heap PSS (the 176MB dictionary file pages are clean/reclaimable)
MAX_TOTAL_PSS_MB = 420.0       # informative ceiling incl. mapped dictionary pages
MAX_FRAME_P95_MS = 48.0        # per-frame render time (intended vsync -> completed), three 60Hz frames
MIN_FRAMES_FOR_TIMING = 20     # the keyboard redraws only on state change; below this the sample is inconclusive
MAX_ACTIVATION_MS = 2500       # tap editor -> mInputShown=true


def run(cmd, check=True, capture=True, timeout=180):
    return subprocess.run(cmd, check=check, capture_output=capture, text=True, timeout=timeout)


def adb(*args, check=True, timeout=60):
    return run(["adb", *args], check=check, timeout=timeout).stdout


def read_secret(local_properties, key):
    with open(local_properties, encoding="utf-8") as f:
        for line in f:
            if line.startswith(key + "="):
                return line[len(key) + 1:].strip()
    return None


def build_and_install_splits(aab, bundletool, keystore, local_properties, out_dir):
    store_pass = read_secret(local_properties, "BANGLU_STORE_PASSWORD")
    key_pass = read_secret(local_properties, "BANGLU_KEY_PASSWORD")
    if not store_pass or not key_pass:
        sys.exit("ERROR: signing passwords missing from local.properties (names checked, values never printed)")
    apks = os.path.join(out_dir, "release-device.apks")
    if os.path.exists(apks):
        os.remove(apks)
    # Passwords go through owner-only temp files, never argv.
    with tempfile.NamedTemporaryFile("w", delete=False) as sp, tempfile.NamedTemporaryFile("w", delete=False) as kp:
        sp.write(store_pass)
        kp.write(key_pass)
    for path in (sp.name, kp.name):
        os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)
    try:
        run([
            "java", "-jar", bundletool, "build-apks",
            f"--bundle={aab}", f"--output={apks}", "--connected-device",
            f"--ks={keystore}", "--ks-key-alias=banglu",
            f"--ks-pass=file:{sp.name}", f"--key-pass=file:{kp.name}",
        ], timeout=600)
        run(["java", "-jar", bundletool, "install-apks", f"--apks={apks}"], timeout=600)
    finally:
        for path in (sp.name, kp.name):
            try:
                os.remove(path)
            except OSError:
                pass
    return apks


def installed_version():
    out = adb("shell", "dumpsys", "package", PACKAGE)
    m = re.search(r"versionName=([^\s]+)", out)
    return m.group(1) if m else None


def bind_ime():
    adb("shell", "ime", "disable", IME_ID, check=False)
    adb("shell", "ime", "enable", IME_ID)
    adb("shell", "ime", "set", IME_ID)


def dump_tree(all_windows):
    args = ["shell", "uiautomator", "dump"] + (["--windows"] if all_windows else []) + ["/sdcard/banglu-smoke.xml"]
    adb(*args, check=False)
    xml = adb("shell", "cat", "/sdcard/banglu-smoke.xml")
    try:
        return ET.fromstring(xml)
    except ET.ParseError:
        return None


def nodes(root):
    return list(root.iter("node")) if root is not None else []


def bounds_center(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(node):
    c = bounds_center(node)
    if not c:
        raise RuntimeError("node has no bounds")
    adb("shell", "input", "tap", str(c[0]), str(c[1]))


ONBOARDING_DISMISS_LABELS = ("Skip", "শুরু করুন", "শুরু করি")


def dismiss_onboarding():
    """A fresh install (the certification case) opens the onboarding
    carousel before the home screen; step past it like a first-run user."""
    for _ in range(4):
        tree = nodes(dump_tree(False))
        if any("EditText" in n.get("class", "") for n in tree):
            return
        target = next((n for n in tree if n.get("text") in ONBOARDING_DISMISS_LABELS), None)
        if target is None:
            return
        tap(target)
        time.sleep(1.5)


def find_editor():
    dismiss_onboarding()
    for _ in range(10):
        for n in nodes(dump_tree(False)):
            if "EditText" in n.get("class", ""):
                return n
        time.sleep(1)
    return None


def keyboard_nodes():
    return [n for n in nodes(dump_tree(True)) if n.get("package") == PACKAGE and n.get("content-desc")]


def wait_input_shown(timeout_s):
    start = time.time()
    while time.time() - start < timeout_s:
        if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
            return int((time.time() - start) * 1000)
        time.sleep(0.1)
    return None


def find_key(label):
    for _ in range(6):
        for n in keyboard_nodes():
            cd = n.get("content-desc", "")
            if cd == label or cd.startswith(label + "."):
                return n
        time.sleep(0.5)
    return None


def editor_text():
    for n in nodes(dump_tree(False)):
        if "EditText" in n.get("class", ""):
            return n.get("text", "")
    return ""


def meminfo_mb():
    """(total PSS MB, heap PSS MB = Dalvik + native) from dumpsys meminfo."""
    out = adb("shell", "dumpsys", "meminfo", PACKAGE)
    total = re.search(r"TOTAL PSS:\s+(\d+)", out) or re.search(r"TOTAL\s+(\d+)", out)
    heap = 0
    for label in ("Dalvik Heap", "Native Heap"):
        m = re.search(label + r"\s+(\d+)", out)
        if m:
            heap += int(m.group(1))
    return (int(total.group(1)) / 1024.0 if total else None), heap / 1024.0


def frame_timings_ms():
    """Per-frame render durations (intended vsync -> frame completed) for
    the keyboard process from `gfxinfo framestats`, excluding flagged
    (first-draw / resize) frames."""
    out = adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats")
    durations = []
    block = False
    header = None
    for line in out.splitlines():
        line = line.strip()
        if line == "---PROFILEDATA---":
            block = not block
            header = None
            continue
        if not block:
            continue
        cols = [c for c in line.split(",") if c != ""]
        if header is None:
            header = cols
            continue
        if len(cols) < len(header):
            continue
        try:
            row = dict(zip(header, cols))
            if int(row.get("Flags", "0")) != 0:
                continue
            start = int(row["IntendedVsync"])
            done = int(row["FrameCompleted"])
        except (KeyError, ValueError):
            continue
        if done > start:
            durations.append((done - start) / 1e6)
    return durations


def percentile(values, pct):
    if not values:
        return None
    s = sorted(values)
    k = max(0, min(len(s) - 1, int(round(pct / 100.0 * (len(s) - 1)))))
    return s[k]


def anr_seen(since_marker_cleared):
    log = adb("logcat", "-d", "-s", "ActivityManager:E", "ANRManager:*", check=False)
    return f"ANR in {PACKAGE}" in log


def ensure_awake_and_unlocked():
    """Wake the screen, keep it on for the run, and refuse to continue on a
    locked device — a PIN/biometric lock cannot be dismissed by adb, and every
    later step would fail with misleading 'not found' errors."""
    adb("shell", "svc", "power", "stayon", "usb", check=False)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    time.sleep(0.8)
    adb("shell", "input", "swipe", "540", "1800", "540", "600", "250", check=False)
    time.sleep(1.0)
    window = adb("shell", "dumpsys", "window", check=False)
    locked = "mDreamingLockscreen=true" in window or "isKeyguardShowing=true" in window
    if locked:
        sys.exit("ERROR: the device is locked — unlock it (PIN/biometric) and rerun the device smoke")


def release_keep_awake():
    adb("shell", "svc", "power", "stayon", "false", check=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--aab", required=True)
    ap.add_argument("--bundletool", required=True)
    ap.add_argument("--keystore", required=True)
    ap.add_argument("--local-properties", required=True)
    ap.add_argument("--expect-version", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--skip-install", action="store_true", help="device already has the build under test")
    ap.add_argument("--aab-sha256", default="", help="recorded in the report (provenance)")
    ap.add_argument("--revision", default="", help="git revision the AAB embeds (provenance)")
    ap.add_argument("--cert-sha256", default="", help="signing certificate SHA-256 (provenance)")
    ap.add_argument("--clean-install", action="store_true",
                    help="uninstall first (a release-signed build cannot update a debug-signed one)")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    report = {
        "package": PACKAGE,
        "artifact": {
            "aab": os.path.basename(a.aab),
            "aab_sha256": a.aab_sha256,
            "revision": a.revision,
            "signing_cert_sha256": a.cert_sha256,
            "expected_version": a.expect_version,
        },
        "checks": {}, "measurements": {},
    }
    failures = []

    def check(name, ok, detail):
        report["checks"][name] = {"ok": bool(ok), "detail": detail}
        print(f"[{'ok' if ok else 'FAIL'}] {name}: {detail}")
        if not ok:
            failures.append(name)

    ensure_awake_and_unlocked()
    report["device"] = {
        "model": adb("shell", "getprop", "ro.product.model").strip(),
        "android": adb("shell", "getprop", "ro.build.version.release").strip(),
        "sdk": adb("shell", "getprop", "ro.build.version.sdk").strip(),
    }
    if not a.skip_install:
        if a.clean_install:
            adb("uninstall", PACKAGE, check=False)
        build_and_install_splits(a.aab, a.bundletool, a.keystore, a.local_properties, a.out)
    version = installed_version()
    check("installed_version", version == a.expect_version, f"installed {version}, expected {a.expect_version}")

    bind_ime()
    adb("logcat", "-c", check=False)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset", check=False)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(2.5)
    editor = find_editor()
    check("editor_found", editor is not None, "try-it editor located in the app")
    if editor is None:
        return finish(report, failures, a.out)
    # Clear anything the editor already holds (the app's own মুছুন button).
    for n in nodes(dump_tree(False)):
        if n.get("text") == "মুছুন":
            tap(n)
            time.sleep(0.5)
            break
    tap(editor)
    activation_ms = wait_input_shown(MAX_ACTIVATION_MS / 1000.0 + 3)
    report["measurements"]["keyboard_activation_ms"] = activation_ms
    check("keyboard_activation", activation_ms is not None and activation_ms <= MAX_ACTIVATION_MS,
          f"{activation_ms} ms (max {MAX_ACTIVATION_MS})")
    time.sleep(2.5)  # seed engine + store attach on a cold process
    # Frame stats start AFTER the window is up: the first frames of a cold
    # window are always long and would dominate a tiny sample.
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset", check=False)

    keys = keyboard_nodes()
    clickable = sum(1 for n in keys if n.get("clickable") == "true")
    report["measurements"]["keyboard_nodes"] = len(keys)
    report["measurements"]["keyboard_clickable_nodes"] = clickable
    check("accessibility_clickable_keys", clickable >= MIN_CLICKABLE_KEYS,
          f"{clickable} clickable of {len(keys)} labelled nodes (min {MIN_CLICKABLE_KEYS})")

    for label in PROBE_KEYS:
        key = find_key(label)
        check(f"key_{label}_present", key is not None, f"key '{label}' in the accessibility tree")
        if key is None:
            return finish(report, failures, a.out)
        tap(key)
        time.sleep(0.4)
    time.sleep(1.5)
    text = editor_text()
    check("typing_probe", text.strip() == PROBE_EXPECTED, f"editor shows '{text.strip()}' (expected '{PROBE_EXPECTED}')")

    # Second pass — backspace the word away and retype it: exercises delete
    # and gives the frame counter a real sample (each press animates).
    backspace = find_key("Backspace")
    if backspace is not None:
        for _ in range(len(PROBE_EXPECTED) + 1):
            tap(backspace)
            time.sleep(0.25)
        for label in PROBE_KEYS:
            key = find_key(label)
            if key is not None:
                tap(key)
                time.sleep(0.35)
        time.sleep(1.5)
        text2 = editor_text()
        check("delete_and_retype", text2.strip() == PROBE_EXPECTED,
              f"after backspace x{len(PROBE_EXPECTED) + 1} and retype: '{text2.strip()}'")
        # Frame sample: a burst of key presses (each animates) so gfxinfo has
        # a real population to judge — fewer than MIN_FRAMES_FOR_JANK frames
        # makes the jank check INCONCLUSIVE, which fails certification.
        burst = [n for n in keyboard_nodes() if len(n.get("content-desc", "")) == 1 and n.get("content-desc", "").isalpha()]
        for n in burst[:26]:
            tap(n)
            time.sleep(0.12)
        for _ in range(min(26, len(burst))):
            tap(backspace)
            time.sleep(0.1)
        time.sleep(1.0)

    pss, heap = meminfo_mb()
    report["measurements"]["total_pss_mb"] = pss
    report["measurements"]["heap_pss_mb"] = heap
    check("memory_heap", heap <= MAX_HEAP_PSS_MB, f"Dalvik+native heap {round(heap, 1)} MB (max {MAX_HEAP_PSS_MB})")
    check("memory_total_pss", pss is not None and pss <= MAX_TOTAL_PSS_MB,
          f"total PSS {pss and round(pss, 1)} MB incl. mapped dictionary pages (max {MAX_TOTAL_PSS_MB})")
    timings = frame_timings_ms()
    p50, p95, worst = percentile(timings, 50), percentile(timings, 95), (max(timings) if timings else None)
    report["measurements"]["frames_sampled"] = len(timings)
    report["measurements"]["frame_ms_p50"] = p50
    report["measurements"]["frame_ms_p95"] = p95
    report["measurements"]["frame_ms_max"] = worst
    if len(timings) < MIN_FRAMES_FOR_TIMING:
        # S138 (F-014): an unmeasured threshold is INCONCLUSIVE, never a pass.
        check("frame_timing", False, f"inconclusive — {len(timings)} frames sampled (need {MIN_FRAMES_FOR_TIMING})")
    else:
        check("frame_timing", p95 <= MAX_FRAME_P95_MS,
              f"p50 {round(p50, 1)} ms, p95 {round(p95, 1)} ms, max {round(worst, 1)} ms over {len(timings)} frames (p95 max {MAX_FRAME_P95_MS})")
    check("no_anr", not anr_seen(True), "no ANR for the keyboard process in logcat during the probe")

    adb("shell", "input", "keyevent", "BACK", check=False)
    return finish(report, failures, a.out)


def finish(report, failures, out_dir):
    release_keep_awake()
    report["result"] = "certified" if not failures else "failed"
    report["failed_checks"] = failures
    path = os.path.join(out_dir, "device-smoke-report.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"report={path}")
    print(f"device_smoke={report['result']}")
    sys.exit(0 if not failures else 1)


if __name__ == "__main__":
    main()
