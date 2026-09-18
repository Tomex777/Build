#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import socket
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By

OUT = Path("tor-browser-probe")
OUT.mkdir(exist_ok=True)
HOSTS = ("animepahe.pw", "animepahe.com", "animepahe.org")
ATTEMPTS = 3

CF_MARKERS = (
    "just a moment",
    "cf-chl-",
    "challenge-platform",
    "attention required",
    "cloudflare",
)

KWIK_RE = re.compile(r"""https?://kwik\.[^"'\s<>]+""", re.I)


def newnym() -> None:
    try:
        with socket.create_connection(("127.0.0.1", 9051), timeout=5) as sock:
            sock.sendall(b"AUTHENTICATE\r\nSIGNAL NEWNYM\r\nQUIT\r\n")
            sock.recv(4096)
    except Exception as exc:
        print(f"NEWNYM warning: {exc}")


def browser() -> webdriver.Chrome:
    opts = Options()
    opts.add_argument("--headless=new")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    opts.add_argument("--disable-gpu")
    opts.add_argument("--window-size=1280,1800")
    opts.add_argument("--proxy-server=socks5://127.0.0.1:9050")
    opts.add_argument("--lang=en-US")
    opts.add_argument("--disable-background-networking")
    opts.add_argument("--disable-default-apps")
    opts.add_argument("--disable-sync")
    opts.page_load_strategy = "eager"
    driver = webdriver.Chrome(options=opts)
    driver.set_page_load_timeout(30)
    driver.set_script_timeout(20)
    return driver


def body_text(driver: webdriver.Chrome) -> str:
    try:
        return driver.find_element(By.TAG_NAME, "body").text
    except Exception:
        return driver.page_source


def classify(driver: webdriver.Chrome) -> str:
    text = (body_text(driver) + " " + driver.page_source).lower()
    if any(marker in text for marker in CF_MARKERS):
        return "cloudflare_challenge"
    if "animepahe" in text:
        return "animepahe_page"
    if text.strip().startswith("{") or '"data"' in text:
        return "json_like"
    return "other"


def save_screen(driver: webdriver.Chrome, name: str) -> None:
    try:
        driver.save_screenshot(str(OUT / f"{name}.png"))
    except Exception:
        pass


def parse_json_text(text: str) -> dict[str, Any] | None:
    candidates = [text]
    m = re.search(r"(\{.*\})", text, re.S)
    if m:
        candidates.append(m.group(1))
    for candidate in candidates:
        try:
            obj = json.loads(candidate)
            if isinstance(obj, dict):
                return obj
        except Exception:
            pass
    return None


report: dict[str, Any] = {"attempts": [], "success": False, "tor_confirmed": False}

for attempt in range(1, ATTEMPTS + 1):
    newnym()
    time.sleep(4)
    driver = browser()
    driver.set_page_load_timeout(45)
    attempt_row: dict[str, Any] = {"attempt": attempt, "hosts": []}
    report["attempts"].append(attempt_row)

    try:
        driver.get("https://check.torproject.org/api/ip")
        time.sleep(2)
        tor_text = body_text(driver)
        attempt_row["tor_check"] = tor_text[:500]
        tor_data = parse_json_text(tor_text) or {}
        attempt_row["tor_confirmed"] = tor_data.get("IsTor") is True
        attempt_row["tor_ip"] = tor_data.get("IP")
        print(f"[browser attempt {attempt}] Tor check: {tor_text[:180]}")
        report["tor_confirmed"] = report["tor_confirmed"] or bool(attempt_row["tor_confirmed"])

        for host in HOSTS:
            row: dict[str, Any] = {"host": host}
            attempt_row["hosts"].append(row)
            base = f"https://{host}/"

            driver.get(base)
            time.sleep(8)
            row["root_title"] = driver.title
            row["root_url"] = driver.current_url
            row["root_classification"] = classify(driver)
            row["root_preview"] = re.sub(r"\s+", " ", body_text(driver))[:400]
            save_screen(driver, f"attempt-{attempt}-{host}-root")
            print(f"  {host} root -> {row['root_classification']} title={driver.title!r}")

            if row["root_classification"] == "cloudflare_challenge":
                continue

            search_url = f"https://{host}/api?m=search&q=bleach"
            driver.get(search_url)
            time.sleep(5)
            search_text = body_text(driver)
            row["search_url"] = driver.current_url
            row["search_classification"] = classify(driver)
            row["search_preview"] = re.sub(r"\s+", " ", search_text)[:400]
            save_screen(driver, f"attempt-{attempt}-{host}-search")
            print(f"  {host} search -> {row['search_classification']}")

            payload = parse_json_text(search_text)
            if not payload or not isinstance(payload.get("data"), list):
                continue

            anime = next(
                (
                    item
                    for item in payload["data"]
                    if isinstance(item, dict)
                    and str(item.get("title", "")).strip().lower() == "bleach"
                ),
                payload["data"][0] if payload["data"] else None,
            )
            if not isinstance(anime, dict):
                continue

            anime_session = str(anime.get("session") or "")
            if not anime_session:
                continue
            row["anime_id"] = anime.get("id")
            row["anime_session"] = anime_session

            release_url = f"https://{host}/api?m=release&id={anime_session}&sort=episode_asc&page=1"
            driver.get(release_url)
            time.sleep(5)
            release_text = body_text(driver)
            row["release_classification"] = classify(driver)
            row["release_preview"] = re.sub(r"\s+", " ", release_text)[:400]
            save_screen(driver, f"attempt-{attempt}-{host}-release")
            print(f"  {host} release -> {row['release_classification']}")

            release = parse_json_text(release_text)
            episodes = release.get("data") if isinstance(release, dict) else None
            if not isinstance(episodes, list) or not episodes:
                continue
            episode = next(
                (item for item in episodes if isinstance(item, dict) and item.get("session")),
                None,
            )
            if not episode:
                continue
            episode_session = str(episode["session"])
            row["episode_session"] = episode_session

            play_url = f"https://{host}/play/{anime_session}/{episode_session}"
            driver.get(play_url)
            time.sleep(7)
            play_source = driver.page_source
            row["play_classification"] = classify(driver)
            row["play_preview"] = re.sub(r"\s+", " ", body_text(driver))[:400]
            save_screen(driver, f"attempt-{attempt}-{host}-play")
            print(f"  {host} play -> {row['play_classification']}")

            kwik_match = KWIK_RE.search(play_source)
            if not kwik_match:
                continue
            kwik_url = kwik_match.group(0).replace("&amp;", "&")
            row["kwik_url"] = kwik_url
            row["kwik_host"] = urlparse(kwik_url).hostname

            driver.get(kwik_url)
            time.sleep(7)
            row["kwik_classification"] = classify(driver)
            row["kwik_preview"] = re.sub(r"\s+", " ", body_text(driver))[:400]
            save_screen(driver, f"attempt-{attempt}-kwik")
            print(f"  Kwik -> {row['kwik_classification']}")

            report["success"] = True
            report["successful_attempt"] = attempt
            report["successful_host"] = host
            report["anime_session"] = anime_session
            report["episode_session"] = episode_session
            report["kwik_url"] = kwik_url
            break

        if report["success"]:
            break
    finally:
        driver.quit()

(OUT / "browser-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2)[:12000])

if not report.get("tor_confirmed"):
    raise SystemExit("Chrome traffic was not confirmed to be using Tor")
