#!/usr/bin/env python3
"""Compile photo-indexed TAC hymn data into the Android app's offline JSON asset.

There is intentionally no scraping/import magic here. Online sources remain candidates
until a text draft is present, photographed/matched, and cleared for reuse.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED_DIR = ROOT / "data"
DRAFTS = ROOT / "data/drafts"
OUTPUT = ROOT / "android/app/src/main/assets/hymns.json"


def compile_asset() -> dict:
    seed = []
    for seed_path in sorted(SEED_DIR.glob("hymns-*.seed.json")):
        seed.extend(json.loads(seed_path.read_text(encoding="utf-8"))["hymns"])
    numbers = [entry["number"] for entry in seed]
    if not numbers or len(set(numbers)) != len(numbers):
        raise ValueError("Hymn numbers missing or duplicated")
    if numbers != sorted(numbers):
        raise ValueError("Seed order should follow printed hymn numbers")
    drafts = {}
    for path in sorted(DRAFTS.glob("hymn-*.yo.json")):
        entry = json.loads(path.read_text(encoding="utf-8"))
        n = entry["number"]
        if n in drafts:
            raise ValueError(f"Duplicate hymn draft for {n}")
        if not entry["verses"] or not all(
            isinstance(v, list) and v and all(isinstance(line, str) and line.strip() for line in v)
            for v in entry["verses"]
        ):
            raise ValueError(f"Invalid verses for hymn {n}")
        for language in ("verses", "verses_en"):
            for verse in entry.get(language, []):
                if not isinstance(verse, list) or not verse or not all(
                    isinstance(line, str) and line.strip() for line in verse
                ):
                    raise ValueError(f"Invalid {language} stanza for hymn {n}")
                for line in verse:
                    if "\\\\n" in line or "\\\\r" in line:
                        raise ValueError(f"Escaped newline in hymn {n}: {language}")
        if entry.get("verses_en") and len(entry["verses_en"]) != len(entry["verses"]):
            raise ValueError(f"English verse count differs from Yoruba for hymn {n}")
        drafts[n] = entry
    if not set(drafts).issubset(numbers):
        raise ValueError(f"Drafts have unknown hymn numbers: {set(drafts) - set(numbers)}")

    hymns = []
    for entry in seed:
        n = entry["number"]
        if not entry["title_yo"] or not entry["source_pages"]:
            raise ValueError(f"Missing title or photograph page reference: hymn {n}")
        draft = drafts.get(n)
        hymns.append({
            "number": n,
            "collection": entry["collection"],
            "title_yo": entry["title_yo"],
            "title_en": draft.get("title_en", entry["title_en"]) if draft else entry["title_en"],
            "source_pages": entry["source_pages"],
            "verses_yo": draft["verses"] if draft else [],
            "verses_en": draft.get("verses_en", []) if draft else [],
            "text_status": draft.get("status", "draft_photo_checked") if draft else "missing",
            "source_url": entry["external_source_url"],
        })
    # Public-domain English alternates are intentionally separate from the
    # photo-verified Yorùbá drafts. Never invent Yorùbá text or mark these
    # versions as the exact TAC wording before comparing printed verses.
    additional_path = SEED_DIR / "english-public-domain.json"
    if additional_path.exists():
        addendum = json.loads(additional_path.read_text(encoding="utf-8"))
        if addendum.get("collection") != "main":
            raise ValueError("English public-domain drafts must belong to Redemption")
        seen = {h["number"] for h in hymns}
        for item in addendum["hymns"]:
            number = item["number"]
            if number in seen or not (1 <= number <= 800):
                raise ValueError(f"Duplicate or invalid public-domain hymn {number}")
            if item["rights"] != "public_domain_historical_english_text":
                raise ValueError(f"Public-domain provenance missing for hymn {number}")
            if item["text_status"] != "public_domain_english_alternate_needs_edition_review":
                raise ValueError(f"Incorrect English-only draft status for hymn {number}")
            if not item["source_url"].startswith("https://"):
                raise ValueError(f"English text requires HTTPS source: hymn {number}")
            verses_en = item["verses_en"]
            if not verses_en or not all(
                isinstance(verse, list) and verse and
                all(isinstance(line, str) and line.strip() for line in verse)
                for verse in verses_en
            ):
                raise ValueError(f"English-only draft has invalid verses: {number}")
            hymns.append({
                "number": number,
                "collection": "main",
                "title_yo": item["title_en"],  # display fallback, not a Yorùbá translation
                "title_en": item["title_en"],
                "source_pages": [],
                "verses_yo": [],
                "verses_en": verses_en,
                "text_status": item["text_status"],
                "source_url": item["source_url"],
            })
            seen.add(number)
    hymns.sort(key=lambda entry: (entry["collection"], entry["number"]))
    return {
        "schema": 1,
        "description": "TAC photographed Yorùbá drafts and carefully separated public-domain English alternative texts. English-only texts remain pending TAC edition comparison.",
        "hymns": hymns,
    }


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    payload = compile_asset()
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(payload['hymns'])} hymns, " +
          f"{sum(bool(h['verses_yo']) for h in payload['hymns'])} with draft lyrics")


if __name__ == "__main__":
    main()
