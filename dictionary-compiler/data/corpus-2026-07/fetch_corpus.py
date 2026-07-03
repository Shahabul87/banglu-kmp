#!/usr/bin/env python3
"""Bengali news frequency-corpus harvester.

Sources:
  1. Prothom Alo (Quintype stories-by-slug JSON API) - primary
  2. BBC Bangla (HTML pages from its sitemap) - secondary

Politeness: 4 worker threads, >=0.2s between request starts per host,
browser User-Agent, 15s timeout, one retry, skip failures.
"""
import collections
import json
import re
import sys
import threading
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

BASE = Path(__file__).resolve().parent
UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
      "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
TOKEN_RE = re.compile(r"[ঀ-৿‍]+")
TAG_RE = re.compile(r"(?s)<[^>]+>")
SCRIPT_RE = re.compile(r"(?is)<(script|style|noscript)[^>]*>.*?</\1>")
TARGET_TOKENS = 1_550_000
MAX_WORKERS = 4
HOST_DELAY = 0.2

counter = collections.Counter()
counter_lock = threading.Lock()
stats = {"tokens": 0, "articles": 0, "fail": 0}
stats_lock = threading.Lock()
per_source = collections.Counter()          # source -> articles
per_source_tokens = collections.Counter()   # source -> tokens
stop_event = threading.Event()

host_last = {}
host_lock = threading.Lock()


def polite_wait(host: str) -> None:
    while True:
        with host_lock:
            now = time.monotonic()
            last = host_last.get(host, 0.0)
            if now - last >= HOST_DELAY:
                host_last[host] = now
                return
            wait = HOST_DELAY - (now - last)
        time.sleep(wait)


def fetch(url: str, timeout: int = 15, retries: int = 1):
    host = url.split("/")[2]
    for attempt in range(retries + 1):
        polite_wait(host)
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": UA,
                "Accept": "*/*",
                "Accept-Language": "bn,en;q=0.8",
            })
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.read()
        except Exception:
            if attempt == retries:
                return None
            time.sleep(0.5)
    return None


def add_text(text: str, source: str) -> int:
    toks = TOKEN_RE.findall(text)
    toks = [t.strip("‍") for t in toks]
    toks = [t for t in toks if t]
    if not toks:
        return 0
    with counter_lock:
        counter.update(toks)
    with stats_lock:
        stats["tokens"] += len(toks)
        stats["articles"] += 1
        per_source[source] += 1
        per_source_tokens[source] += len(toks)
        if stats["tokens"] >= TARGET_TOKENS:
            stop_event.set()
    return len(toks)


# ---------- Prothom Alo ----------

def pa_story_text(story: dict) -> str:
    parts = [story.get("headline") or "", story.get("subheadline") or ""]

    def walk(obj):
        if isinstance(obj, dict):
            for k, v in obj.items():
                if k == "text" and isinstance(v, str):
                    parts.append(v)
                else:
                    walk(v)
        elif isinstance(obj, list):
            for x in obj:
                walk(x)

    walk(story.get("cards", []))
    blob = " ".join(parts)
    blob = TAG_RE.sub(" ", blob)
    return blob


def do_pa_article(slug: str):
    if stop_event.is_set():
        return
    url = "https://www.prothomalo.com/api/v1/stories-by-slug?slug=" + slug
    data = fetch(url)
    if data is None:
        with stats_lock:
            stats["fail"] += 1
        return
    try:
        story = json.loads(data).get("story") or {}
        add_text(pa_story_text(story), "prothomalo")
    except Exception:
        with stats_lock:
            stats["fail"] += 1


# ---------- BBC Bangla ----------

def do_bbc_article(url: str):
    if stop_event.is_set():
        return
    data = fetch(url)
    if data is None:
        with stats_lock:
            stats["fail"] += 1
        return
    try:
        html_text = data.decode("utf-8", errors="replace")
        txt = SCRIPT_RE.sub(" ", html_text)
        txt = TAG_RE.sub(" ", txt)
        add_text(txt, "bbc_bangla")
    except Exception:
        with stats_lock:
            stats["fail"] += 1


# ---------- URL collection ----------

def collect_pa_urls():
    idx = fetch("https://www.prothomalo.com/sitemap.xml")
    if idx is None:
        return []
    daily = re.findall(r"<loc>(https://www\.prothomalo\.com/sitemap/sitemap-daily-[^<]+)</loc>",
                       idx.decode("utf-8", "replace"))
    slugs = []
    seen = set()
    for sm_url in daily:
        sm = fetch(sm_url)
        if sm is None:
            continue
        for loc in re.findall(r"<loc>(https://www\.prothomalo\.com/[^<]+)</loc>",
                              sm.decode("utf-8", "replace")):
            slug = loc.split("prothomalo.com/", 1)[1].strip()
            if slug and slug not in seen:
                seen.add(slug)
                slugs.append(slug)
    return slugs


def collect_bbc_urls():
    sm = fetch("https://www.bbc.com/bengali/sitemap.xml")
    if sm is None:
        return []
    locs = re.findall(r"<loc>([^<]+)</loc>", sm.decode("utf-8", "replace"))
    return [u.strip() for u in locs if "/bengali/" in u]


def checkpoint():
    with counter_lock:
        items = counter.most_common()
    with open(BASE / "news_counts.tsv", "w", encoding="utf-8") as f:
        for w, c in items:
            f.write(f"{w}\t{c}\n")


def main():
    print("collecting Prothom Alo article slugs...", flush=True)
    pa_slugs = collect_pa_urls()
    print(f"prothomalo slugs: {len(pa_slugs)}", flush=True)
    bbc_urls = collect_bbc_urls()
    print(f"bbc urls: {len(bbc_urls)}", flush=True)

    tasks = [("pa", s) for s in pa_slugs] + [("bbc", u) for u in bbc_urls]

    last_report = time.monotonic()
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        futures = []
        it = iter(tasks)
        # feed gradually so stop_event can cut off early
        in_flight = set()
        done_feeding = False
        while True:
            if stop_event.is_set():
                break
            while len(in_flight) < MAX_WORKERS * 4 and not done_feeding:
                try:
                    kind, arg = next(it)
                except StopIteration:
                    done_feeding = True
                    break
                fn = do_pa_article if kind == "pa" else do_bbc_article
                in_flight.add(pool.submit(fn, arg))
            if not in_flight:
                break
            done_now = {f for f in in_flight if f.done()}
            if not done_now:
                time.sleep(0.1)
            in_flight -= done_now
            now = time.monotonic()
            if now - last_report > 15:
                last_report = now
                with stats_lock:
                    print(f"progress: articles={stats['articles']} tokens={stats['tokens']} "
                          f"fail={stats['fail']}", flush=True)
                checkpoint()
        # drain
        for f in list(in_flight):
            try:
                f.result(timeout=60)
            except Exception:
                pass

    checkpoint()
    with stats_lock, counter_lock:
        uniq = len(counter)
        meta = [
            f"total_tokens\t{stats['tokens']}",
            f"unique_words\t{uniq}",
            f"articles_fetched\t{stats['articles']}",
            f"failed_fetches\t{stats['fail']}",
        ]
        for src in per_source:
            meta.append(f"source\t{src}\tarticles={per_source[src]}\ttokens={per_source_tokens[src]}")
        meta.append("blocked_sources\tbangla.bdnews24.com (403), www.anandabazar.com (403)")
        meta.append("token_regex\t[ঀ-৿\\u200d]+ (Unicode Bengali block, ZWJ-trimmed)")
    (BASE / "news_meta.txt").write_text("\n".join(meta) + "\n", encoding="utf-8")
    print("DONE", flush=True)
    for line in meta:
        print(line, flush=True)


if __name__ == "__main__":
    sys.exit(main())
