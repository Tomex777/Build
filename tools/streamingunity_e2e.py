#!/usr/bin/env python3
import html as htmllib
import json
import os
import re
import subprocess
import tempfile
import urllib.parse
from pathlib import Path

import requests

BASES = [
    "https://streamingunity.fun",
    "https://streamingunity.win",
    "https://streamingunity.vip",
]
TITLE_ID = 1693
SLUG = "house"
UA = "Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"

session = requests.Session()
session.headers.update({"User-Agent": UA, "Accept-Language": "en-US,en;q=0.9"})


def data_page(text):
    m = re.search(r'data-page="([^"]+)"', text)
    if not m:
        return None
    try:
        return json.loads(htmllib.unescape(m.group(1)))
    except Exception:
        return None


def fetch_page(url, **kwargs):
    r = session.get(url, timeout=30, allow_redirects=True, **kwargs)
    r.raise_for_status()
    return r


def title_record(page):
    props = (page or {}).get("props") or {}
    title = props.get("title") or {}
    loaded = props.get("loadedSeason") or {}
    return title, loaded


def resolve():
    proof = {"titleId": TITLE_ID, "slug": SLUG, "attempts": [], "resolved": {}}
    chosen = None
    for base in BASES:
        url = f"{base}/en/titles/{TITLE_ID}-{SLUG}"
        try:
            r = fetch_page(url)
            page = data_page(r.text)
            rec = {
                "base": base,
                "status": r.status_code,
                "finalHost": urllib.parse.urlparse(r.url).netloc,
                "bytes": len(r.content),
                "hasDataPage": bool(page),
            }
            if page:
                title, loaded = title_record(page)
                rec["title"] = {
                    "id": title.get("id"),
                    "name": title.get("name"),
                    "type": title.get("type"),
                    "tmdb_id": title.get("tmdb_id"),
                }
                rec["seasonCount"] = len(title.get("seasons") or [])
                rec["loadedEpisodeCount"] = len(loaded.get("episodes") or [])
                if title.get("id") == TITLE_ID:
                    chosen = (r.url.split("/en/")[0], page)
            proof["attempts"].append(rec)
            if chosen:
                break
        except Exception as e:
            proof["attempts"].append({"base": base, "error": repr(e)})

    if not chosen:
        raise RuntimeError("StreamingUnity title page could not be resolved")

    base, page = chosen
    title, loaded = title_record(page)
    episodes = loaded.get("episodes") or []
    if not episodes:
        r = fetch_page(f"{base}/en/titles/{TITLE_ID}-{SLUG}/season-1")
        page2 = data_page(r.text) or {}
        loaded = ((page2.get("props") or {}).get("loadedSeason") or {})
        episodes = loaded.get("episodes") or []
    if not episodes:
        raise RuntimeError("No episode metadata resolved")

    ep = episodes[0]
    proof["resolved"]["episode"] = {
        "id": ep.get("id"),
        "number": ep.get("number"),
        "name": ep.get("name"),
    }

    iframe_url = f"{base}/en/iframe/{TITLE_ID}?episode_id={ep.get('id')}&next_episode=0"
    ir = fetch_page(iframe_url, headers={"Referer": base + "/"})
    proof["resolved"]["iframe"] = {"status": ir.status_code, "bytes": len(ir.content)}

    m = re.search(r'https://vixcloud\.co/embed/[^"\'\s<>]+', ir.text)
    if not m:
        raise RuntimeError("VixCloud embed not found")
    embed = htmllib.unescape(m.group(0))
    eu = urllib.parse.urlparse(embed)
    proof["resolved"]["embed"] = {
        "host": eu.netloc,
        "path": eu.path,
        "queryKeys": sorted(urllib.parse.parse_qs(eu.query).keys()),
    }

    er = fetch_page(embed, headers={"Referer": base + "/"})
    proof["resolved"]["embedResponse"] = {"status": er.status_code, "bytes": len(er.content)}

    script = None
    for sm in re.finditer(r"<script[^>]*>(.*?)</script>", er.text, re.S | re.I):
        if "masterPlaylist" in sm.group(1):
            script = sm.group(1)
            break
    if not script:
        raise RuntimeError("masterPlaylist script not found")

    def field(name):
        patterns = [
            rf'["\']{re.escape(name)}["\']\s*:\s*["\']([^"\']+)["\']',
            rf'\b{re.escape(name)}\s*:\s*["\']([^"\']+)["\']',
        ]
        for pat in patterns:
            mm = re.search(pat, script, re.I)
            if mm:
                return htmllib.unescape(mm.group(1)).replace("\\/", "/")
        return None

    token = field("token")
    expires = field("expires")
    raw_url = field("url")
    fhd = bool(re.search(r"canPlayFHD\s*=\s*true", script))
    proof["resolved"]["masterConfig"] = {
        "gotToken": bool(token),
        "gotExpires": bool(expires),
        "gotBaseUrl": bool(raw_url),
        "canPlayFHD": fhd,
    }
    if not (token and expires and raw_url):
        raise RuntimeError("Incomplete HLS master config")

    master = urllib.parse.urljoin(embed, raw_url)
    params = []
    if fhd:
        params.append(("h", "1"))
    params.extend([("token", token), ("expires", expires), ("lang", "en")])
    sep = "&" if "?" in master else "?"
    master += sep + urllib.parse.urlencode(params)

    headers = {
        "Referer": "https://vixcloud.co/",
        "Origin": "https://vixcloud.co",
        "User-Agent": UA,
        "Accept": "*/*",
    }
    mr = fetch_page(master, headers=headers)
    text = mr.text
    variants = []
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    for i, line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF") and i + 1 < len(lines):
            uri = lines[i + 1]
            if not uri.startswith("#"):
                res = re.search(r"RESOLUTION=(\d+)x(\d+)", line)
                bw = re.search(r"BANDWIDTH=(\d+)", line)
                variants.append({
                    "width": int(res.group(1)) if res else 0,
                    "height": int(res.group(2)) if res else 0,
                    "bandwidth": int(bw.group(1)) if bw else 0,
                    "url": urllib.parse.urljoin(master, uri),
                })

    proof["resolved"]["master"] = {
        "status": mr.status_code,
        "contentType": mr.headers.get("content-type"),
        "isM3U8": "#EXTM3U" in text,
        "hasAudioGroup": "#EXT-X-MEDIA:TYPE=AUDIO" in text,
        "hasSubtitleGroup": "#EXT-X-MEDIA:TYPE=SUBTITLES" in text,
        "variantCount": len(variants),
        "variants": [
            {"width": v["width"], "height": v["height"], "bandwidth": v["bandwidth"]}
            for v in variants
        ],
    }
    if "#EXTM3U" not in text or not variants:
        raise RuntimeError("No playable HLS variants")

    selected = max(variants, key=lambda v: (v["height"], v["bandwidth"]))
    vr = fetch_page(selected["url"], headers=headers)
    vtext = vr.text
    uris = [line.strip() for line in vtext.splitlines() if line.strip() and not line.startswith("#")]
    segment = next((u for u in uris if ".m3u8" not in u.lower()), None)
    if not segment:
        raise RuntimeError("No media segment found")
    segment_url = urllib.parse.urljoin(selected["url"], segment)
    sr = session.get(segment_url, headers=headers, timeout=30, stream=True)
    sr.raise_for_status()
    first = next(sr.iter_content(chunk_size=65536), b"")
    sr.close()
    proof["resolved"]["segment"] = {
        "status": sr.status_code,
        "contentType": sr.headers.get("content-type"),
        "bytesRead": len(first),
        "nonEmpty": bool(first),
    }
    if not first:
        raise RuntimeError("Media segment was empty")

    with tempfile.TemporaryDirectory(prefix="streamingunity-e2e-") as td:
        sample = os.path.join(td, "sample.mkv")
        header_blob = (
            "Referer: https://vixcloud.co/\r\n"
            "Origin: https://vixcloud.co\r\n"
            f"User-Agent: {UA}\r\n"
        )
        cmd = [
            "ffmpeg", "-hide_banner", "-loglevel", "error",
            "-headers", header_blob,
            "-i", master,
            "-map", "0:v:0", "-map", "0:a:0?",
            "-t", "8",
            "-c", "copy",
            "-y", sample,
        ]
        cp = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=120)
        proof["resolved"]["remux"] = {
            "returnCode": cp.returncode,
            "bytes": os.path.getsize(sample) if os.path.exists(sample) else 0,
            "copyOnly": True,
        }
        if cp.returncode != 0 or not os.path.exists(sample) or os.path.getsize(sample) < 4096:
            raise RuntimeError("FFmpeg stream-copy sample failed: " + cp.stderr[-500:])

        fp = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries",
             "format=duration,size:stream=index,codec_type,codec_name,width,height",
             "-of", "json", sample],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=30,
        )
        if fp.returncode != 0:
            raise RuntimeError("ffprobe failed: " + fp.stderr[-500:])
        meta = json.loads(fp.stdout or "{}")
        proof["resolved"]["ffprobe"] = meta
        proof["resolved"]["playableSample"] = bool(meta.get("streams")) and float((meta.get("format") or {}).get("duration") or 0) > 0
        if not proof["resolved"]["playableSample"]:
            raise RuntimeError("Sample was not playable")

    proof["complete"] = True
    return proof


def main():
    out = resolve()
    Path("streamingunity-e2e.json").write_text(json.dumps(out, indent=2), encoding="utf-8")
    r = out["resolved"]
    lines = [
        "# StreamingUnity end-to-end verification",
        "",
        f"- Resolver complete: **{out.get('complete') is True}**",
        f"- Master HLS: **{r.get('master', {}).get('isM3U8') is True}**",
        f"- Audio group: **{r.get('master', {}).get('hasAudioGroup') is True}**",
        f"- Subtitle group: **{r.get('master', {}).get('hasSubtitleGroup') is True}**",
        f"- Media segment bytes: **{r.get('segment', {}).get('nonEmpty') is True}**",
        f"- Stream-copy remux: **{r.get('remux', {}).get('returnCode') == 0}**",
        f"- ffprobe playable sample: **{r.get('playableSample') is True}**",
        "",
        "No transcoding is performed; FFmpeg uses stream copy only.",
    ]
    Path("streamingunity-e2e.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({
        "complete": out.get("complete"),
        "master": r.get("master"),
        "segment": r.get("segment"),
        "remux": r.get("remux"),
        "playableSample": r.get("playableSample"),
        "ffprobe": r.get("ffprobe"),
    }, indent=2))


if __name__ == "__main__":
    main()
