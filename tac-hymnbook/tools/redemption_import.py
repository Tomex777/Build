"""Validate a church-licensed bilingual TAC Redemption hymn batch for offline packaging.

This module does not scrape other hymn apps. Rights are checked in a documented
approval workflow: permission_reference must identify the signed licence or
public-domain determination and printed_edition_verified must be true.
The tool cannot itself establish the legal validity of a claimed permission.
"""
from __future__ import annotations

import argparse
import json
import unicodedata
from pathlib import Path


class RedemptionImportError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RedemptionImportError(message)


def normal_text(value: object, field: str) -> str:
    require(isinstance(value, str) and bool(value.strip()), f"{field} must be nonempty")
    clean = value.strip()
    require(clean == unicodedata.normalize("NFC", clean),
            f"{field} must use NFC Unicode (important for Yorùbá diacritics)")
    require(not any(ch in clean for ch in "\\r\\n\\x00"),
            f"{field} must not contain embedded line breaks")
    require(len(clean) < 1024, f"{field} is implausibly long")
    return clean


def validate_verses(value: object, field: str) -> list[list[str]]:
    require(isinstance(value, list) and 1 <= len(value) <= 25,
            f"{field} needs 1 to 25 stanzas")
    clean = []
    for stanza_index, stanza in enumerate(value):
        require(isinstance(stanza, list) and 1 <= len(stanza) <= 30,
                f"{field} stanza {stanza_index+1} needs 1 to 30 lines")
        clean.append([
            normal_text(line, f"{field} stanza {stanza_index+1} line {i+1}")
            for i, line in enumerate(stanza)
        ])
    return clean


def validate_batch(payload: dict, known_numbers: set[int] | None = None) -> list[dict]:
    require(isinstance(payload, dict), "Expected JSON object")
    require(payload.get("schema_version") == 1, "Unsupported import schema")
    require(payload.get("collection") == "main", "Àkọkún must not use the Redemption import")
    hymns = payload.get("hymns")
    require(isinstance(hymns, list), "hymns must be an array")
    seen: set[int] = set()
    results = []
    for entry in hymns:
        require(isinstance(entry, dict), "Every hymn needs an object")
        n = entry.get("number")
        require(type(n) is int and 1 <= n <= 800, "Redemption number must be 1..800")
        require(n not in seen, f"Duplicate hymn number {n}")
        seen.add(n)
        require(entry.get("rights_verified") is True,
                f"Hymn {n} lacks checked distribution rights")
        require(entry.get("printed_edition_verified") is True,
                f"Hymn {n} has not been checked against the TAC printed edition")
        reviewer = normal_text(entry.get("reviewer"), f"Hymn {n} reviewer")
        permission = normal_text(entry.get("permission_reference"), f"Hymn {n} permission_reference")
        source_url = entry.get("source_url")
        if source_url is not None:
            source_url = normal_text(source_url, f"Hymn {n} source_url")
            require(source_url.startswith("https://"), "Sources must use HTTPS")
        pages = entry.get("source_pages", [])
        require(isinstance(pages, list) and all(type(p) is int and 1 <= p <= 9999 for p in pages),
                f"Hymn {n} has invalid printed source pages")
        require(bool(pages) or bool(source_url),
                f"Hymn {n} requires numbered printed pages or an authenticated source URL")
        if known_numbers is not None and n in known_numbers:
            require(entry.get("replaces_working_draft") is True,
                    f"Replacing existing hymn {n} requires explicit reviewed override")
        verses_yo = validate_verses(entry.get("verses_yo"), f"Hymn {n} Yorùbá")
        verses_en = validate_verses(entry.get("verses_en"), f"Hymn {n} English")
        results.append({
            "number": n,
            "collection": "main",
            "title_yo": normal_text(entry.get("title_yo"), f"Hymn {n} Yorùbá title"),
            "title_en": normal_text(entry.get("title_en"), f"Hymn {n} English title"),
            "verses_yo": verses_yo,
            "verses_en": verses_en,
            "source_pages": sorted(set(pages)),
            "source_url": source_url,
            "text_status": "licensed_bilingual_print_verified",
            "rights_verified": True,
            "printed_edition_verified": True,
            "replaces_working_draft": entry.get("replaces_working_draft") is True,
            "permission_reference": permission,
            "reviewer": reviewer,
        })
    return results


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate rights-cleared TAC Redemption hymn imports")
    parser.add_argument("input", type=Path, help="Licensed bilingual JSON batch")
    parser.add_argument("--output", type=Path, required=True,
                        help="Destination approved-redemption.json")
    parser.add_argument("--dry-run", action="store_true",
                        help="Only validate; do not write any assets")
    args = parser.parse_args()
    payload = json.loads(args.input.read_text(encoding="utf-8"))
    entries = validate_batch(payload)
    print(f"Validated {len(entries)} rights-reviewed bilingual hymns")
    if not args.dry_run:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps({"schema_version": 1, "collection": "main", "hymns": entries},
                       ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"Approved import records written to {args.output}")


if __name__ == "__main__":
    main()
