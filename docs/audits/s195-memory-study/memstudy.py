"""S195 memory study: clean install, then sample the IME process heap over time.
Columns: t(s) phase nativePss nativeSize nativeAlloc nativeFree dalvikPss dalvikAlloc totalPss"""
import sys, time, re, subprocess
sys.path.insert(0, "/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad")
import kb
APK = sys.argv[1]; OUT = sys.argv[2]; CLEAN = sys.argv[3] == "clean"
def meminfo():
    out = kb.sh("dumpsys", "meminfo", "com.banglu.keyboard")
    def row(label):
        m = re.search(label + r"\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)", out)
        return [int(x) for x in m.groups()] if m else None
    n = row("Native Heap"); d = row("Dalvik Heap")
    t = re.search(r"TOTAL PSS:\s+(\d+)", out)
    # columns: Pss Total, Private Dirty, Private Clean, SwapPss Dirty, Rss Total, Heap Size, Heap Alloc, Heap Free
    return (n[0]/1024 if n else -1, n[5]/1024 if n else -1, n[6]/1024 if n else -1, n[7]/1024 if n else -1,
            d[0]/1024 if d else -1, d[6]/1024 if d else -1, int(t.group(1))/1024 if t else -1)
def field_box():
    for n in kb.dump().iter('node'):
        if n.get('class','').endswith('EditText'): return [int(v) for v in re.findall(r'\d+',n.get('bounds'))]
f = open(OUT, "w"); f.write("t\tphase\tnativePss\tnativeSize\tnativeAlloc\tnativeFree\tdalvikPss\tdalvikAlloc\ttotalPss\n")
t0 = time.time()
def sample(phase):
    m = meminfo(); line = f"{time.time()-t0:6.1f}\t{phase}\t" + "\t".join(f"{x:.1f}" for x in m); f.write(line+"\n"); f.flush(); print(line)
if CLEAN:
    kb.sh("pm", "uninstall", "com.banglu.keyboard"); time.sleep(1)
    subprocess.run(["adb", "install", APK], capture_output=True)
    kb.sh("ime", "enable", "com.banglu.keyboard/.BangluIMEService")
else:
    kb.sh("am", "force-stop", "com.banglu.keyboard")
kb.sh("ime", "set", "com.banglu.keyboard/.BangluIMEService"); time.sleep(0.5)
kb.sh("am", "start", "-n", "com.banglu.keyboard/.MainActivity"); time.sleep(3)
for _ in range(3):
    skip = kb.find("এড়িয়ে যান")
    if not skip: break
    x, y = kb.center(skip); kb.sh("input", "tap", str(x), str(y)); time.sleep(1.5)
b = field_box(); kb.sh("input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2)); t0 = time.time()
for i in range(36 if CLEAN else 12):  # 3 minutes at 5 s (1 minute warm)
    sample("boot+%ds" % (i*5)); time.sleep(5)
t = kb.dump(); letters = [(cd, bb) for cd, tx, bb, c in kb.keys(t) if re.match(r'^[a-z](\.|$)', cd)]
bs = kb.find("Backspace", t)
for r in range(3):
    for cd, bb in letters[:26]:
        x, y = kb.center(bb); kb.sh("input", "tap", str(x), str(y)); time.sleep(0.05)
    sample("typing-burst-%d" % (r+1))
    x, y = kb.center(bs); kb.sh("input", "swipe", str(x), str(y), str(x), str(y), "2500"); time.sleep(0.5)
for i in range(12):
    time.sleep(10); sample("idle-after-typing+%ds" % ((i+1)*10))
kb.sh("am", "send-trim-memory", "com.banglu.keyboard", "RUNNING_CRITICAL"); time.sleep(3); sample("after-trim-RUNNING_CRITICAL")
kb.sh("am", "send-trim-memory", "com.banglu.keyboard", "COMPLETE"); time.sleep(3); sample("after-trim-COMPLETE")
time.sleep(20); sample("final")
f.close()
