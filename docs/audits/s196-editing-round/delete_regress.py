import sys, time, re
sys.argv = ["delete_test.py"]
src = open("/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad/delete_test.py").read().split('print("=== A.')[0]
exec(src)
def bs(n=1, wait=0.6):
    for _ in range(n): tapk("BS", wait)
def marker():
    if "SHIFT" in keys: tapk("SHIFT", 0.3)
    tapk("k", 0.8)
print("R1 delete run then type: amader, BS x3, then 'r', space")
clear(); type_word("amader"); tapk(" ", 0.8); bs(4); print("  after BS:", repr(text())); tapk("r", 0.8); print("  after r:", repr(text())); tapk(" ", 0.8); print("  after space:", repr(text()))
print("R2 delete run then space commits exactly the visible text: bishwabiddaloy, BS x3, space, then 'kotha'")
clear(); type_word("bishwabiddaloy"); tapk(" ", 0.8); bs(4); v = text(); print("  visible:", repr(v)); tapk(" ", 1.0); print("  after space:", repr(text())); type_word("kotha"); tapk(" ", 0.8); print("  then kotha:", repr(text()))
print("R3 mid-word insert with caret: tomar, caret after তোমা (left x1), type 'de', then marker")
clear(); type_word("tomar"); tapk(" ", 0.8); kb.sh("input","keyevent","67"); time.sleep(0.3); kb.sh("input","keyevent","21"); time.sleep(0.4)
tapk("d", 0.5); tapk("e", 0.8); print("  after de:", repr(text())); marker(); print("  marker:", repr(text()))
print("R4 mid-word delete run: tomader, caret after তোমাদে (left x1), BS x2, then marker")
clear(); type_word("tomader"); tapk(" ", 0.8); kb.sh("input","keyevent","67"); time.sleep(0.3); kb.sh("input","keyevent","21"); time.sleep(0.4)
bs(1); print("  after BS1:", repr(text())); bs(1); print("  after BS2:", repr(text())); marker(); print("  marker:", repr(text()))
print("R5 plain typing then backspace inside the un-committed word (no resume involved): 'bhalo' BS x2")
clear(); type_word("bhalo"); bs(2); print("  visible:", repr(text())); tapk("o", 0.8); print("  +o:", repr(text()))
clear()
