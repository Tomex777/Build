#!/usr/bin/env python3
import argparse
import json
import re
import sys
from urllib.parse import urlparse, urlunparse

try:
    import curl_cffi.requests
except Exception as exc:
    print(f"curl_cffi is required: {exc}", file=sys.stderr)
    raise SystemExit(2)

KWIK_TLDS = ["cx", "gg", "si", "me", "net", "in", "cc"]
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


def unpack_packer(script: str) -> str:
    match = re.search(
        r"\}\s*\(\s*'(.*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)",
        script,
        re.DOTALL,
    )
    if not match:
        return script
    payload, base_s, count_s, mapping_s = match.groups()
    base, count = int(base_s), int(count_s)
    mapping = mapping_s.split("|")
    digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    def enc(number: int) -> str:
        if number < base:
            return digits[number]
        return enc(number // base) + digits[number % base]

    lookup = {
        enc(i): (mapping[i] if i < len(mapping) and mapping[i] else enc(i))
        for i in range(count)
    }
    return re.sub(r"\b\w+\b", lambda m: lookup.get(m.group(0), m.group(0)), payload)


def extract_media(html: str):
    match = re.search(r"(https?://[^\s'\"\\>]+(?:uwu\.m3u8|\.m3u8)[^\s'\"\\>]*)", html)
    if match:
        return match.group(1).replace("\\/", "/").rstrip("\\")

    scripts = sorted(
        re.findall(r"<script[^>]*>([\s\S]*?)</script>", html, re.IGNORECASE),
        key=len,
        reverse=True,
    )
    for script in scripts:
        current = script
        for _ in range(6):
            inner = re.search(r'eval\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)', current)
            if inner:
                try:
                    current = bytes(inner.group(1), "utf-8").decode("unicode_escape")
                except Exception:
                    current = inner.group(1)
                continue
            unpacked = unpack_packer(current)
            if unpacked != current:
                current = unpacked
                continue
            break
        match = re.search(r"(https?://[^\s'\"\\>]+(?:uwu\.m3u8|\.m3u8)[^\s'\"\\>]*)", current)
        if match:
            return match.group(1).replace("\\/", "/").rstrip("\\")

    match = re.search(r'<source[^>]+src=["\']([^"\']+\.(?:m3u8|mp4)[^"\']*)["\']', html, re.I)
    return match.group(1) if match else None


def swap_domain(url: str, tld: str) -> str:
    parsed = urlparse(url)
    parts = parsed.netloc.split(".")
    if len(parts) >= 2:
        parts[-1] = tld
    return urlunparse((parsed.scheme, ".".join(parts), parsed.path, parsed.params, parsed.query, parsed.fragment))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--proxy", required=True)
    parser.add_argument("--referer", default="https://animepahe.pw/")
    parser.add_argument("--timeout", type=float, default=20)
    args = parser.parse_args()

    headers = {
        "User-Agent": UA,
        "Referer": args.referer,
    }

    current_tld = urlparse(args.url).netloc.rsplit(".", 1)[-1]
    candidates = [args.url]
    candidates.extend(swap_domain(args.url, tld) for tld in KWIK_TLDS if tld != current_tld)

    errors = []
    for candidate in candidates:
        try:
            response = curl_cffi.requests.get(
                candidate,
                impersonate="chrome120",
                headers=headers,
                proxy=args.proxy,
                timeout=args.timeout,
                allow_redirects=True,
            )
            if response.status_code != 200:
                errors.append(f"{candidate}: HTTP {response.status_code}")
                continue
            media = extract_media(response.text)
            if not media:
                errors.append(f"{candidate}: no media URL")
                continue
            cookies = [{"name": k, "value": v} for k, v in response.cookies.items()]
            print(json.dumps({
                "url": media,
                "referer": str(response.url),
                "userAgent": UA,
                "cookies": cookies,
                "cookie": "; ".join(f"{c['name']}={c['value']}" for c in cookies),
                "kwik": candidate,
            }))
            return
        except Exception as exc:
            errors.append(f"{candidate}: {type(exc).__name__}: {exc}")

    print("Kwik resolution failed: " + " | ".join(errors[-7:]), file=sys.stderr)
    raise SystemExit(3)


if __name__ == "__main__":
    main()
