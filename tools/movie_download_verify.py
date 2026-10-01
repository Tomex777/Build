#!/usr/bin/env python3
import json, re, subprocess, sys, time
from pathlib import Path
from urllib.parse import urljoin, quote
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError

TMDB_ID = "10378"  # Big Buck Bunny — open/CC test title
BASE = "https://vixsrc.to"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131 Safari/537.36"
OUT = Path("movie-download-proof.mkv")
REPORT = Path("movie-download-proof.json")
MD = Path("movie-download-proof.md")
opener = build_opener(HTTPRedirectHandler())

def get(url, headers=None):
    h={"User-Agent":UA,"Accept":"*/*","Accept-Encoding":"identity"}
    if headers: h.update(headers)
    req=Request(url,headers=h)
    with opener.open(req,timeout=20) as r:
        return r.status,r.geturl(),dict(r.headers.items()),r.read().decode("utf-8","replace")

def resolve():
    api = BASE + "/api/movie/" + TMDB_ID
    status, _, _, body = get(api, {
        "Accept":"application/json,*/*",
        "Referer":BASE,
        "Origin":BASE,
    })
    data=json.loads(body)
    src=data.get("src")
    if status != 200 or not src:
        raise RuntimeError("VixSrc API did not return embed source")
    embed=urljoin(BASE,src)
    estatus, _, _, html=get(embed,{
        "Accept":"text/html,*/*",
        "Referer":BASE,
        "Origin":BASE,
    })
    token=re.search(r'token["\']\s*:\s*["\']([^"\']+)',html)
    expires=re.search(r'expires["\']\s*:\s*["\']([^"\']+)',html)
    playlist=re.search(r'url\s*:\s*["\']([^"\']+)',html)
    if estatus != 200 or not (token and expires and playlist):
        raise RuntimeError("VixSrc embed did not expose fresh playlist token")
    base=playlist.group(1).replace("\\/","/")
    master=base+("&" if "?" in base else "?")+"token="+quote(token.group(1),safe="")+"&expires="+quote(expires.group(1),safe="")+"&h=1"
    return api, master

def run():
    api, master = resolve()
    if OUT.exists(): OUT.unlink()

    # Let FFmpeg consume the master so linked audio/subtitle playlists remain
    # available. Choose the first video + first audio and remux without re-encoding.
    header_blob = "Referer: " + api + "\r\nOrigin: " + BASE + "\r\nUser-Agent: " + UA + "\r\n"
    cmd=[
        "ffmpeg","-hide_banner","-loglevel","error","-nostdin",
        "-headers",header_blob,
        "-i",master,
        "-map","0:v:0","-map","0:a:0?",
        "-c","copy","-y",str(OUT),
    ]
    started=time.time()
    cp=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=900)
    elapsed=round(time.time()-started,2)
    if cp.returncode != 0:
        # Do not emit the tokenized URL or raw FFmpeg stderr.
        raise RuntimeError("ffmpeg failed with exit code " + str(cp.returncode))

    if not OUT.exists() or OUT.stat().st_size < 1_000_000:
        raise RuntimeError("download output is missing or implausibly small")

    probe_cmd=[
        "ffprobe","-v","error",
        "-show_entries","format=duration,size,format_name:stream=index,codec_type,codec_name,width,height",
        "-of","json",str(OUT),
    ]
    pp=subprocess.run(probe_cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=60)
    if pp.returncode != 0:
        raise RuntimeError("ffprobe could not reopen downloaded movie")
    meta=json.loads(pp.stdout)
    duration=float((meta.get("format") or {}).get("duration") or 0)
    size=int((meta.get("format") or {}).get("size") or OUT.stat().st_size)
    streams=meta.get("streams") or []
    video=[s for s in streams if s.get("codec_type")=="video"]
    audio=[s for s in streams if s.get("codec_type")=="audio"]

    # Big Buck Bunny is roughly ten minutes. This threshold distinguishes a full
    # feature download from a tiny clip/segment while allowing provider edits.
    full = duration >= 500 and size >= 1_000_000 and len(video) >= 1
    report={
        "title":"Big Buck Bunny",
        "tmdb":TMDB_ID,
        "provider":"VixSrc",
        "download_complete": cp.returncode == 0,
        "reopen_ffprobe": pp.returncode == 0,
        "duration_seconds":round(duration,3),
        "file_size_bytes":size,
        "elapsed_seconds":elapsed,
        "video_streams":len(video),
        "audio_streams":len(audio),
        "video_codecs":[s.get("codec_name") for s in video],
        "audio_codecs":[s.get("codec_name") for s in audio],
        "dimensions":[[s.get("width"),s.get("height")] for s in video],
        "full_movie_proven":full,
    }
    REPORT.write_text(json.dumps(report,indent=2),encoding="utf-8")
    MD.write_text(
        "# Movie download verification\n\n"
        + "| Check | Result |\n|---|---|\n"
        + "| Title | Big Buck Bunny |\n"
        + "| Provider | VixSrc |\n"
        + "| Download command completed | " + str(report["download_complete"]) + " |\n"
        + "| Reopened with ffprobe | " + str(report["reopen_ffprobe"]) + " |\n"
        + "| Duration | " + str(report["duration_seconds"]) + " s |\n"
        + "| File size | " + str(report["file_size_bytes"]) + " bytes |\n"
        + "| Video streams | " + str(report["video_streams"]) + " |\n"
        + "| Audio streams | " + str(report["audio_streams"]) + " |\n"
        + "| Full movie proof | **" + str(report["full_movie_proven"]) + "** |\n",
        encoding="utf-8"
    )
    print(json.dumps(report,indent=2))
    if not full:
        sys.exit(2)

if __name__=="__main__":
    run()
