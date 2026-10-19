#!/usr/bin/env python3
"""Use Hymnary's explicitly published 'Export as CSV' control for RH2006.

Run once against the site's advertised CSV endpoint and respect access denials.

This exports instance *metadata*, not lyrics. No fallback to protected
HTML, no retry loops, and never generate made-up entries on failure.
"""
from __future__ import annotations
import csv
import io
from pathlib import Path
from urllib.parse import urlencode
import requests

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "data" / "redemption-hymnary-export.csv"
BASE = "https://hymnary.org/instances"
PARAMS = {
    "export": "csv",
    "limit": "879",
    "order": "asc",
    "qu": "hymnalNumber:rh2006 in:instances",
    "sort": "firstLine",
}


def main() -> None:
    response = requests.get(
        BASE,
        params=PARAMS,
        headers={"User-Agent": "TAC-Hymnal-Research/1.0 (+https://github.com/Tomex777/Build)"},
        timeout=45,
    )
    if response.status_code == 403:
        raise SystemExit("Hymnary declined automated access to its official CSV export (403). Stop; request a voluntary CSV upload.")
    response.raise_for_status()
    content_type = response.headers.get("Content-Type", "")
    if "text/csv" not in content_type and "application/octet-stream" not in content_type:
        raise SystemExit(f"Unexpected non-CSV content type: {content_type}")
    rows = list(csv.DictReader(io.StringIO(response.text.lstrip("\ufeff"))))
    print(f"CSV fields: {list(rows[0]) if rows else []}", flush=True)
    print(f"Exported instance rows: {len(rows)}", flush=True)
    if len(rows) < 800:
        raise SystemExit("Incomplete CSV export; do not trust as full index.")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(response.text, encoding="utf-8")
    print(f"Authorized export saved to {OUTPUT}")


if __name__ == "__main__":
    main()
