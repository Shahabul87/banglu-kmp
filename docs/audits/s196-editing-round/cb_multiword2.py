import sys, time, re
sys.argv=["delete_test.py"]
src=open("/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad/delete_test.py").read().split('print("=== A.')[0]
exec(src)
def holdc(): x,y=c("c"); kb.sh("input","swipe",str(x),str(y),str(x),str(y),"1100"); time.sleep(0.9)
def marker():
    if "SHIFT" in keys: tapk("SHIFT",0.3)
    tapk("k",0.8)
def left(n):
    for _ in range(n): kb.sh("input","keyevent","21"); time.sleep(0.15)
    time.sleep(0.5)
def setup(words):
    clear()
    for w in words: type_word(w); tapk(" ",0.5)
    time.sleep(0.5); kb.sh("input","keyevent","67"); time.sleep(0.3)
print("A 'bad ami', LEFT x4 (after বা), hold c")
setup(["bad","ami"]); left(4); holdc(); print("  after hold c:", repr(text())); marker(); print("  marker:", repr(text()))
print("B 'bad ami', LEFT x3 (after বাদ, before space), hold c")
setup(["bad","ami"]); left(3); holdc(); print("  after hold c:", repr(text())); marker(); print("  marker:", repr(text()))
print("C 'ami bad ami', LEFT x4 (after বা in the middle word), hold c")
setup(["ami","bad","ami"]); left(4); holdc(); print("  after hold c:", repr(text())); marker(); print("  marker:", repr(text()))
print("D 'bad ami', TAP into the first word, marker to read the caret, then hold c")
setup(["bad","ami"]); b=field_box(); kb.sh("input","tap",str(b[0]+75),str((b[1]+b[3])//2)); time.sleep(0.8); marker(); print("  caret:", repr(text())); kb.sh("input","keyevent","67"); time.sleep(0.5)
holdc(); print("  after hold c:", repr(text())); marker(); print("  marker:", repr(text()))
print("E 'bad ami', LEFT x4, type 'm' then hold c (letter first, then the sign)")
setup(["bad","ami"]); left(4); tapk("m",0.6); print("  after m:", repr(text())); holdc(); print("  after hold c:", repr(text())); marker(); print("  marker:", repr(text()))
clear()
