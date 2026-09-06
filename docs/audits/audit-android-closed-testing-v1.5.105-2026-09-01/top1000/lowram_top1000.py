"""S171 protocol on the 2 GB emulator: type the top-1000 keys, compare to the JVM oracle,
sample PSS/PID, and scan logcat for LMK/OOM/ANR. Usage: lowram_top1000.py oracle.tsv out.tsv"""
import sys, time, re, statistics, subprocess
sys.argv_saved = list(sys.argv)
inp, outp = sys.argv[1], sys.argv[2]
sys.argv = ["delete_test.py"]
src = open("/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad/delete_test.py").read().split('print("=== A.')[0]
exec(src)
import unicodedata
def fold(s): return unicodedata.normalize("NFC", s).replace("য়","য়").replace("ড়","ড়").replace("ঢ়","ঢ়")
def pss():
    out = kb.sh("dumpsys","meminfo","com.banglu.keyboard"); m = re.search(r"TOTAL PSS:\s+(\d+)", out); return int(m.group(1))//1024 if m else -1
def pid(): return kb.sh("pidof","com.banglu.keyboard").strip()
rows = [l.rstrip("\n").split("\t") for l in open(inp, encoding="utf-8") if l.strip()]
out = open(outp, "w", encoding="utf-8"); out.write("bengali\tkey\tjvm\tdevice\tmatch\n")
pid0 = pid(); psss = []; ok = 0; n = 0
kb.sh("logcat","-c")
clear(); since_clear = 0
for i, r in enumerate(rows):
    bengali, key, jvm = r[0], r[1], r[2]
    if since_clear >= 20:
        clear(); since_clear = 0
    for ch in key: tapk(ch, 0.05)
    tapk(" ", 0.45)
    full = text()
    got = full.strip().split(" ")[-1] if full.strip() else ""
    since_clear += 1
    m = fold(got) == fold(jvm); ok += m; n += 1
    out.write(f"{bengali}\t{key}\t{jvm}\t{got}\t{'OK' if m else 'DIFF'}\n"); out.flush()
    if i % 50 == 0:
        p = pss(); psss.append(p); print(f"{i:4d} ok={ok}/{n} pss={p}MB pid={pid()}", flush=True)
p = pss(); psss.append(p)
lc = kb.sh("logcat","-d")
lmk = len(re.findall(r"lowmemorykiller|Kill 'com.banglu.keyboard'|killing.*com.banglu.keyboard", lc)); oom = lc.count("OutOfMemoryError"); anr = len(re.findall(r"ANR in com.banglu.keyboard", lc))
print(f"DONE engine-exact={ok}/{n} pid_start={pid0} pid_end={pid()} pss_median={statistics.median(psss)}MB pss_max={max(psss)}MB lmk={lmk} oom={oom} anr={anr}")
