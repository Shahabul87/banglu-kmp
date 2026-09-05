#!/usr/bin/env python3
"""S188: union parishad and village names — 'বাংলাদেশের ইউনিয়ন' is a container of
per-district / per-upazila subcategories, so walk three levels. Titles are the
names people type; 300 union pages add the village names listed inside them."""
import sys; sys.path.insert(0, __file__.rsplit('/',1)[0])
from fetch_wiki2 import members, page_text, count_texts, write, api
import time
def walk(cat, cap, depth):
    pages = members(cat, 0, cap)
    if depth > 0:
        for sub in members(cat, 14, 500):
            if len(pages) >= cap: break
            pages += walk(sub, cap - len(pages), depth - 1)
    return list(dict.fromkeys(pages))[:cap]
titles = walk("বিষয়শ্রেণী:বাংলাদেশের ইউনিয়ন", 4000, 3)
print(f"union titles: {len(titles)}", flush=True)
texts = []
for i, t in enumerate(titles[:300]):
    texts.append(page_text("bn.wikipedia.org", t)); time.sleep(0.15)
write("unions", count_texts(texts) + count_texts(titles), titles)
print("DONE", flush=True)
