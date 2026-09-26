#!/usr/bin/env python3
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(os.environ.get("SORA_ENGINE_FIXTURE_ROOT", "/tmp/sora-engine-fixture"))
PORT = int(os.environ.get("SORA_ENGINE_FIXTURE_PORT", "8765"))

ANIME_ID = 20
MANGA_ID = 30013

def card(media_type: str):
    anime = media_type == "ANIME"
    return {
        "id": ANIME_ID if anime else MANGA_ID,
        "idMal": 20 if anime else 11,
        "type": media_type,
        "title": {
            "userPreferred": "Naruto",
            "romaji": "Naruto",
            "english": "Naruto",
            "native": "ナルト",
        },
        "synonyms": ["NARUTO"],
        "format": "TV" if anime else "MANGA",
        "status": "FINISHED",
        "averageScore": 79 if anime else 80,
        "episodes": 220 if anime else None,
        "chapters": None if anime else 700,
        "volumes": None if anime else 72,
        "coverImage": {},
    }

def details(media_type: str):
    anime = media_type == "ANIME"
    item = card(media_type)
    item.update({
        "description": "Deterministic Naruto catalog fixture for Sora media-engine CI.",
        "season": "FALL" if anime else None,
        "seasonYear": 2002 if anime else None,
        "startDate": {"year": 2002 if anime else 1999, "month": 10 if anime else 9, "day": 3 if anime else 21},
        "genres": ["Action", "Adventure"],
        "relations": {
            "edges": [{
                "relationType": "SOURCE" if anime else "ADAPTATION",
                "node": card("MANGA" if anime else "ANIME"),
            }],
        },
    })
    return item

def graphql_response(query: str):
    media_type = "MANGA" if "type: MANGA" in query else "ANIME"

    if "Media(" in query:
        return {"data": {"Media": details(media_type)}}

    if "search:" in query:
        return {"data": {"Page": {"media": [card(media_type)]}}}

    if "current: Page" in query:
        names = ["current", "popular", "top"]
        if media_type == "ANIME":
            names += ["season", "upcoming"]
        else:
            names += ["recent"]
        return {"data": {name: {"media": [card(media_type)]} for name in names}}

    return {"data": {"Page": {"media": [card(media_type)]}}}

class Handler(BaseHTTPRequestHandler):
    server_version = "SoraEngineFixture/1.0"

    def log_message(self, fmt, *args):
        print("[fixture]", fmt % args, flush=True)

    def do_POST(self):
        if self.path != "/graphql":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length)
        try:
            body = json.loads(raw.decode("utf-8"))
            query = body.get("query", "")
            payload = json.dumps(graphql_response(query)).encode("utf-8")
        except Exception as exc:
            payload = json.dumps({"errors": [{"message": str(exc)}]}).encode("utf-8")
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def do_HEAD(self):
        self._serve_asset(head_only=True)

    def do_GET(self):
        if self.path == "/health":
            payload = b"ok"
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        self._serve_asset(head_only=False)

    def _serve_asset(self, head_only: bool):
        if self.path.startswith("/media/video.mp4"):
            path = ROOT / "video.mp4"
            content_type = "video/mp4"
        elif self.path.startswith("/pages/"):
            path = ROOT / "page.jpg"
            content_type = "image/jpeg"
        else:
            self.send_error(404)
            return

        if not path.exists():
            self.send_error(404)
            return

        size = path.stat().st_size
        start, end = 0, size - 1
        range_header = self.headers.get("Range")

        if range_header:
            match = re.match(r"bytes=(\d*)-(\d*)", range_header)
            if match:
                if match.group(1):
                    start = int(match.group(1))
                if match.group(2):
                    end = int(match.group(2))
                end = min(end, size - 1)
                start = min(start, end)
                status = 206
            else:
                status = 200
        else:
            status = 200

        length = max(0, end - start + 1)
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()

        if head_only:
            return

        with path.open("rb") as fh:
            fh.seek(start)
            remaining = length
            while remaining > 0:
                chunk = fh.read(min(64 * 1024, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)

if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Sora engine fixture server on 0.0.0.0:{PORT}, root={ROOT}", flush=True)
    server.serve_forever()
