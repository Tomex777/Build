#!/usr/bin/env python3
"""Deterministic WEB chapter endpoint for the Android Bible reader CI flow."""
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote_plus, urlsplit

HOST = "0.0.0.0"
PORT = 8765

CHAPTERS = {
    1: [(1, "In the beginning God created the heavens and the earth.")],
    2: [(1, "The heavens and the earth were finished, and all their vast array.")],
}
for chapter in CHAPTERS:
    CHAPTERS[chapter].extend(
        (number, f"Genesis {chapter} fixture verse {number} keeps the reader list long enough to scroll and verify chapter navigation.")
        for number in range(2, 36)
    )


def response(reference: str) -> dict:
    match = re.fullmatch(r"Genesis\s+([12])", reference, flags=re.IGNORECASE)
    if not match:
        return {"error": f"Fixture has no passage for {reference}."}
    chapter = int(match.group(1))
    return {
        "reference": f"Genesis {chapter}",
        "translation_name": "World English Bible",
        "translation_id": "web",
        "verses": [
            {
                "book_name": "Genesis",
                "chapter": chapter,
                "verse": number,
                "text": text,
            }
            for number, text in CHAPTERS[chapter]
        ],
    }


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[bible-fixture] " + fmt % args, flush=True)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/health":
            self.send_json({"status": "ok"})
            return
        if path == "/data":
            self.send_json({"translations": [{
                "identifier": "web",
                "name": "World English Bible",
                "language": "English",
                "language_code": "eng",
            }]})
            return
        reference = unquote_plus(path.lstrip("/"))
        self.send_json(response(reference))

    def send_json(self, value):
        body = json.dumps(value).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
