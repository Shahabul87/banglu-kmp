#!/usr/bin/env python3
# S99/S100: regenerates CharBigramData.BANGLA_ROMAN from the current
# dictionary.sqlite (canonical p0 store keys, frequency-weighted, scaled
# to 65535). Rerun after any db rebuild that changes frequencies, then
# splice the output rows into shared/.../touch/CharBigramData.kt.
import sqlite3, sys
db = sqlite3.connect('/Users/mdshahabulalam/myprojects/banlgu/banglu-kmp/dictionary.sqlite')
counts = [0.0]*676
q = """SELECT p.key, p.frequency FROM phonetic_index p WHERE p.priority=0"""
n=0
for key, freq in db.execute(q):
    w = max(int(freq), 1)
    k = key.lower()
    for a, b in zip(k, k[1:]):
        if 'a' <= a <= 'z' and 'a' <= b <= 'z':
            counts[(ord(a)-97)*26 + (ord(b)-97)] += w
    n += 1
mx = max(counts)
scaled = [round(c*65535/mx) for c in counts]
print(f"# rows={n} max_raw={mx:.0f}", file=sys.stderr)
# top-10 sanity
top = sorted(range(676), key=lambda i:-scaled[i])[:10]
for i in top:
    print(f"# {chr(97+i//26)}{chr(97+i%26)} = {scaled[i]}", file=sys.stderr)
lines=[]
for r in range(26):
    lines.append("        " + ", ".join(str(scaled[r*26+c]) for c in range(26)) + ",")
open('bangla_roman_bigrams.txt','w').write("\n".join(lines)+"\n")
