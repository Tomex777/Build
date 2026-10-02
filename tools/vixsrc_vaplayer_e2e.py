#!/usr/bin/env python3
import html as htmlmod
import json
import os
import re
import subprocess
import tempfile
import urllib.parse
from pathlib import Path

import requests

UA = "Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
MOVIES = [
    {"title":"Night of the Living Dead","tmdb":"10331","imdb":"tt0063350"},
    {"title":"Big Buck Bunny","tmdb":"10378","imdb":"tt1254207"},
]
TV = {"title":"The Beverly Hillbillies S01E01","tmdb":"1930","imdb":"tt0055662","season":"1","episode":"1"}
session = requests.Session()
session.headers.update({"User-Agent":UA,"Accept-Language":"en-US,en;q=0.9"})


def get(url, headers=None, stream=False):
    r = session.get(url, headers=headers or {}, timeout=30, allow_redirects=True, stream=stream)
    r.raise_for_status()
    return r


def ffprobe_url(url, headers):
    blob = "".join(f"{k}: {v}\r\n" for k,v in headers.items())
    cp = subprocess.run([
        "ffprobe","-v","error","-headers",blob,
        "-show_entries","format=format_name,duration:stream=codec_type,codec_name,width,height",
        "-of","json",url,
    ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=90)
    return {"returnCode":cp.returncode,"stdout":cp.stdout,"stderr":cp.stderr[-1000:]}


def remux_sample(url, headers, label):
    with tempfile.TemporaryDirectory(prefix="provider-e2e-") as td:
        out = os.path.join(td, "sample.mkv")
        blob = "".join(f"{k}: {v}\r\n" for k,v in headers.items())
        cp = subprocess.run([
            "ffmpeg","-hide_banner","-loglevel","error","-headers",blob,
            "-i",url,"-map","0:v:0","-map","0:a:0?","-t","8","-c","copy","-y",out,
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=120)
        rec={"label":label,"returnCode":cp.returncode,"stderr":cp.stderr[-1000:]}
        if cp.returncode != 0 or not os.path.exists(out):
            return rec
        rec["bytes"]=os.path.getsize(out)
        fp=subprocess.run([
            "ffprobe","-v","error",
            "-show_entries","format=duration,size:stream=codec_type,codec_name,width,height",
            "-of","json",out,
        ],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=30)
        rec["ffprobeReturnCode"]=fp.returncode
        try: rec["ffprobe"]=json.loads(fp.stdout or "{}")
        except: rec["ffprobe"]={}
        rec["playable"]=(
            fp.returncode==0 and
            float((rec["ffprobe"].get("format") or {}).get("duration") or 0)>0 and
            any(x.get("codec_type")=="video" for x in rec["ffprobe"].get("streams") or [])
        )
        return rec


def read_segment(master_url, headers):
    r=get(master_url,headers)
    text=r.text
    if "#EXTM3U" not in text:
        return {"status":r.status_code,"isM3U8":False,"bytes":len(r.content)}
    base=r.url
    lines=[x.strip() for x in text.splitlines() if x.strip()]
    uris=[x for x in lines if not x.startswith("#")]
    child=next((x for x in uris if ".m3u8" in x.lower()),None)
    if child:
        cr=get(urllib.parse.urljoin(base,child),headers)
        if "#EXTM3U" in cr.text:
            base=cr.url
            lines=[x.strip() for x in cr.text.splitlines() if x.strip()]
            uris=[x for x in lines if not x.startswith("#")]
    seg=next((x for x in uris if ".m3u8" not in x.lower()),None)
    if not seg:
        return {"status":r.status_code,"isM3U8":True,"segment":False}
    sr=get(urllib.parse.urljoin(base,seg),headers,stream=True)
    first=next(sr.iter_content(chunk_size=65536),b"")
    sr.close()
    return {
        "status":r.status_code,
        "isM3U8":True,
        "segmentStatus":sr.status_code,
        "segmentBytes":len(first),
        "contentType":sr.headers.get("content-type"),
    }


def vixsrc(kind, item):
    if kind=="movie":
        api=f"https://vixsrc.to/api/movie/{item['tmdb']}"
    else:
        api=f"https://vixsrc.to/api/tv/{item['tmdb']}/{item['season']}/{item['episode']}"
    headers={"Accept":"application/json,*/*","Referer":"https://vixsrc.to","Origin":"https://vixsrc.to"}
    ar=get(api,headers)
    data=ar.json()
    src=data.get("src") if isinstance(data,dict) else None
    if not src:
        raise RuntimeError("VixSrc returned no src")
    er=get(urllib.parse.urljoin("https://vixsrc.to",src),{**headers,"Accept":"text/html,*/*"})
    html=er.text
    token=re.search(r'token["\']\s*:\s*["\']([^"\']+)',html)
    expires=re.search(r'expires["\']\s*:\s*["\']([^"\']+)',html)
    playlist=re.search(r'url\s*:\s*["\']([^"\']+)',html)
    if not(token and expires and playlist):
        raise RuntimeError("VixSrc embed config incomplete")
    base=htmlmod.unescape(playlist.group(1)).replace("\\/","/")
    q={"token":token.group(1),"expires":expires.group(1),"h":"1"}
    master=base+("&" if "?" in base else "?")+urllib.parse.urlencode(q)
    media_headers={"Referer":api,"Origin":"https://vixsrc.to","User-Agent":UA}
    seg=read_segment(master,media_headers)
    sample=remux_sample(master,media_headers,f"vixsrc-{kind}")
    return {
        "provider":"VixSrc","kind":kind,"title":item["title"],
        "apiStatus":ar.status_code,"embedStatus":er.status_code,
        "segment":seg,"sample":sample,
        "complete":bool(seg.get("segmentBytes")) and sample.get("playable") is True,
    }


def vaplayer(kind,item):
    params={"imdb":item["imdb"],"type":kind}
    if kind=="tv":
        params.update({"season":item["season"],"episode":item["episode"]})
        referer=f"https://nextgencloudfabric.com/embed/tv/{item['imdb']}/{item['season']}/{item['episode']}"
    else:
        referer=f"https://nextgencloudfabric.com/embed/movie/{item['imdb']}"
    headers={"Referer":referer,"Origin":"https://nextgencloudfabric.com","Accept":"application/json,*/*"}
    api="https://streamdata.vaplayer.ru/api.php?"+urllib.parse.urlencode(params)
    r=get(api,headers)
    data=r.json()
    urls=((data.get("data") or {}).get("stream_urls") if isinstance(data,dict) else None) or []
    urls=[u for u in urls if isinstance(u,str) and u.startswith(("http://","https://"))]
    attempts=[]
    for url in urls[:6]:
        media_headers={"Referer":"https://nextgencloudfabric.com/","Origin":"https://nextgencloudfabric.com","User-Agent":UA}
        try:
            seg=read_segment(url,media_headers)
            sample=remux_sample(url,media_headers,f"vaplayer-{kind}")
            attempts.append({"segment":seg,"sample":sample})
            if sample.get("playable"):
                break
        except Exception as e:
            attempts.append({"error":repr(e)})
    return {
        "provider":"VaPlayer","kind":kind,"title":item["title"],
        "apiStatus":r.status_code,"apiStatusCode":data.get("status_code") if isinstance(data,dict) else None,
        "urlCount":len(urls),"attempts":attempts,
        "complete":any(x.get("sample",{}).get("playable") is True for x in attempts),
    }


def main():
    rows=[]
    for item in MOVIES:
        for fn in (vixsrc,vaplayer):
            try: rows.append(fn("movie",item))
            except Exception as e: rows.append({"provider":fn.__name__,"kind":"movie","title":item["title"],"complete":False,"error":repr(e)})
    for fn in (vixsrc,vaplayer):
        try: rows.append(fn("tv",TV))
        except Exception as e: rows.append({"provider":fn.__name__,"kind":"tv","title":TV["title"],"complete":False,"error":repr(e)})
    summary={
        "rows":rows,
        "vixsrcMovie":any(r.get("provider")=="VixSrc" and r.get("kind")=="movie" and r.get("complete") for r in rows),
        "vixsrcTv":any(r.get("provider")=="VixSrc" and r.get("kind")=="tv" and r.get("complete") for r in rows),
        "vaplayerMovie":any(r.get("provider")=="VaPlayer" and r.get("kind")=="movie" and r.get("complete") for r in rows),
        "vaplayerTv":any(r.get("provider")=="VaPlayer" and r.get("kind")=="tv" and r.get("complete") for r in rows),
    }
    summary["complete"]=all(summary[k] for k in ("vixsrcMovie","vixsrcTv","vaplayerMovie","vaplayerTv"))
    Path("vixsrc-vaplayer-e2e.json").write_text(json.dumps(summary,indent=2),encoding="utf-8")
    print(json.dumps(summary,indent=2))
    if not summary["complete"]:
        raise SystemExit(1)

if __name__=="__main__":
    main()
