#!/usr/bin/env python3
"""S188 pass 2: deeper category corpora. Fixes from pass 1: TextExtracts returns
whole-page text for ONE page per request (pass 1 got intros only), the upazila
and union categories are split into per-district subcategories (walk one level),
and Wikisource book pages transclude proofread pages (use action=parse)."""
import json, re, time, urllib.request, urllib.parse, collections
from pathlib import Path
BASE = Path(__file__).resolve().parent
UA = "BangluEngineStudy/1.0 (https://www.bangluweb.com; engine coverage research)"
TOKEN_RE = re.compile(r"[ঀ-৿]+"); TAG_RE = re.compile(r"(?s)<[^>]+>")
def api(host, params):
    url = f"https://{host}/w/api.php?" + urllib.parse.urlencode(dict(params, format="json"))
    for _ in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30) as r: return json.loads(r.read())
        except Exception: time.sleep(1.5)
    return {}
def members(cat, ns, cap=5000):
    out, cont = [], {}
    while len(out) < cap:
        d = api("bn.wikipedia.org", dict(action="query", list="categorymembers", cmtitle=cat, cmlimit=500, cmnamespace=ns, **cont))
        out += [m["title"] for m in d.get("query", {}).get("categorymembers", [])]
        cont = d.get("continue", {})
        if not cont: break
        time.sleep(0.25)
    return out[:cap]
def members_recursive(cat, cap_pages, depth=1):
    pages = members(cat, 0, cap_pages)
    if depth > 0:
        for sub in members(cat, 14, 400):
            if len(pages) >= cap_pages: break
            pages += members_recursive(sub, cap_pages - len(pages), depth - 1)
    return list(dict.fromkeys(pages))[:cap_pages]
def page_text(host, title):
    d = api(host, dict(action="query", prop="extracts", explaintext=1, titles=title))
    for p in d.get("query", {}).get("pages", {}).values(): return p.get("extract", "") or ""
    return ""
def count_texts(texts):
    c = collections.Counter()
    for t in texts: c.update(TOKEN_RE.findall(t))
    return c
def write(name, c, titles=None):
    with open(BASE / f"{name}_counts.tsv", "w", encoding="utf-8") as f:
        for w, n in c.most_common(): f.write(f"{w}\t{n}\n")
    if titles is not None:
        tc = count_texts(titles)
        with open(BASE / f"{name}_titles.tsv", "w", encoding="utf-8") as f:
            for w, n in tc.most_common(): f.write(f"{w}\t{n}\n")
    print(f"{name}: tokens={sum(c.values())} unique={len(c)}", flush=True)
def harvest(name, cats, cap_titles, cap_pages, depth):
    titles = []
    for c in cats:
        got = members_recursive(c, cap_titles, depth); print(f"{c}: {len(got)} titles", flush=True); titles += got
    titles = list(dict.fromkeys(titles))
    texts = []
    for i, t in enumerate(titles[:cap_pages]):
        texts.append(page_text("bn.wikipedia.org", t)); time.sleep(0.15)
        if i % 100 == 99: print(f"  {name}: {i+1} pages", flush=True)
    write(name, count_texts(texts) + count_texts(titles), titles)
harvest("places", ["বিষয়শ্রেণী:বাংলাদেশের উপজেলা", "বিষয়শ্রেণী:বাংলাদেশের ইউনিয়ন পরিষদ", "বিষয়শ্রেণী:বাংলাদেশের গ্রাম", "বিষয়শ্রেণী:বাংলাদেশের জেলা", "বিষয়শ্রেণী:বাংলাদেশের নদী", "বিষয়শ্রেণী:বাংলাদেশের শহর"], 3000, 700, 2)
harvest("people", ["বিষয়শ্রেণী:বাংলাদেশী ব্যক্তি", "বিষয়শ্রেণী:একুশে পদক বিজয়ী", "বিষয়শ্রেণী:বাংলাদেশী ক্রিকেটার", "বিষয়শ্রেণী:বাংলাদেশী রাজনীতিবিদ", "বিষয়শ্রেণী:বাংলাদেশী লেখক", "বিষয়শ্রেণী:বাংলাদেশী কবি", "বিষয়শ্রেণী:বাংলাদেশী অভিনেতা", "বিষয়শ্রেণী:বাংলাদেশী অভিনেত্রী"], 3000, 500, 1)
harvest("science", ["বিষয়শ্রেণী:পদার্থবিজ্ঞান", "বিষয়শ্রেণী:রসায়ন", "বিষয়শ্রেণী:জীববিজ্ঞান", "বিষয়শ্রেণী:গণিত", "বিষয়শ্রেণী:চিকিৎসাবিজ্ঞান", "বিষয়শ্রেণী:জ্যোতির্বিজ্ঞান", "বিষয়শ্রেণী:কম্পিউটার বিজ্ঞান", "বিষয়শ্রেণী:মানবদেহ", "বিষয়শ্রেণী:উদ্ভিদবিজ্ঞান", "বিষয়শ্রেণী:প্রাণিবিজ্ঞান", "বিষয়শ্রেণী:প্রযুক্তি"], 1500, 500, 1)
harvest("objects", ["বিষয়শ্রেণী:খাদ্য", "বিষয়শ্রেণী:বাংলাদেশী রন্ধনশৈলী", "বিষয়শ্রেণী:ফল", "বিষয়শ্রেণী:সবজি", "বিষয়শ্রেণী:পোশাক", "বিষয়শ্রেণী:যানবাহন", "বিষয়শ্রেণী:আসবাবপত্র", "বিষয়শ্রেণী:রান্নার সরঞ্জাম", "বিষয়শ্রেণী:বাদ্যযন্ত্র", "বিষয়শ্রেণী:পাখি", "বিষয়শ্রেণী:মাছ", "বিষয়শ্রেণী:উদ্ভিদ", "বিষয়শ্রেণী:প্রাণী", "বিষয়শ্রেণী:সরঞ্জাম"], 1500, 500, 1)
# Wikisource literature: rendered pages (transclusions resolved)
lit = []
for pre in ["গল্পগুচ্ছ/", "দেবদাস/", "শ্রীকান্ত/", "পথের পাঁচালী/", "চোখের বালি/", "আনন্দমঠ/", "অগ্নিবীণা/", "সঞ্চিতা/", "গোরা/", "ঘরে-বাইরে/", "কপালকুণ্ডলা/", "দুর্গেশনন্দিনী/", "পল্লীসমাজ/", "পদ্মানদীর মাঝি/", "লালসালু/", "হাজার বছর ধরে/", "চাঁদের পাহাড়/", "আরণ্যক/", "শেষের কবিতা/", "গৃহদাহ/", "চরিত্রহীন/", "বিষবৃক্ষ/", "রাজসিংহ/", "যোগাযোগ/", "নৌকাডুবি/", "রূপসী বাংলা/", "বনলতা সেন/", "ছায়ানট/", "বিষের বাঁশী/", "মৃত্যুক্ষুধা/"]:
    d = api("bn.wikisource.org", dict(action="query", list="prefixsearch", pssearch=pre, pslimit=40))
    titles = [x["title"] for x in d.get("query", {}).get("prefixsearch", [])]
    n = 0
    for t in titles:
        r = api("bn.wikisource.org", dict(action="parse", page=t, prop="text"))
        html = r.get("parse", {}).get("text", {}).get("*", "")
        if html: lit.append(TAG_RE.sub(" ", html)); n += 1
        time.sleep(0.2)
    print(f"wikisource {pre}: {n} pages", flush=True)
write("literature", count_texts(lit))
print("DONE", flush=True)
