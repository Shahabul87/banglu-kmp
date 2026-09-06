import sys, time, re
sys.argv = ["delete_test.py"]
src = open("/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad/delete_test.py").read().split('print("=== A.')[0]
exec(src)
for w, exp in [("ajij","আজিজ"),("aziz","আযিয"),("ajiz","আজিয"),("azij","আযিজ"),("zakir","যাকির"),("jakir","জাকির"),("kaz","কাজ"),("zodi","যদি"),("jodi","যদি"),("nize","নিজে"),("zonno","জন্য"),("hamza","হামযা"),("zamai","যামাই")]:
    clear(); type_word(w); time.sleep(0.9); ch = chips()[:4]; tapk(" ", 0.8); got = text().strip()
    print(f"{w:8s} -> {got:10s} {'OK ' if got==exp else 'DIFF'} strip={ch}")
clear()
