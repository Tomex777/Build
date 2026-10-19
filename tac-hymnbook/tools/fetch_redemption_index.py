#!/usr/bin/env python3
"""Fetch the 800-number RH2006 first-line index from Hymnary.

This is an INDEX importer, not a lyrics scraper. The 2015 reset of the
1951 hymnal contains traditional hymns alongside potentially protected
works/editions. Do not bulk-copy lyrics without rights verification.

Usage: python3 tools/fetch_redemption_index.py
Dependencies: requests, beautifulsoup4
"""
from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "data" / "redemption-hymnal-index.json"
HYMNAL_URL = "https://hymnary.org/hymnal/RH2006"
LINK_RE = re.compile(r"/hymn/RH2006/(\\d+)([a-z]?)", re.I)
NUMBER_RE = re.compile(r"^(\\d{1,3})([a-z])?$", re.I)


def parse_index(html: str) -> list[dict]:
    soup = BeautifulSoup(html, "html.parser")
    found = []
    for tr in soup.find_all("tr"):
        links = [
            (LINK_RE.search(a.get("href", "")), a.get_text(" ", strip=True))
            for a in tr.find_all("a", href=True)
        ]
        hymn_links = [(m, text) for m, text in links if m is not None]
        if not hymn_links:
            continue
        n = int(hymn_links[0][0].group(1))
        suffix = hymn_links[0][0].group(2).lower()
        numbers = [text for _, text in hymn_links if NUMBER_RE.fullmatch(text)]
        titles = [text for _, text in hymn_links if not NUMBER_RE.fullmatch(text)]
        if not (1 <= n <= 800 and numbers and titles):
            continue
        title = titles[0].strip()
        if not title or len(title) < 4:
            raise ValueError(f"Unusable title for RH {n}: {title!r}")
        found.append({
            "number": n,
            "tune_variant": suffix or None,
            "title_en": title,
            "source_url": f"https://hymnary.org/hymn/RH2006/{n}{suffix}",
        })
    return found


def build_catalogue(page_htmls: list[str]) -> dict:
    songs = {}
    variants: dict[int, list[str]] = {}
    for html in page_htmls:
        for row in parse_index(html):
            n = row["number"]
            if n in songs:
                previous = songs[n]
                # Multiple tunes like 2a/2b have the same hymn and must not
                # inflate the canonical hymn count.
                if " ".join(previous["title_en"].lower().split()) != " ".join(row["title_en"].lower().split()):
                    raise ValueError(f"Conflicting titles for RH {n}: {previous['title_en']} vs {row['title_en']}")
                variants.setdefault(n, []).append(row["tune_variant"] or "")
                continue
            songs[n] = row
            variants[n] = [row["tune_variant"] or ""]
    expected = set(range(1, 801))
    got = set(songs)
    if got != expected:
        raise ValueError(f"Expected 800 unique RH hymn numbers, got {len(got)}; missing {sorted(expected-got)[:30]}, extras {sorted(got-expected)}")
    return {
        "collection": "redemption",
        "edition": "RH2006 (published 2015 reset of 1951 Redemption Hymnal)",
        "source": HYMNAL_URL,
        "scope": "numbers_and_english_first_lines_only",
        "lyrics_included": False,
        "permission_note": "Hymnal index metadata only. Do not import modern lyrics, translations, or arrangements without applicable reuse permission.",
        "hymns": [
            {
                "number": n,
                "title_en": songs[n]["title_en"],
                "source_url": f"https://hymnary.org/hymn/RH2006/{n}",
                "tune_variants": sorted(set(variants[n])),
            }
            for n in range(1, 801)
        ],
    }


def fetch_catalogue() -> dict:
    session = requests.Session()
    session.headers.update({
        "User-Agent": "NIGHT-Hymnbook-Index/1.0 (catalogue research; nine pages; no lyrics)",
        "Accept": "text/html,application/xhtml+xml",
    })
    pages = []
    for i in range(9):  # Hymnary paginates RH2006 from page=0 through page=8.
        url = HYMNAL_URL if i == 0 else f"{HYMNAL_URL}?page={i}"
        response = session.get(url, timeout=35)
        response.raise_for_status()
        page = response.text
        if "<html" not in page.lower():
            raise ValueError(f"Not an HTML document: {url}")
        entries = parse_index(page)
        if len(entries) < 45:
            raise ValueError(f"Unexpectedly sparse RH index page {i}: {len(entries)} parsed rows")
        print(f"Page {i}: {len(entries)} table rows", flush=True)
        pages.append(page)
        if i < 8:
            time.sleep(1.1)
    return build_catalogue(pages)


def main() -> None:
    catalog = fetch_catalogue()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Saved RH index: {len(catalog['hymns'])} canonical hymn numbers to {OUTPUT}")


if __name__ == "__main__":
    main()
