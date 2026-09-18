#!/usr/bin/env python3
from __future__ import annotations

import html
import json
import os
import re
import socket
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any
from urllib.parse import quote, urlparse


OUT_DIR = Path("flaresolverr-tor-probe")
OUT_DIR.mkdir(exist_ok=True)
REPORT_PATH = OUT_DIR / "endpoint-report.json"

FLARESOLVERR_URL = os.getenv("FLARESOLVERR_URL", "http://127.0.0.1:8191/v1")
TOR_PROXY = os.getenv("TOR_PROXY", "socks5://127.0.0.1:9050")
ATTEMPTS = int(os.getenv("FLARESOLVERR_ATTEMPTS", "2"))
SEARCH_QUERY = os.getenv("ANIMEPAHE_SEARCH_QUERY", "bleach")
CANONICAL_HOST = "animepahe.pw"

CHALLENGE_MARKERS = (
    "cf-chl-",
    "challenge-platform",
    "just a moment",
    "attention required",
    "performing security verification",
    "you have been blocked",
)
KWIK_RE = re.compile(r"https?://kwik\.[^\"'\s<>]+", re.I)


def compact_text(value: str, limit: int = 320) -> str:
    return re.sub(r"\s+", " ", value).strip()[:limit]


def api_call(payload: dict[str, Any], timeout: int = 150) -> dict[str, Any]:
    request = urllib.request.Request(
        FLARESOLVERR_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"FlareSolverr HTTP {exc.code}: {compact_text(body)}") from exc
    return json.loads(body)


def browser_get(session: str, url: str, max_timeout_ms: int = 90_000) -> dict[str, Any]:
    started = time.monotonic()
    try:
        data = api_call(
            {
                "cmd": "request.get",
                "url": url,
                "session": session,
                "maxTimeout": max_timeout_ms,
                "waitInSeconds": 2,
                "disableMedia": True,
            },
            timeout=max_timeout_ms // 1000 + 35,
        )
        solution = data.get("solution") or {}
        response_text = str(solution.get("response") or "")
        status = solution.get("status")
        low = response_text.lower()
        if data.get("status") != "ok":
            classification = "flaresolverr_error"
        elif any(marker in low for marker in CHALLENGE_MARKERS):
            classification = "cloudflare_challenge"
        elif status in (403, 429, 503):
            classification = "blocked"
        elif parse_json_document(response_text) is not None:
            classification = "json"
        elif status == 200:
            classification = "html"
        else:
            classification = "unexpected"
        headers = solution.get("headers") or {}
        content_type = ""
        if isinstance(headers, dict):
            content_type = str(headers.get("content-type") or headers.get("Content-Type") or "")
        return {
            "requested_url": url,
            "final_url": solution.get("url"),
            "status": status,
            "content_type": content_type,
            "classification": classification,
            "message": data.get("message"),
            "preview": compact_text(response_text),
            "elapsed_seconds": round(time.monotonic() - started, 2),
            "response": response_text,
        }
    except Exception as exc:
        return {
            "requested_url": url,
            "final_url": None,
            "status": None,
            "content_type": "",
            "classification": "request_error",
            "message": str(exc),
            "preview": "",
            "elapsed_seconds": round(time.monotonic() - started, 2),
            "response": "",
        }


def parse_json_document(text: str) -> Any | None:
    candidates = [text.strip()]
    pre = re.search(r"<pre[^>]*>(.*?)</pre>", text, re.I | re.S)
    if pre:
        candidates.append(html.unescape(re.sub(r"<[^>]+>", "", pre.group(1))).strip())
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        candidates.append(html.unescape(text[start : end + 1]))
    for candidate in candidates:
        try:
            return json.loads(candidate)
        except (json.JSONDecodeError, TypeError):
            pass
    return None


def public_result(result: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in result.items() if key != "response"}


def create_session(session: str) -> dict[str, Any]:
    return api_call(
        {
            "cmd": "sessions.create",
            "session": session,
            "proxy": {"url": TOR_PROXY},
        },
        timeout=60,
    )


def destroy_session(session: str) -> None:
    try:
        api_call({"cmd": "sessions.destroy", "session": session}, timeout=30)
    except Exception:
        pass


def new_tor_identity() -> None:
    try:
        with socket.create_connection(("127.0.0.1", 9051), timeout=5) as control:
            control.sendall(b"AUTHENTICATE\r\nSIGNAL NEWNYM\r\nQUIT\r\n")
            control.recv(4096)
        time.sleep(10)
    except Exception as exc:
        print(f"Tor NEWNYM warning: {exc}", flush=True)


def first_search_item(document: Any) -> dict[str, Any] | None:
    if not isinstance(document, dict):
        return None
    rows = document.get("data")
    if not isinstance(rows, list):
        return None
    return next(
        (
            item
            for item in rows
            if isinstance(item, dict) and item.get("session") and item.get("title")
        ),
        None,
    )


def first_episode(document: Any) -> dict[str, Any] | None:
    if not isinstance(document, dict):
        return None
    rows = document.get("data")
    if not isinstance(rows, list):
        return None
    return next(
        (item for item in rows if isinstance(item, dict) and item.get("session")),
        None,
    )


def run_attempt(number: int) -> dict[str, Any]:
    session = f"pahe-tor-{number}-{int(time.time())}"
    attempt: dict[str, Any] = {"attempt": number, "session_created": False, "steps": {}}
    try:
        created = create_session(session)
        attempt["session_created"] = created.get("status") == "ok"
        attempt["session_message"] = created.get("message")
        if not attempt["session_created"]:
            return attempt

        tor = browser_get(session, "https://check.torproject.org/api/ip", 60_000)
        tor_doc = parse_json_document(tor["response"])
        attempt["tor"] = public_result(tor)
        attempt["tor_confirmed"] = bool(isinstance(tor_doc, dict) and tor_doc.get("IsTor") is True)
        attempt["tor_ip"] = tor_doc.get("IP") if isinstance(tor_doc, dict) else None
        print(
            f"[attempt {number}] Tor confirmed={attempt['tor_confirmed']} ip={attempt['tor_ip']}",
            flush=True,
        )
        if not attempt["tor_confirmed"]:
            return attempt

        root = browser_get(session, f"https://{CANONICAL_HOST}/")
        attempt["steps"]["root"] = public_result(root)
        print(
            f"  root {root['status']} {root['classification']} -> {root['final_url']}",
            flush=True,
        )
        if root["classification"] == "cloudflare_challenge":
            return attempt

        final_host = urlparse(str(root.get("final_url") or "")).hostname or CANONICAL_HOST
        attempt["canonical_host"] = final_host.lower()

        search_url = f"https://{final_host}/api?m=search&q={quote(SEARCH_QUERY)}"
        search = browser_get(session, search_url)
        search_doc = parse_json_document(search["response"])
        attempt["steps"]["search"] = public_result(search)
        attempt["search_endpoint"] = "/api?m=search&q=<query>"
        item = first_search_item(search_doc)
        if not item:
            return attempt

        anime_session = str(item["session"])
        attempt["search_result"] = {
            "title": item.get("title"),
            "session": anime_session,
            "numeric_id": item.get("id"),
        }

        release_urls = {
            "release_modern": (
                f"https://{final_host}/api?m=release&id={quote(anime_session)}"
                "&sort=episode_asc&page=1"
            ),
            "release_legacy": (
                f"https://{final_host}/api/{quote(anime_session)}/releases"
                "?sort=episode_asc&page=1"
            ),
        }
        release_doc = None
        for name, url in release_urls.items():
            result = browser_get(session, url)
            document = parse_json_document(result["response"])
            attempt["steps"][name] = public_result(result)
            if first_episode(document) and release_doc is None:
                release_doc = document
                attempt["working_release_endpoint"] = (
                    "/api?m=release&id=<session>&sort=episode_asc&page=<page>"
                    if name == "release_modern"
                    else "/api/<session>/releases?sort=episode_asc&page=<page>"
                )

        episode = first_episode(release_doc)
        if not episode:
            return attempt

        episode_session = str(episode["session"])
        play_url = f"https://{final_host}/play/{anime_session}/{episode_session}"
        play = browser_get(session, play_url)
        attempt["steps"]["play"] = public_result(play)
        attempt["play_endpoint"] = "/play/<anime-session>/<episode-session>"
        attempt["kwik_urls"] = sorted(set(KWIK_RE.findall(play["response"])))[:10]
        attempt["fully_verified"] = bool(
            search["classification"] == "json"
            and release_doc is not None
            and play["status"] == 200
            and attempt["kwik_urls"]
        )
        return attempt
    finally:
        destroy_session(session)


def main() -> int:
    report: dict[str, Any] = {
        "configuration": {
            "canonical_candidate": CANONICAL_HOST,
            "search_endpoint": "/api?m=search&q=<query>",
            "release_endpoint": "/api?m=release&id=<session>&sort=episode_asc&page=<page>",
            "legacy_release_endpoint": "/api/<session>/releases?sort=episode_asc&page=<page>",
            "play_endpoint": "/play/<anime-session>/<episode-session>",
            "tor_is_ci_only": True,
        },
        "attempts": [],
    }

    for number in range(1, ATTEMPTS + 1):
        attempt = run_attempt(number)
        report["attempts"].append(attempt)
        REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
        if attempt.get("fully_verified"):
            break
        if number < ATTEMPTS:
            new_tor_identity()

    fully_verified = next(
        (attempt for attempt in report["attempts"] if attempt.get("fully_verified")),
        None,
    )
    tor_confirmed = any(attempt.get("tor_confirmed") for attempt in report["attempts"])
    challenge_seen = any(
        step.get("classification") == "cloudflare_challenge"
        for attempt in report["attempts"]
        for step in attempt.get("steps", {}).values()
    )
    report["summary"] = {
        "tor_confirmed": tor_confirmed,
        "fully_verified": bool(fully_verified),
        "canonical_host": fully_verified.get("canonical_host") if fully_verified else None,
        "working_release_endpoint": fully_verified.get("working_release_endpoint") if fully_verified else None,
        "kwik_reached": bool(fully_verified and fully_verified.get("kwik_urls")),
        "verdict": (
            "endpoints_verified_live_through_flaresolverr_over_tor"
            if fully_verified
            else "cloudflare_still_blocked_flaresolverr_over_tor"
            if tor_confirmed and challenge_seen
            else "probe_inconclusive"
        ),
    }
    REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"], indent=2), flush=True)

    if not tor_confirmed:
        print("Tor was not confirmed inside the FlareSolverr browser.", flush=True)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
