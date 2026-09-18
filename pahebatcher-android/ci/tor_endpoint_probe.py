#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import sys
import time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from curl_cffi import requests

OUT_DIR = Path("tor-probe")
OUT_DIR.mkdir(exist_ok=True)
REPORT_PATH = OUT_DIR / "endpoint-report.json"

OFFICIAL_HOSTS = ("animepahe.pw", "animepahe.com", "animepahe.org")
SEARCH_QUERY = "bleach"
MAX_CIRCUITS = int(os.getenv("TOR_PROBE_CIRCUITS", "10"))
TIMEOUT = 20

CF_MARKERS = (
    "cf-chl-",
    "challenge-platform",
    "just a moment",
    "attention required",
    "cloudflare",
)

KWIK_RE = re.compile(r"""https?://kwik\.[^"'\s<>]+""", re.I)


@dataclass
class ProbeResult:
    circuit: int
    exit_ip: str | None
    host: str
    step: str
    url: str
    status: int | None
    content_type: str | None
    classification: str
    final_url: str | None
    preview: str
    error: str | None = None


def preview_text(text: str, limit: int = 260) -> str:
    return re.sub(r"\s+", " ", text).strip()[:limit]


def classify(status: int | None, text: str, content_type: str | None) -> str:
    low = text.lower()
    if status is None:
        return "network_error"
    if status == 429:
        return "rate_limited"
    if status in (404, 410):
        return "not_found"
    if any(marker in low for marker in CF_MARKERS) and status in (200, 403, 429, 503):
        return "cloudflare_challenge"
    if status in (403, 503):
        return "blocked"
    if 200 <= status < 300:
        if content_type and "json" in content_type.lower():
            try:
                obj = json.loads(text)
                if isinstance(obj, dict) and "data" in obj:
                    return "json_data"
                return "json"
            except Exception:
                return "invalid_json"
        if text.lstrip().startswith(("{", "[")):
            try:
                obj = json.loads(text)
                if isinstance(obj, dict) and "data" in obj:
                    return "json_data"
                return "json"
            except Exception:
                pass
        return "html_or_text"
    if 300 <= status < 400:
        return "redirect"
    return f"http_{status}"


def tor_proxy(circuit: int) -> str:
    # Tor is configured with IsolateSOCKSAuth, so each username gets a separate circuit.
    return f"socks5h://pahe-probe-{circuit}:x@127.0.0.1:9050"


def new_session(circuit: int) -> requests.Session:
    proxy = tor_proxy(circuit)
    s = requests.Session(
        impersonate="chrome",
        proxies={"http": proxy, "https": proxy},
        timeout=TIMEOUT,
    )
    s.headers.update(
        {
            "Accept-Language": "en-US,en;q=0.9",
            "Cache-Control": "no-cache",
            "Pragma": "no-cache",
        }
    )
    return s


def request(
    session: requests.Session,
    circuit: int,
    exit_ip: str | None,
    host: str,
    step: str,
    url: str,
    *,
    referer: str | None = None,
    accept: str | None = None,
) -> tuple[ProbeResult, str]:
    headers: dict[str, str] = {}
    if referer:
        headers["Referer"] = referer
    if accept:
        headers["Accept"] = accept
    try:
        resp = session.get(url, headers=headers, allow_redirects=True)
        text = resp.text or ""
        ctype = resp.headers.get("content-type")
        result = ProbeResult(
            circuit=circuit,
            exit_ip=exit_ip,
            host=host,
            step=step,
            url=url,
            status=resp.status_code,
            content_type=ctype,
            classification=classify(resp.status_code, text, ctype),
            final_url=str(resp.url),
            preview=preview_text(text),
        )
        return result, text
    except Exception as exc:
        return (
            ProbeResult(
                circuit=circuit,
                exit_ip=exit_ip,
                host=host,
                step=step,
                url=url,
                status=None,
                content_type=None,
                classification="network_error",
                final_url=None,
                preview="",
                error=f"{type(exc).__name__}: {exc}",
            ),
            "",
        )


def get_exit_ip(session: requests.Session) -> tuple[str | None, dict[str, Any] | None]:
    try:
        resp = session.get("https://check.torproject.org/api/ip")
        data = resp.json()
        return data.get("IP"), data
    except Exception:
        return None, None


def find_bleach(payload: dict[str, Any]) -> dict[str, Any] | None:
    rows = payload.get("data")
    if not isinstance(rows, list):
        return None
    exact = next(
        (row for row in rows if isinstance(row, dict) and str(row.get("title", "")).strip().lower() == "bleach"),
        None,
    )
    return exact or next((row for row in rows if isinstance(row, dict)), None)


def extract_kwik(html: str) -> str | None:
    m = KWIK_RE.search(html)
    if not m:
        return None
    return m.group(0).replace("&amp;", "&")


results: list[ProbeResult] = []
summary: dict[str, Any] = {
    "tor_confirmed": False,
    "successful_circuit": None,
    "successful_exit_ip": None,
    "successful_host": None,
    "search_endpoint": "/api?m=search&q=<query>",
    "release_endpoint": "/api?m=release&id=<session>&sort=episode_asc&page=<page>",
    "legacy_release_endpoint": "/api/<session>/releases?sort=episode_asc&page=<page>",
    "play_endpoint": "/play/<anime-session>/<episode-session>",
    "kwik_reached": False,
}

for circuit in range(1, MAX_CIRCUITS + 1):
    session = new_session(circuit)
    exit_ip, tor_info = get_exit_ip(session)
    is_tor = bool(tor_info and tor_info.get("IsTor") is True)
    summary["tor_confirmed"] = summary["tor_confirmed"] or is_tor
    print(f"[circuit {circuit}] tor={is_tor} exit={exit_ip}")

    for host in OFFICIAL_HOSTS:
        base = f"https://{host}/"

        root_result, _ = request(
            session,
            circuit,
            exit_ip,
            host,
            "root",
            base,
            accept="text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
        results.append(root_result)
        print(f"  {host} root -> {root_result.status} {root_result.classification}")

        search_url = f"https://{host}/api?m=search&q={SEARCH_QUERY}"
        search_result, search_body = request(
            session,
            circuit,
            exit_ip,
            host,
            "search",
            search_url,
            referer=base,
            accept="application/json,text/plain,*/*",
        )
        results.append(search_result)
        print(f"  {host} search -> {search_result.status} {search_result.classification}")

        if search_result.classification not in ("json", "json_data"):
            continue

        try:
            search_json = json.loads(search_body)
        except Exception:
            continue
        anime = find_bleach(search_json)
        if not anime:
            continue

        anime_session = str(anime.get("session") or "")
        anime_id = anime.get("id")
        if not anime_session:
            continue

        summary.update(
            {
                "successful_circuit": circuit,
                "successful_exit_ip": exit_ip,
                "successful_host": host,
                "anime_id": anime_id,
                "anime_session": anime_session,
            }
        )

        release_url = (
            f"https://{host}/api?m=release&id={anime_session}"
            f"&sort=episode_asc&page=1"
        )
        release_result, release_body = request(
            session,
            circuit,
            exit_ip,
            host,
            "release",
            release_url,
            referer=base,
            accept="application/json,text/plain,*/*",
        )
        results.append(release_result)
        print(f"  {host} release -> {release_result.status} {release_result.classification}")

        legacy_url = f"https://{host}/api/{anime_session}/releases?sort=episode_asc&page=1"
        legacy_result, _ = request(
            session,
            circuit,
            exit_ip,
            host,
            "legacy_release",
            legacy_url,
            referer=base,
            accept="application/json,text/plain,*/*",
        )
        results.append(legacy_result)
        print(f"  {host} legacy release -> {legacy_result.status} {legacy_result.classification}")

        if anime_id:
            id_release_url = (
                f"https://{host}/api?m=release&id={anime_id}"
                f"&sort=episode_asc&page=1"
            )
            id_result, _ = request(
                session,
                circuit,
                exit_ip,
                host,
                "release_using_stable_anime_id",
                id_release_url,
                referer=base,
                accept="application/json,text/plain,*/*",
            )
            results.append(id_result)
            print(f"  {host} stable-id release -> {id_result.status} {id_result.classification}")

        if release_result.classification not in ("json", "json_data"):
            break

        try:
            release_json = json.loads(release_body)
        except Exception:
            break
        episodes = release_json.get("data")
        if not isinstance(episodes, list) or not episodes:
            break
        episode = next((row for row in episodes if isinstance(row, dict) and row.get("session")), None)
        if not episode:
            break
        episode_session = str(episode["session"])
        summary["episode_session"] = episode_session

        play_url = f"https://{host}/play/{anime_session}/{episode_session}"
        play_result, play_body = request(
            session,
            circuit,
            exit_ip,
            host,
            "play",
            play_url,
            referer=base,
            accept="text/html,application/xhtml+xml,*/*",
        )
        results.append(play_result)
        print(f"  {host} play -> {play_result.status} {play_result.classification}")

        kwik_url = extract_kwik(play_body)
        if kwik_url:
            summary["kwik_url"] = kwik_url
            kwik_host = re.sub(r"^www\.", "", urlparse(kwik_url).hostname or "kwik")
            kwik_result, _ = request(
                session,
                circuit,
                exit_ip,
                kwik_host,
                "kwik",
                kwik_url,
                referer=base,
                accept="text/html,application/xhtml+xml,*/*",
            )
            results.append(kwik_result)
            summary["kwik_reached"] = kwik_result.status is not None
            print(f"  {kwik_host} kwik -> {kwik_result.status} {kwik_result.classification}")

        # We reached the live API chain. No need to burn more Tor circuits.
        break

    if summary["successful_host"]:
        break

report = {
    "summary": summary,
    "results": [asdict(item) for item in results],
}
REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")

print("\n=== TOR ENDPOINT SUMMARY ===")
print(json.dumps(summary, indent=2))

if not summary["tor_confirmed"]:
    print("Tor itself was not confirmed.", file=sys.stderr)
    raise SystemExit(2)

# Endpoint probe is diagnostic: Cloudflare may challenge every exit. We still succeed
# so CI can continue to the emulator and upload the classification report.
raise SystemExit(0)
