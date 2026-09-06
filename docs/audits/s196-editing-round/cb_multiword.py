import sys, time, re
sys.argv=["delete_test.py"]
src=open("/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad/delete_test.py").read().split('print("=== A.')[0]
exec(src)
def holdc(): x,y=c("c"); kb.sh("input","swipe",str(x),str(y),str(x),str(y),"1100"); time.sleep(0.9)
def marker():
    if "SHIFT" in keys: tapk("SHIFT",0.3)
    tapk("k",0.8)
print("T1 arrows: 'chad ami', LEFT x5 (after চা), hold c, marker")
clear(); type_word("chad"); tapk(" ",0.5); type_word("ami"); tapk(" ",0.8); kb.sh("input","keyevent","67"); time.sleep(0.3)
for _ in range(5): kb.sh("input","keyevent","21"); time.sleep(0.15)
time.sleep(0.5); marker(); print("  caret check:", repr(text())); kb.sh("input","keyevent","67"); time.sleep(0.4)
holdc(); print("  after hold c:", repr(text())); marker(); print("  marker:", repr(text()))
print("T2 tap into first word: 'chad ami', tap near the start of the field text, hold c, marker")
clear(); type_word("chad"); tapk(" ",0.5); type_word("ami"); tapk(" ",0.8); kb.sh("input","keyevent","67"); time.sleep(0.3)
b = field_box(); kb.sh("input","tap",str(b[0]+52),str((b[1]+b[3])//2)); time.sleep(0.8)
marker(); print("  caret check:", repr(text())); kb.sh("input","keyevent","67"); time.sleep(0.4)
holdc(); print("  after hold c:", repr(text())); marker(); print("  marker:", repr(text()))
print("T3 three words, caret in the middle word: 'ami chad ami', LEFT x5, hold c")
clear(); type_word("ami"); tapk(" ",0.5); type_word("chad"); tapk(" ",0.5); type_word("ami"); tapk(" ",0.8); kb.sh("input","keyevent","67"); time.sleep(0.3)
for _ in range(5): kb.sh("input","keyevent","21"); time.sleep(0.15)
time.sleep(0.5); holdc(); print("  after hold c:", repr(text())); marker(); print("  marker:", repr(text()))
clear()
