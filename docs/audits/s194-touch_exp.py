"""Replay identical touch sequences on Banglu (EN mode) and the Samsung keyboard in the
Banglu try-field; compare the committed text. Usage: touch_exp.py banglu|samsung out.tsv"""
import kb, time, re, sys, random, subprocess
which, outp = sys.argv[1], sys.argv[2]
IME = {"banglu": "com.banglu.keyboard/.BangluIMEService", "samsung": "com.samsung.android.honeyboard/.service.HoneyBoardService"}[which]
def field_box():
    for n in kb.dump().iter('node'):
        if n.get('class','').endswith('EditText'): return [int(v) for v in re.findall(r'\d+',n.get('bounds'))]
def text():
    f = kb.field_text() or ""
    return "" if "লিখে দেখুন" in f else f
kb.sh("ime","set",IME); time.sleep(0.6)
kb.sh("am","start","-n","com.banglu.keyboard/.MainActivity"); time.sleep(2)
b = field_box(); kb.sh("input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)); time.sleep(3)
t = kb.dump()
if which == "banglu" and [tx for cd,tx,b_,c in kb.keys(t) if tx == "বাংলু (BN)"]:
    kb.tap("EN", t); time.sleep(0.8); t = kb.dump()
# key map: letter -> (cx, cy, w, h)
keys = {}
for n in t.iter('node'):
    pkg = n.get('package') or ''
    if which == "banglu" and pkg != "com.banglu.keyboard": continue
    if which == "samsung" and pkg != "com.samsung.android.honeyboard": continue
    cd = n.get('content-desc') or ''
    if not n.get('bounds'): continue
    bb = [int(v) for v in re.findall(r'\d+', n.get('bounds'))]
    if bb[1] < 1500: continue
    if which == "banglu":
        m = re.match(r'^([A-Za-z])(\.|$)', cd)
        if m: keys[m.group(1).lower()] = bb
        elif cd.startswith("Spacebar"): keys[" "] = bb
        elif cd.startswith("Backspace"): keys["BS"] = bb
    else:
        if re.match(r'^[A-Z]$', cd): keys[cd.lower()] = bb
        elif cd == "Space bar": keys[" "] = bb
        elif cd == "Backspace": keys["BS"] = bb
assert len([k for k in keys if len(k) == 1 and k.isalpha()]) == 26, keys.keys()
def c(k): b = keys[k]; return ((b[0]+b[2])/2, (b[1]+b[3])/2, b[2]-b[0], b[3]-b[1])
def clear():
    for _ in range(3):
        bx, by = c("BS")[:2]
        kb.sh("input","keyevent","123"); kb.sh("input","swipe",str(int(bx)),str(int(by)),str(int(bx)),str(int(by)),"3000"); time.sleep(0.3)
        if text() == "": return
def run(lines):
    open(f"{S}/inj/seq.txt","w").write("\n".join(lines)+"\n")
    subprocess.run(["adb","push",f"{S}/inj/seq.txt","/data/local/tmp/seq.txt"],capture_output=True)
    subprocess.run(["adb","shell","export CLASSPATH=/data/local/tmp/inject.jar; app_process /system/bin Inject /data/local/tmp/seq.txt"],capture_output=True)
S = "/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad"
PHRASE = "the quick brown fox jumps over the lazy dog"
def tap_seq(points, down_ms, gap_ms):
    out = []
    for (x, y) in points:
        out += [f"d 0 {x:.0f} {y:.0f}", f"s {down_ms}", f"u 0 {x:.0f} {y:.0f}", f"s {gap_ms}"]
    return out
def pts(phrase, dx=0.0, dy=0.0, jitter=0.0, seed=1):
    rnd = random.Random(seed); out = []
    for ch in phrase:
        cx, cy, w, h = c(ch)
        x = cx + dx*w + (rnd.gauss(0, jitter) if jitter else 0)
        y = cy + dy*h + (rnd.gauss(0, jitter) if jitter else 0)
        out.append((x, y))
    return out
tests = []
tests.append(("T1 centre 30/30ms", tap_seq(pts(PHRASE), 30, 30)))
tests.append(("T2 short 12ms taps 50ms gap", tap_seq(pts(PHRASE), 12, 50)))
# T3 rollover: next key goes down before the previous comes up
lines = []; p = pts(PHRASE)
for i, (x, y) in enumerate(p):
    pid = i % 2
    lines += [f"d {pid} {x:.0f} {y:.0f}", "s 25"]
    if i > 0:
        px, py = p[i-1]; lines += [f"u {(i-1)%2} {px:.0f} {py:.0f}", "s 25"]
x, y = p[-1]; lines += [f"u {(len(p)-1)%2} {x:.0f} {y:.0f}"]
tests.append(("T3 rollover overlap 25ms", lines))
# T4 slide 24px right during the press
lines = []
for (x, y) in pts(PHRASE):
    lines += [f"d 0 {x:.0f} {y:.0f}", "s 10", f"m 0 {x+8:.0f} {y:.0f}", "s 10", f"m 0 {x+16:.0f} {y:.0f}", "s 10", f"m 0 {x+24:.0f} {y:.0f}", "s 10", f"u 0 {x+24:.0f} {y:.0f}", "s 40"]
tests.append(("T4 slide +24px in press", lines))
tests.append(("T5 right-edge taps (+38% key)", tap_seq(pts(PHRASE, dx=0.38), 30, 40)))
tests.append(("T6 bottom-edge taps (+40% row)", tap_seq(pts(PHRASE, dy=0.40), 30, 40)))
tests.append(("T7 gaussian jitter 14px", tap_seq(pts(PHRASE, jitter=14, seed=7), 30, 40)))
tests.append(("T8 gaussian jitter 22px", tap_seq(pts(PHRASE, jitter=22, seed=11), 30, 40)))
def lev(a, b):
    prev = list(range(len(b)+1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j]+1, cur[j-1]+1, prev[j-1] + (ca != cb)))
        prev = cur
    return prev[-1]
out = open(outp, "w", encoding="utf-8"); out.write("test\tkeyboard\texpected\tgot\tedits\n")
for name, lines in tests:
    clear(); time.sleep(0.3); run(lines); time.sleep(1.2)
    got = text().strip().lower()
    e = lev(PHRASE, got)
    out.write(f"{name}\t{which}\t{PHRASE}\t{got}\t{e}\n"); out.flush()
    print(f"{name:32s} edits={e:2d}  {got}")
clear()
kb.sh("ime","set","com.banglu.keyboard/.BangluIMEService")
