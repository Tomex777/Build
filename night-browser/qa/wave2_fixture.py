#!/usr/bin/env python3
"""Deterministic, self-contained public-content pages for on-device browser QA.

No real credentials, browsing history, uploaded bytes or cookie values are saved.
Run ONLY against a disposable emulator profile. Fixture listens on the CI host;
Android emulator uses http://10.0.2.2:8765.
"""
import base64
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from threading import Lock
from urllib.parse import urlsplit

PORT = 8765
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg=="
)
PDF = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
       b"2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n"
       b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 150 150]>>endobj\n"
       b"trailer<</Root 1 0 R>>\n%%EOF\n")
BIN = b"NIGHT-BROWSER-QA-DOWNLOAD-V1\n" * 256


class State:
    def __init__(self):
        self.lock = Lock()
        self.count = Counter()
        self.cookie_observed = {}
        self.upload_length = 0
        self.form_posted = False

    def record(self, path, cookie):
        with self.lock:
            self.count[path] += 1
            if path == "/cookie/echo":
                self.cookie_observed[self.count[path]] = "nightQa=fixture" in cookie

    def snapshot(self):
        with self.lock:
            return {"count": dict(self.count),
                    "cookie_seen": dict(self.cookie_observed),
                    "upload_length": self.upload_length,
                    "form_posted": self.form_posted}


class Handler(BaseHTTPRequestHandler):
    state = State()

    def log_message(self, format, *args):
        # URLs and request bodies are intentionally NOT printed in CI logs.
        pass

    def send(self, payload, content_type="text/html; charset=utf-8", code=200, headers=None):
        if isinstance(payload, str):
            payload = payload.encode()
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        path = urlsplit(self.path).path
        self.state.record(path, self.headers.get("Cookie", ""))
        if path == "/__qa/status":
            return self.send(json.dumps(self.state.snapshot()), "application/json")
        if path == "/redirect":
            return self.send(b"", code=302, headers={"Location": "/landing"})
        if path == "/cookie/set":
            return self.send(self.page("COOKIE SET"), headers={
                "Set-Cookie": "nightQa=fixture; Path=/; SameSite=Lax"})
        if path == "/cookie/echo":
            return self.send(self.page("COOKIE READ"))
        if path == "/file.png":
            return self.send(PNG, "image/png", headers={
                "Content-Disposition": "attachment; filename=night-qa.png"})
        if path == "/file.pdf":
            return self.send(PDF, "application/pdf", headers={
                "Content-Disposition": "attachment; filename=night-qa.pdf"})
        if path == "/file.bin":
            return self.send(BIN, "application/octet-stream", headers={
                "Content-Disposition": "attachment; filename=night-qa.bin"})
        if path == "/not-found":
            return self.send(self.page("404"), code=404)
        if path == "/server-error":
            return self.send(self.page("500"), code=500)
        if path == "/form":
            return self.send(self.page("FORM", '<form action="/form/submit" method="post">'
                '<input name="qa" value="testonly"><button type="submit">Send test form</button></form>'))
        if path == "/upload":
            return self.send(self.page("UPLOAD", '<form action="/upload/submit" method="post" enctype="multipart/form-data">'
                '<input type="file" name="qa-file"><button type="submit">Upload test file</button></form>'))
        if path == "/links":
            return self.send(self.page("LINKS", '<a href="/branch/a">Visit A</a>'
                '<a href="/branch/b">Visit B</a><a href="/redirect">Redirect</a>'))
        if path == "/branch/a":
            return self.send(self.page("BRANCH A", '<a href="/branch/c">Visit C</a>'))
        if path == "/branch/b":
            return self.send(self.page("BRANCH B", '<a href="/branch/d">Visit D</a>'))
        if path == "/branch/c":
            return self.send(self.page("BRANCH C"))
        if path == "/branch/d":
            return self.send(self.page("BRANCH D"))
        return self.send(self.page({
            "/": "START", "/landing": "REDIRECT LANDING",
            "/form/submit": "FORM RECEIVED", "/upload/submit": "UPLOAD RECEIVED"
        }.get(path, "UNKNOWN PAGE")))

    def do_POST(self):
        path = urlsplit(self.path).path
        if path not in ("/form/submit", "/upload/submit"):
            return self.send("Unsupported", code=404)
        size = int(self.headers.get("Content-Length", "0"))
        if not (0 < size <= 3_000_000):
            return self.send("Invalid test length", code=400)
        body = self.rfile.read(size)
        with self.state.lock:
            self.state.count[path] += 1
            if path == "/form/submit":
                self.state.form_posted = True
            else:
                self.state.upload_length = len(body)
        return self.send(self.page("POST COMPLETE"))

    @staticmethod
    def page(title, body=""):
        return ('<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1">'
                '<title>NIGHT QA ' + title + '</title><style>body{font:20px system-ui;margin:1.5em}'
                'a,button,input{display:block;margin:1em 0;padding:.6em}</style></head>'
                '<body><h1>NIGHT QA ' + title + '</h1>' + body + '</body></html>')


def serve():
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()


if __name__ == "__main__":
    serve()
