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
import hashlib
import io
import wave
import re
from threading import Lock
from urllib.parse import urlsplit

PORT = 8765
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg=="
)
def make_pdf():
    """Generate a valid tiny PDF with xref offsets, not just a PDF-shaped header."""
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length 44 >>\nstream\nBT /F1 12 Tf 30 110 Td (NIGHT QA PDF) Tj ET\nendstream",
    ]
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0]
    for idx, obj in enumerate(objects, 1):
        offsets.append(len(out))
        out.extend(str(idx).encode() + b" 0 obj\n" + obj + b"\nendobj\n")
    startxref = len(out)
    out.extend(("xref\n0 " + str(len(offsets)) + "\n").encode())
    out.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        out.extend(("%010d 00000 n \n" % offset).encode())
    out.extend(("trailer\n<< /Size " + str(len(offsets)) +
                " /Root 1 0 R >>\nstartxref\n" + str(startxref) +
                "\n%%EOF\n").encode())
    return bytes(out)


PDF = make_pdf()
BIN = b"NIGHT-BROWSER-QA-DOWNLOAD-V1\n" * 256
def make_wav() -> bytes:
    """A real, license-free 0.1-second silent mono WAV for media downloads."""
    out = io.BytesIO()
    with wave.open(out, "wb") as writer:
        writer.setnchannels(1)
        writer.setsampwidth(2)
        writer.setframerate(8000)
        writer.writeframes(b"\\x00\\x00" * 800)
    return out.getvalue()


WAV = make_wav()
UPLOAD_BYTES = b"NIGHT_BROWSER_UPLOAD_TEST_V1_NO_PRIVATE_DATA\n" * 16

def uploaded_part_sha256(body: bytes, content_type: str) -> str | None:
    """Hash only the uploaded qa-file payload; never retain actual upload contents."""
    match = re.search(r'boundary=(?:"([^"]+)"|([^;\s]+))', content_type, re.I)
    if match is None:
        return None
    boundary = (match.group(1) or match.group(2)).encode("ascii", "strict")
    if not (1 <= len(boundary) <= 128) or b"\r" in boundary or b"\n" in boundary:
        return None
    marker = b"--" + boundary
    for part in body.split(marker):
        if b"\r\n\r\n" not in part:
            continue
        header, payload = part.split(b"\r\n\r\n", 1)
        if (b"content-disposition: form-data" not in header.lower() or
                re.search(rb'(?i)(?:^|;\s*)name="qa-file"(?:;|\s*$)', header) is None):
            continue
        if not payload.endswith(b"\r\n"):
            return None
        return hashlib.sha256(payload[:-2]).hexdigest()
    return None


def has_test_cookie(header: str) -> bool:
    # Do not count another cookie whose name merely ends with the marker.
    return re.search(r"(?:^|;\\s*)nightQa=fixture(?:;|$)", header) is not None


class State:
    def __init__(self):
        self.lock = Lock()
        self.count = Counter()
        self.cookie_observed = {}
        self.upload_length = 0
        self.upload_sha256 = None
        self.form_posted = False
        self.secure_download_ok = 0
        self.secure_download_denied = 0

    def record(self, path, cookie):
        with self.lock:
            self.count[path] += 1
            if path == "/cookie/echo":
                self.cookie_observed[self.count[path]] = has_test_cookie(cookie)

    def snapshot(self):
        with self.lock:
            return {"count": dict(self.count),
                    "cookie_seen": dict(self.cookie_observed),
                    "upload_length": self.upload_length,
                    "upload_sha256": self.upload_sha256,
                    "form_posted": self.form_posted,
                    "secure_download_ok": self.secure_download_ok,
                    "secure_download_denied": self.secure_download_denied}


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
        if path == "/media.wav":
            return self.send(WAV, "audio/wav", headers={
                "Content-Disposition": "attachment; filename=night-qa-audio.wav"})
        if path == "/file.bin":
            return self.send(BIN, "application/octet-stream", headers={
                "Content-Disposition": "attachment; filename=night-qa.bin"})
        if path == "/secure/file.bin":
            # Synthetic cookie-backed resource; no credentials or token values logged.
            authorized = has_test_cookie(self.headers.get("Cookie", ""))
            with self.state.lock:
                if authorized:
                    self.state.secure_download_ok += 1
                else:
                    self.state.secure_download_denied += 1
            if not authorized:
                return self.send("Forbidden", code=403)
            return self.send(BIN, "application/octet-stream", headers={
                "Content-Disposition": "attachment; filename=night-secure-qa.bin"})
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
                self.state.upload_sha256 = uploaded_part_sha256(
                    body, self.headers.get("Content-Type", ""))
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
