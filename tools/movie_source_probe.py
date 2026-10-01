#!/usr/bin/env python3
import json, re, time, html as htmlmod
from pathlib import Path
from urllib.parse import urljoin, urlparse
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36"
TIMEOUT = 20
MAX_BODY = 2500000

TEST_TITLES = [
    {"title":"Big Buck Bunny","tmdb":"10378","imdb":"tt1254207"},
    {"title":"Night of the Living Dead","tmdb":"10331","imdb":"tt0063350"},
]

PROVIDERS = {
    "vixsrc": "https://vixsrc.to/movie/{tmdb}",
    "vidlink": "https://vidlink.pro/movie/{tmdb}",
    "vidnest": "https://vidnest.fun/movie/{tmdb}",
    "vidfast": "https://vidfast.pro/movie/{tmdb}",
    "autoembed": "https://autoembed.co/movie/tmdb/{tmdb}",
    "vidrock": "https://vidrock.net/embed/movie/{tmdb}",
    "vidsrc_to": "https://vidsrc.to/embed/movie/{tmdb}",
    "vidsrc_xyz": "https://vidsrc.xyz/embed/movie/{tmdb}",
    "vidbox": "https://dl.vidbox.vc/",
}

opener = build_opener(HTTPRedirectHandler())

def fetch(url, referer=None, range_header=None):
    headers = {
        "User-Agent": UA,
        "Accept": "*/*",
        "Accept-Encoding": "identity",
        "Cache-Control": "no-cache",
    }
    if referer:
        headers["Referer"] = referer
        p = urlparse(referer)
        if p.scheme and p.netloc:
            headers["Origin"] = p.scheme + "://" + p.netloc
    if range_header:
        headers["Range"] = range_header
    req = Request(url, headers=headers)
    started = time.time()
    try:
        with opener.open(req, timeout=TIMEOUT) as r:
            data = r.read(MAX_BODY)
            return {
                "ok": True, "status": getattr(r, "status", 200), "url": r.geturl(),
                "headers": dict(r.headers.items()), "data": data,
                "elapsed_ms": round((time.time()-started)*1000),
            }
    except HTTPError as e:
        try:
            data = e.read(300000)
        except Exception:
            data = b""
        return {
            "ok": False, "status": e.code, "url": getattr(e, "url", url),
            "headers": dict(getattr(e, "headers", {}) or {}), "data": data,
            "error": str(e), "elapsed_ms": round((time.time()-started)*1000),
        }
    except Exception as e:
        return {
            "ok": False, "status": None, "url": url, "headers": {}, "data": b"",
            "error": repr(e), "elapsed_ms": round((time.time()-started)*1000),
        }

def text_of(resp):
    raw = resp.get("data", b"")
    ct = resp.get("headers", {}).get("Content-Type", "")
    m = re.search(r"charset=([A-Za-z0-9_-]+)", ct, re.I)
    charset = m.group(1) if m else "utf-8"
    try:
        return raw.decode(charset, "replace")
    except Exception:
        return raw.decode("utf-8", "replace")

def normalized_urls(s):
    if not s:
        return []
    s = htmlmod.unescape(s).replace("\\/", "/")
    urls = re.findall(r'https?://[^\s"\'<>\\]+', s)
    out = []
    for u in urls:
        u = u.rstrip("),;]")
        if u not in out:
            out.append(u)
    return out

def extract_json_urls(s):
    try:
        obj = json.loads(s)
    except Exception:
        return []
    found = []
    def walk(x):
        if isinstance(x, dict):
            for v in x.values():
                walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)
        elif isinstance(x, str) and x.startswith(("http://", "https://")):
            found.append(x)
    walk(obj)
    return found

def media_urls(s):
    return [u for u in normalized_urls(s) if re.search(r'\.(m3u8|mp4|mpd)(\?|$)', u, re.I)]

def iframe_urls(base, s):
    out = []
    for m in re.finditer(r'<iframe[^>]+src=["\']([^"\']+)', s, re.I):
        u = urljoin(base, htmlmod.unescape(m.group(1)))
        if u not in out:
            out.append(u)
    return out

def script_urls(base, s):
    out = []
    for m in re.finditer(r'<script[^>]+src=["\']([^"\']+)', s, re.I):
        u = urljoin(base, htmlmod.unescape(m.group(1)))
        if u not in out:
            out.append(u)
    return out

def vixsrc_master(s):
    if "masterPlaylist" not in s:
        return None
    bm = re.search(r'masterPlaylist\s*=\s*\{(.*?)\}', s, re.S)
    block = bm.group(1) if bm else s
    def field(name):
        for pat in [
            r'["\']' + name + r'["\']\s*:\s*["\']([^"\']+)["\']',
            r'\b' + name + r'\s*:\s*["\']([^"\']+)["\']',
        ]:
            m = re.search(pat, block, re.I)
            if m:
                return htmlmod.unescape(m.group(1)).replace("\\/", "/")
        return None
    u = field("url")
    token = field("token")
    expires = field("expires")
    if not u:
        return None
    params = []
    if token:
        params.append("token=" + token)
    if expires:
        params.append("expires=" + expires)
    params += ["h=1", "lang=en"]
    return u + ("&" if "?" in u else "?") + "&".join(params)

def validate_media(url, referer):
    first = fetch(url, referer=referer, range_header="bytes=0-131071")
    ct = first.get("headers", {}).get("Content-Type", "")
    data = first.get("data", b"")
    txt = data.decode("utf-8", "replace")
    rec = {
        "url": url, "status": first.get("status"), "content_type": ct,
        "bytes": len(data), "final_url": first.get("url"),
        "is_hls": False, "is_dash": False,
        "segment_status": None, "segment_bytes": 0,
    }

    if "#EXTM3U" in txt or ".m3u8" in url.lower() or "mpegurl" in ct.lower():
        if "#EXTM3U" not in txt:
            full = fetch(url, referer=referer)
            txt = text_of(full)
            rec["status"] = full.get("status")
            rec["bytes"] = len(full.get("data", b""))
            base = full.get("url") or url
        else:
            base = first.get("url") or url
        rec["is_hls"] = "#EXTM3U" in txt
        if rec["is_hls"]:
            lines = [ln.strip() for ln in txt.splitlines() if ln.strip()]
            uris = [ln for ln in lines if not ln.startswith("#")]
            child = next((u for u in uris if ".m3u8" in u.lower()), None)
            if child:
                child_url = urljoin(base, child)
                child_resp = fetch(child_url, referer=referer)
                child_txt = text_of(child_resp)
                if "#EXTM3U" in child_txt:
                    lines = [ln.strip() for ln in child_txt.splitlines() if ln.strip()]
                    uris = [ln for ln in lines if not ln.startswith("#")]
                    base = child_resp.get("url") or child_url
            seg = next((u for u in uris if ".m3u8" not in u.lower() and ".mpd" not in u.lower()), None)
            if seg:
                seg_url = urljoin(base, seg)
                sr = fetch(seg_url, referer=referer, range_header="bytes=0-262143")
                rec["segment_url"] = seg_url
                rec["segment_status"] = sr.get("status")
                rec["segment_bytes"] = len(sr.get("data", b""))
                rec["segment_content_type"] = sr.get("headers", {}).get("Content-Type", "")
    elif ".mpd" in url.lower() or "dash+xml" in ct.lower():
        rec["is_dash"] = "<MPD" in txt
    elif "video/" in ct.lower() or ".mp4" in url.lower():
        rec["range_proven"] = first.get("status") in (200, 206) and len(data) > 0

    rec["media_bytes_proven"] = (
        (rec["is_hls"] and rec["segment_status"] in (200,206) and rec["segment_bytes"] > 0)
        or rec.get("range_proven", False)
        or (rec["is_dash"] and first.get("status") in (200,206) and len(data) > 0)
    )
    return rec

def probe_provider(name, url, tmdb):
    r = fetch(url)
    body = text_of(r)
    rec = {
        "provider": name,
        "embed_url": url,
        "embed_status": r.get("status"),
        "embed_final_url": r.get("url"),
        "embed_type": r.get("headers", {}).get("Content-Type", ""),
        "embed_bytes": len(r.get("data", b"")),
        "elapsed_ms": r.get("elapsed_ms"),
        "error": r.get("error"),
    }
    candidates = []
    notes = []

    for u in media_urls(body):
        if u not in candidates:
            candidates.append(u)

    if name == "vixsrc":
        master = vixsrc_master(body)
        if master:
            notes.append("window.masterPlaylist extracted")
            if master not in candidates:
                candidates.append(master)

    for u in extract_json_urls(body):
        if re.search(r'\.(m3u8|mp4|mpd)(\?|$)', u, re.I) and u not in candidates:
            candidates.append(u)

    if name == "vidlink":
        api = "https://vidlink.pro/api/movie/" + tmdb
        ar = fetch(api, referer=url)
        at = text_of(ar)
        notes.append("direct API probe status=" + str(ar.get("status")) + " type=" + ar.get("headers",{}).get("Content-Type",""))
        for u in media_urls(at) + extract_json_urls(at):
            if re.search(r'\.(m3u8|mp4|mpd)(\?|$)', u, re.I) and u not in candidates:
                candidates.append(u)

    level = iframe_urls(r.get("url") or url, body)[:8]
    rec["iframes"] = level
    seen = set()
    for depth in range(2):
        nxt = []
        for fr in level:
            if fr in seen:
                continue
            seen.add(fr)
            frsp = fetch(fr, referer=r.get("url") or url)
            ftxt = text_of(frsp)
            notes.append("iframe depth=" + str(depth) + " status=" + str(frsp.get("status")) + " url=" + fr)
            for u in media_urls(ftxt) + extract_json_urls(ftxt):
                if re.search(r'\.(m3u8|mp4|mpd)(\?|$)', u, re.I) and u not in candidates:
                    candidates.append(u)
            nxt.extend(iframe_urls(frsp.get("url") or fr, ftxt)[:8])
        level = nxt[:8]

    hints = []
    scripts = script_urls(r.get("url") or url, body)[:12]
    rec["scripts_scanned"] = len(scripts)
    for su in scripts:
        sr = fetch(su, referer=r.get("url") or url)
        st = text_of(sr)
        for u in media_urls(st):
            if u not in candidates:
                candidates.append(u)
        for u in normalized_urls(st):
            lo = u.lower()
            if any(k in lo for k in ["m3u8","stream","source","decrypt","playlist","/api/"]):
                hints.append(u[:300])
                if len(hints) >= 20:
                    break
        if len(hints) >= 20:
            break

    rec["candidate_media_urls"] = candidates[:20]
    rec["script_hints"] = hints[:20]
    rec["notes"] = notes
    rec["media_checks"] = []
    for u in candidates[:8]:
        try:
            rec["media_checks"].append(validate_media(u, r.get("url") or url))
        except Exception as e:
            rec["media_checks"].append({"url":u, "error":repr(e), "media_bytes_proven":False})

    if any(x.get("media_bytes_proven") for x in rec["media_checks"]):
        rec["classification"] = "PROVEN_MEDIA_BYTES"
    elif r.get("status") in (200,206) and len(r.get("data",b"")) > 0:
        rec["classification"] = "EMBED_REACHABLE_NO_MEDIA_PROOF"
    elif r.get("status") in (403,429,451):
        rec["classification"] = "BLOCKED"
    elif r.get("status") in (404,410):
        rec["classification"] = "DEAD_OR_ROUTE_GONE"
    else:
        rec["classification"] = "FAILED"
    return rec

def main():
    all_results = []
    for title in TEST_TITLES:
        for name, templ in PROVIDERS.items():
            if name == "vidbox" and title != TEST_TITLES[0]:
                continue
            url = templ.format(**title)
            print("::group::" + title["title"] + " :: " + name)
            rec = probe_provider(name, url, title["tmdb"])
            rec["test_title"] = title["title"]
            rec["tmdb"] = title["tmdb"]
            print(json.dumps({
                "provider": rec["provider"],
                "test_title": rec["test_title"],
                "embed_status": rec["embed_status"],
                "classification": rec["classification"],
            }, indent=2))
            print("::endgroup::")
            all_results.append(rec)

    Path("movie-source-probe-results.json").write_text(json.dumps(all_results, indent=2), encoding="utf-8")

    lines = [
        "# Movies source probe results", "",
        "Strict meaning: PROVEN_MEDIA_BYTES requires an actual media response and transferred HLS segment / MP4-DASH bytes. Embed-only reachability is not counted as working.", "",
        "| Provider | Test title | Embed | Classification | Media candidates | Proven checks |",
        "|---|---|---:|---|---:|---:|",
    ]
    for r in all_results:
        proven = sum(1 for x in r.get("media_checks", []) if x.get("media_bytes_proven"))
        lines.append("| " + r["provider"] + " | " + r["test_title"] + " | " + str(r.get("embed_status")) + " | " + r["classification"] + " | " + str(len(r.get("candidate_media_urls", []))) + " | " + str(proven) + " |")
    lines += ["", "## Detailed notes", ""]
    for r in all_results:
        lines.append("### " + r["provider"] + " — " + r["test_title"])
        lines.append("- Embed: " + str(r.get("embed_status")) + " " + str(r.get("embed_final_url")))
        lines.append("- Classification: " + r["classification"])
        for mc in r.get("media_checks", []):
            lines.append("  - media status=" + str(mc.get("status")) + " bytes=" + str(mc.get("bytes")) + " segment_status=" + str(mc.get("segment_status")) + " segment_bytes=" + str(mc.get("segment_bytes")) + " proven=" + str(mc.get("media_bytes_proven")) + " url=" + str(mc.get("url")))
        for n in r.get("notes", [])[:12]:
            lines.append("- " + n)
        lines.append("")
    Path("movie-source-probe-results.md").write_text("\n".join(lines), encoding="utf-8")

if __name__ == "__main__":
    main()
