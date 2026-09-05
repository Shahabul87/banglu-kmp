"""S188 device pass: type each (key, expected) from a TSV on the S22 and record the
strip before space and the committed text. Usage: python3 s188_device.py sample.tsv out.tsv"""
import kb, time, re, sys, unicodedata
inp, outp = sys.argv[1], sys.argv[2]
def field_box():
    for n in kb.dump().iter('node'):
        if n.get('class','').endswith('EditText'): return [int(v) for v in re.findall(r'\d+',n.get('bounds'))]
def text():
    f=kb.field_text() or ""
    return "" if "লিখে দেখুন" in f else f
def chips():
    tt=kb.dump(); return [cd.replace("Suggestion ","") for cd,tx,b_,c in kb.keys(tt) if cd.startswith("Suggestion ")]
def fold(s): return unicodedata.normalize("NFC", s).replace("য়","য়").replace("ড়","ড়").replace("ঢ়","ঢ়")
kb.sh("ime","set","com.banglu.keyboard/.BangluIMEService"); time.sleep(0.5)
kb.sh("am","start","-n","com.banglu.keyboard/.MainActivity"); time.sleep(2)
b=field_box(); kb.sh("input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)); time.sleep(4)
t=kb.dump(); bb=kb.find("Backspace",t); bx,by=kb.center(bb)
if [tx for cd,tx,b_,c in kb.keys(t) if tx=="English (EN)"]: kb.tap("BN",t); time.sleep(0.8); t=kb.dump()
def clear():
    for _ in range(4):
        kb.sh("input","keyevent","123"); kb.sh("input","swipe",str(bx),str(by),str(bx),str(by),"3500"); time.sleep(0.4)
        if text()=="": return
def type_word(w):
    for c in w: kb.tap(c+"." if c in "rtuisd" else c,t); time.sleep(0.08)
rows=[l.rstrip("\n").split("\t") for l in open(inp, encoding="utf-8") if l.strip()]
out=open(outp,"w",encoding="utf-8"); out.write("key\texpected\tdevice_commit\tdevice_strip\tmatch\tin_strip\n")
ok=ins=0
for i,(key,expected) in enumerate(rows):
    clear(); type_word(key); time.sleep(0.9); strip=chips(); kb.tap("Spacebar",t); time.sleep(0.7); got=text().strip()
    m = fold(got)==fold(expected); s = m or fold(expected) in [fold(c) for c in strip]
    ok+=m; ins+=s
    out.write(f"{key}\t{expected}\t{got}\t{'|'.join(strip)}\t{m}\t{s}\n"); out.flush()
    if i%20==19: print(f"{i+1}/{len(rows)} commit-exact={ok} in-strip={ins}", flush=True)
clear(); print(f"DONE {len(rows)} commit-exact={ok} in-strip={ins}")
