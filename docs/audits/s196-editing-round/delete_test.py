"""Delete-experience + chandrabindu-caret probe on the S22 (BN mode, Banglu try-field)."""
import sys, time, re, subprocess
sys.path.insert(0, "/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad")
import kb
def field_box():
    for n in kb.dump().iter('node'):
        if n.get('class','').endswith('EditText'): return [int(v) for v in re.findall(r'\d+',n.get('bounds'))]
def text():
    f = kb.field_text() or ""
    return "" if "লিখে দেখুন" in f else f
def chips():
    return [cd.replace("Suggestion ","") for cd,tx,b_,c in kb.keys(kb.dump()) if cd.startswith("Suggestion ")]
kb.sh("ime","set","com.banglu.keyboard/.BangluIMEService"); time.sleep(0.5)
kb.sh("am","start","-n","com.banglu.keyboard/.MainActivity"); time.sleep(4)
for _ in range(3):
    skip = kb.find("এড়িয়ে যান")
    if not skip: break
    x, y = kb.center(skip); kb.sh("input", "tap", str(x), str(y)); time.sleep(1.5)
b = field_box()
if b is None:
    time.sleep(3); b = field_box()
if b is None:
    print("no field; focus:", kb.sh("dumpsys","window").split("mCurrentFocus")[1][:100]); sys.exit(1)
kb.sh("input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)); time.sleep(3)
t = kb.dump()
if [tx for cd,tx,b_,c in kb.keys(t) if tx == "English (EN)"]:
    kb.tap("BN", t); time.sleep(0.8); t = kb.dump()
keys = {}
for cd,tx,bb,c in kb.keys(t):
    m = re.match(r'^([a-z])(\.|$)', cd)
    if m: keys[m.group(1)] = bb
    elif cd.startswith("Spacebar"): keys[" "] = bb
    elif cd.startswith("Backspace"): keys["BS"] = bb
    elif cd == "⇧" or cd.lower().startswith("shift"): keys["SHIFT"] = bb
def c(k): b = keys[k]; return ((b[0]+b[2])//2, (b[1]+b[3])//2)
def tapk(k, wait=0.12): x,y = c(k); kb.sh("input","tap",str(x),str(y)); time.sleep(wait)
def clear():
    for _ in range(3):
        bx, by = c("BS"); kb.sh("input","keyevent","123"); kb.sh("input","swipe",str(bx),str(by),str(bx),str(by),"3000"); time.sleep(0.3)
        if text() == "": return
def type_word(w):
    for ch in w: tapk(ch)
print("=== A. backspace step-by-step through converted words")
for w in ["tomader", "bishwabiddaloy", "kotha", "amader"]:
    clear(); type_word(w); tapk(" ", 0.8)
    seq = [text()]
    for i in range(len(w)+1):
        tapk("BS", 0.7); seq.append(text() + "  [" + "|".join(chips()[:3]) + "]")
        if text() == "": break
    print(w, "->", " => ".join(seq))
print("=== B. hold-delete speed: 40 chars, hold 2.0 s")
clear()
for w in ["amader", "tomader", "bangladesh", "kotha", "bhalo"]: type_word(w); tapk(" ", 0.5)
before = text(); bx, by = c("BS")
t0 = time.time(); kb.sh("input","swipe",str(bx),str(by),str(bx),str(by),"2000"); time.sleep(0.8)
after = text(); print(f"before={before!r} ({len(before)} chars) after 2.0s hold={after!r} ({len(after)} chars) deleted={len(before)-len(after)}")
print("=== C. chandrabindu caret: 'bad' then caret after বা, hold c, then marker")
clear(); type_word("bad"); tapk(" ", 0.8); kb.sh("input","keyevent","67"); time.sleep(0.4)  # remove the space
kb.sh("input","keyevent","21"); time.sleep(0.4)  # DPAD_LEFT from END: caret after বা
x,y = c("c"); kb.sh("input","swipe",str(x),str(y),str(x),str(y),"1100"); time.sleep(0.8)
mid = text(); print("after hold c:", repr(mid))
if "SHIFT" in keys: tapk("SHIFT", 0.3)
tapk("k", 0.8); print("after marker K:", repr(text()))
print("=== D. chandrabindu at end then cursor left twice and a letter")
clear(); type_word("cha"); x,y = c("c"); kb.sh("input","swipe",str(x),str(y),str(x),str(y),"1100"); time.sleep(0.6); tapk("d", 0.5); tapk(" ", 0.8); kb.sh("input","keyevent","67"); time.sleep(0.3)
print("word:", repr(text()))
kb.sh("input","keyevent","21"); kb.sh("input","keyevent","21"); time.sleep(0.4)
if "SHIFT" in keys: tapk("SHIFT", 0.3)
tapk("k", 0.8); print("after 2x left + marker K:", repr(text()))
clear()
