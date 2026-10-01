#!/usr/bin/env python3
import json, re, time, html as htmlmod, ssl
from pathlib import Path
from urllib.parse import urlencode, urljoin, urlparse, quote
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
TIMEOUT = 18
MAX_BODY = 3000000

TESTS = [
    {"title":"Big Buck Bunny","tmdb":"10378","imdb":"tt1254207","year":"2008","slug":"big-buck-bunny"},
    {"title":"Night of the Living Dead","tmdb":"10331","imdb":"tt0063350","year":"1968","slug":"night-of-the-living-dead"},
]

VIDNEST_ALPHABET = "RB0fpH8ZEyVLkv7c2i6MAJ5u3IKFDxlS1NTsnGaqmXYdUrtzjwObCgQP94hoeW+/="
VIDNEST_BACKENDS = [
    ("MoviesAPI","moviesapi"), ("HollyMovieHD","hollymoviehd"), ("AllMovies","allmovies"),
    ("VidLink","vidlink"), ("KlikXXI","klikxxi"), ("Movies4F","movies4f"),
    ("MovieBox","moviebox"), ("Videasy","videasy"), ("Movies5F","movies5f"),
]

opener = build_opener(HTTPRedirectHandler())

def request(url, headers=None, method="GET", data=None, range_header=None, max_body=MAX_BODY):
    h = {
        "User-Agent": UA,
        "Accept": "*/*",
        "Accept-Encoding": "identity",
        "Cache-Control": "no-cache",
    }
    if headers: h.update(headers)
    if range_header: h["Range"] = range_header
    req = Request(url, headers=h, method=method, data=data)
    started = time.time()
    try:
        with opener.open(req, timeout=TIMEOUT) as r:
            body = r.read(max_body)
            return {"ok":True,"status":getattr(r,"status",200),"url":r.geturl(),"headers":dict(r.headers.items()),"data":body,"ms":round((time.time()-started)*1000)}
    except HTTPError as e:
        try: body = e.read(min(max_body,300000))
        except Exception: body = b""
        return {"ok":False,"status":e.code,"url":getattr(e,"url",url),"headers":dict(getattr(e,"headers",{}) or {}),"data":body,"error":str(e),"ms":round((time.time()-started)*1000)}
    except Exception as e:
        return {"ok":False,"status":None,"url":url,"headers":{},"data":b"","error":repr(e),"ms":round((time.time()-started)*1000)}

def text(resp):
    raw=resp.get("data",b"")
    ct=resp.get("headers",{}).get("Content-Type","")
    m=re.search(r"charset=([A-Za-z0-9_-]+)",ct,re.I)
    cs=m.group(1) if m else "utf-8"
    try:return raw.decode(cs,"replace")
    except:return raw.decode("utf-8","replace")

def safe_url(u):
    if not u: return None
    try:
        p=urlparse(u)
        path=p.path
        if len(path)>80: path=path[:77]+"..."
        return p.scheme+"://"+p.netloc+path
    except: return "<unparseable>"

def looks_media(u):
    lo=(u or "").lower()
    return any(x in lo for x in [".m3u8",".mp4",".mpd",".mkv",".ts",".webm"]) or "stream" in lo or "video" in lo

def extract_urls(obj):
    found=[]
    def walk(x):
        if isinstance(x,dict):
            for v in x.values(): walk(v)
        elif isinstance(x,list):
            for v in x: walk(v)
        elif isinstance(x,str) and x.startswith(("http://","https://")):
            found.append(x)
    walk(obj)
    return found

def validate_media(url, headers=None):
    headers=headers or {}
    first=request(url,headers=headers,range_header="bytes=0-262143",max_body=350000)
    ct=first.get("headers",{}).get("Content-Type","")
    data=first.get("data",b"")
    body=data.decode("utf-8","replace")
    rec={"status":first.get("status"),"bytes":len(data),"content_type":ct,"host":urlparse(first.get("url") or url).netloc,"kind":"unknown","segment_status":None,"segment_bytes":0}
    base=first.get("url") or url

    if "#EXTM3U" in body or ".m3u8" in url.lower() or "mpegurl" in ct.lower():
        if "#EXTM3U" not in body:
            full=request(url,headers=headers,max_body=1000000)
            body=text(full); base=full.get("url") or url
            rec["status"]=full.get("status"); rec["bytes"]=len(full.get("data",b""))
        if "#EXTM3U" in body:
            rec["kind"]="hls"
            lines=[ln.strip() for ln in body.splitlines() if ln.strip()]
            uris=[ln for ln in lines if not ln.startswith("#")]
            child=next((u for u in uris if ".m3u8" in u.lower()),None)
            if child:
                cr=request(urljoin(base,child),headers=headers,max_body=1000000)
                cbody=text(cr)
                if "#EXTM3U" in cbody:
                    base=cr.get("url") or urljoin(base,child)
                    lines=[ln.strip() for ln in cbody.splitlines() if ln.strip()]
                    uris=[ln for ln in lines if not ln.startswith("#")]
            seg=next((u for u in uris if ".m3u8" not in u.lower() and ".mpd" not in u.lower()),None)
            if seg:
                sr=request(urljoin(base,seg),headers=headers,range_header="bytes=0-262143",max_body=350000)
                rec["segment_status"]=sr.get("status"); rec["segment_bytes"]=len(sr.get("data",b"")); rec["segment_type"]=sr.get("headers",{}).get("Content-Type","")
    elif ".mpd" in url.lower() or "dash+xml" in ct.lower() or "<MPD" in body:
        rec["kind"]="dash"
    elif "video/" in ct.lower() or any(x in url.lower() for x in [".mp4",".mkv",".webm"]):
        rec["kind"]="file"

    rec["proven"] = (
        (rec["kind"]=="hls" and rec["segment_status"] in (200,206) and rec["segment_bytes"]>0)
        or (rec["kind"] in ("file","dash") and rec["status"] in (200,206) and rec["bytes"]>0)
    )
    return rec

def result(provider,t,stage,status="PARTIAL",details=None,media=None):
    return {"provider":provider,"title":t["title"],"tmdb":t["tmdb"],"stage":stage,"classification":status,"details":details or {},"media":media or []}

def probe_vixsrc(t):
    provider="VixSrc"
    api="https://vixsrc.to/api/movie/"+t["tmdb"]
    h={"Accept":"application/json,*/*","Referer":"https://vixsrc.to","Origin":"https://vixsrc.to"}
    ar=request(api,headers=h)
    try: data=json.loads(text(ar))
    except: data={}
    src=data.get("src") if isinstance(data,dict) else None
    if ar.get("status")!=200 or not src:
        return result(provider,t,"api", "BLOCKED_OR_NO_SOURCE", {"api_status":ar.get("status"),"src":bool(src)})
    er=request(urljoin("https://vixsrc.to",src),headers={**h,"Accept":"text/html,*/*"})
    html=text(er)
    token=re.search(r'token["\']\s*:\s*["\']([^"\']+)',html)
    expires=re.search(r'expires["\']\s*:\s*["\']([^"\']+)',html)
    playlist=re.search(r'url\s*:\s*["\']([^"\']+)',html)
    if not (token and expires and playlist):
        return result(provider,t,"embed_tokens","EMBED_ONLY",{"api_status":ar.get("status"),"embed_status":er.get("status"),"token":bool(token),"expires":bool(expires),"playlist":bool(playlist)})
    base=htmlmod.unescape(playlist.group(1)).replace("\\/","/")
    master=base+("&" if "?" in base else "?")+"token="+quote(token.group(1),safe="")+"&expires="+quote(expires.group(1),safe="")+"&h=1"
    mh={"Referer":api,"Origin":"https://vixsrc.to","User-Agent":UA}
    mc=validate_media(master,mh)
    cls="PROVEN_MEDIA_BYTES" if mc["proven"] else "RESOLVED_MEDIA_UNPROVEN"
    return result(provider,t,"media",cls,{"api_status":ar.get("status"),"embed_status":er.get("status"),"media_host":mc.get("host")},[mc])

def probe_vidlink(t):
    provider="VidLink"
    enc=request("https://enc-dec.app/api/enc-vidlink?"+urlencode({"text":t["tmdb"]}),headers={"Accept":"application/json"})
    try: ed=json.loads(text(enc))
    except: ed={}
    code=ed.get("result") if isinstance(ed,dict) else None
    if not code:
        return result(provider,t,"encrypt","BLOCKED_OR_NO_SOURCE",{"enc_status":enc.get("status"),"has_code":False})
    api="https://vidlink.pro/api/b/movie/"+str(code)+"?multiLang=0"
    ar=request(api,headers={"Referer":"https://vidlink.pro","Origin":"https://vidlink.pro","Accept":"application/json,*/*"})
    try: data=json.loads(text(ar))
    except: data={}
    quals=((data or {}).get("stream") or {}).get("qualities") if isinstance(data,dict) else None
    urls=[]
    if isinstance(quals,dict):
        for q,e in quals.items():
            if isinstance(e,dict) and isinstance(e.get("url"),str): urls.append((q,e["url"]))
    media=[]
    for q,u in urls[:4]:
        mc=validate_media(u,{"Referer":"https://vidlink.pro/","Origin":"https://vidlink.pro","User-Agent":UA}); mc["quality"]=q; media.append(mc)
    cls="PROVEN_MEDIA_BYTES" if any(m["proven"] for m in media) else ("RESOLVED_MEDIA_UNPROVEN" if urls else "EMBED_ONLY")
    return result(provider,t,"media" if urls else "api",cls,{"enc_status":enc.get("status"),"api_status":ar.get("status"),"quality_count":len(urls)},media)

def vidnest_decode(data):
    lookup={c:i for i,c in enumerate(VIDNEST_ALPHABET)}
    out=bytearray(); i=0
    while i<len(data):
        ch=data[i:i+4]
        while len(ch)<4: ch+="="
        vals=[lookup.get(c,64) for c in ch]
        out.append((vals[0]<<2)|(vals[1]>>4))
        if vals[2]!=64: out.append(((vals[1]&15)<<4)|(vals[2]>>2))
        if vals[3]!=64: out.append(((vals[2]&3)<<6)|vals[3])
        i+=4
    s=out.decode("utf-8","ignore")
    try:return json.loads(s)
    except:
        m=re.search(r'\{.*\}',s,re.S)
        if m:
            try:return json.loads(m.group(0))
            except:pass
    return None

def first_stream_url(data):
    if isinstance(data,str):
        if data.startswith(("http://","https://")): return data
        try:data=json.loads(data)
        except:return None
    if not isinstance(data,dict): return None
    for key in ("sources","streams"):
        arr=data.get(key)
        if isinstance(arr,list):
            for x in arr:
                if isinstance(x,dict) and isinstance(x.get("url"),str): return x["url"]
    d=data.get("data")
    if isinstance(d,dict):
        dls=d.get("downloads")
        if isinstance(dls,list):
            good=[x for x in dls if isinstance(x,dict) and isinstance(x.get("url"),str)]
            if good:
                good.sort(key=lambda x: x.get("resolution",0) if isinstance(x.get("resolution",0),(int,float)) else 0,reverse=True)
                return good[0]["url"]
    if isinstance(data.get("url"),str): return data["url"]
    for u in extract_urls(data):
        if looks_media(u): return u
    return None

def probe_vidnest(t):
    provider="VidNest"
    successes=[]; backend_status=[]
    for name,path in VIDNEST_BACKENDS:
        u="https://new.vidnest.fun/"+path+"/movie/"+t["tmdb"]
        r=request(u,headers={"User-Agent":UA,"Accept":"application/json,*/*","Origin":"https://vidnest.fun","Referer":"https://vidnest.fun/"})
        backend_status.append((name,r.get("status")))
        if r.get("status")!=200: continue
        try:d=json.loads(text(r))
        except:continue
        if d.get("encrypted") is True:
            d=vidnest_decode(d.get("data",""))
        su=first_stream_url(d)
        if su: successes.append((name,su))
    media=[]
    for name,u in successes[:5]:
        mc=validate_media(u,{"Referer":"https://vidnest.fun/","Origin":"https://vidnest.fun","User-Agent":UA}); mc["backend"]=name; media.append(mc)
    cls="PROVEN_MEDIA_BYTES" if any(m["proven"] for m in media) else ("RESOLVED_MEDIA_UNPROVEN" if successes else "NO_SOURCE_FOR_TEST_TITLE")
    return result(provider,t,"media" if successes else "backend_api",cls,{"successful_backends":len(successes),"backend_status":backend_status},media)

def probe_vaplayer(t):
    provider="VaPlayer"
    params=urlencode({"imdb":t["imdb"],"type":"movie"})
    referer="https://nextgencloudfabric.com/embed/movie/"+t["imdb"]
    r=request("https://streamdata.vaplayer.ru/api.php?"+params,headers={"Referer":referer,"Origin":"https://nextgencloudfabric.com","Accept":"application/json,*/*"})
    try:d=json.loads(text(r))
    except:d={}
    urls=((d.get("data") or {}).get("stream_urls") if isinstance(d,dict) else None) or []
    urls=[u for u in urls if isinstance(u,str) and u.startswith(("http://","https://"))]
    media=[validate_media(u,{"Referer":"https://nextgencloudfabric.com/","Origin":"https://nextgencloudfabric.com","User-Agent":UA}) for u in urls[:4]]
    cls="PROVEN_MEDIA_BYTES" if any(m["proven"] for m in media) else ("RESOLVED_MEDIA_UNPROVEN" if urls else "NO_SOURCE_FOR_TEST_TITLE")
    return result(provider,t,"media" if urls else "api",cls,{"api_status":r.get("status"),"api_status_code":d.get("status_code") if isinstance(d,dict) else None,"url_count":len(urls)},media)

def probe_vidzee(t):
    provider="VidZee"
    servers=["dcloud","tik","ipcloud","v6:Hindi"]
    resolved=[]; statuses=[]
    for sr in servers:
        u="https://core.vidzee.wtf/streams/movie/"+t["tmdb"]+"?"+urlencode({"s":sr,"e":"0"})
        r=request(u,headers={"Referer":"https://player.vidzee.wtf/","Origin":"https://player.vidzee.wtf","Accept":"application/json,*/*"})
        statuses.append((sr,r.get("status")))
        if r.get("status")!=200: continue
        try:d=json.loads(text(r))
        except:continue
        if isinstance(d,dict) and isinstance(d.get("url"),str):
            resolved.append((sr,d["url"],d.get("headers") if isinstance(d.get("headers"),dict) else {}))
    media=[]
    for sr,u,extra in resolved[:4]:
        hh={"Referer":"https://player.vidzee.wtf/","Origin":"https://player.vidzee.wtf","User-Agent":UA}; hh.update(extra)
        mc=validate_media(u,hh); mc["server"]=sr; media.append(mc)
    cls="PROVEN_MEDIA_BYTES" if any(m["proven"] for m in media) else ("RESOLVED_MEDIA_UNPROVEN" if resolved else "NO_SOURCE_FOR_TEST_TITLE")
    return result(provider,t,"media" if resolved else "api",cls,{"server_status":statuses,"resolved_count":len(resolved)},media)

def multipart(fields):
    boundary="----ChatGPTMoviesProbeBoundary7MA4YWxkTrZu0gW"
    chunks=[]
    for k,v in fields.items():
        chunks.append("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+v+"\r\n")
    chunks.append("--"+boundary+"--\r\n")
    return ("".join(chunks)).encode(), "multipart/form-data; boundary="+boundary

def probe_mp4hydra(t):
    provider="MP4Hydra"
    variants=[t["slug"]+"-"+t["year"],t["slug"]]
    urls=[]; status=[]
    for slug in variants:
        payload=json.dumps([{"s":slug,"t":"movie","se":None,"ep":None}],separators=(",",":"))
        body,ctype=multipart({"v":"8","z":payload})
        r=request("https://mp4hydra.org/info2?v=8",method="POST",data=body,headers={"Content-Type":ctype,"Origin":"https://mp4hydra.org","Referer":"https://mp4hydra.org/movie/"+slug,"Accept":"*/*"})
        status.append((slug,r.get("status")))
        if r.get("status")!=200: continue
        try:d=json.loads(text(r))
        except:continue
        playlist=d.get("playlist") if isinstance(d,dict) else None
        servers=d.get("servers") if isinstance(d,dict) else None
        if isinstance(playlist,list) and isinstance(servers,dict):
            for sn in ("Beta","Beta#3"):
                base=servers.get(sn)
                if not isinstance(base,str): continue
                for item in playlist[:4]:
                    if isinstance(item,dict) and isinstance(item.get("src"),str):
                        urls.append((sn,urljoin(base,item["src"])))
            if urls: break
    media=[]
    for sn,u in urls[:6]:
        mc=validate_media(u,{"Referer":"https://mp4hydra.org/","Origin":"https://mp4hydra.org","User-Agent":UA}); mc["server"]=sn; media.append(mc)
    cls="PROVEN_MEDIA_BYTES" if any(m["proven"] for m in media) else ("RESOLVED_MEDIA_UNPROVEN" if urls else "NO_SOURCE_FOR_TEST_TITLE")
    return result(provider,t,"media" if urls else "api",cls,{"request_status":status,"resolved_count":len(urls)},media)

def probe_streamflix(t):
    provider="StreamFlix"
    dr=request("https://api.streamflix.app/data.json",headers={"Accept":"application/json,*/*"})
    cr=request("https://api.streamflix.app/config/config-streamflixapp.json",headers={"Accept":"application/json,*/*"})
    try:dd=json.loads(text(dr))
    except:dd={}
    try:cfg=json.loads(text(cr))
    except:cfg={}
    items=dd.get("data") if isinstance(dd,dict) else []
    match=None
    if isinstance(items,list):
        match=next((x for x in items if isinstance(x,dict) and str(x.get("tmdb"))==t["tmdb"]),None)
    bases=cfg.get("download") if isinstance(cfg,dict) else None
    urls=[]
    if match and isinstance(match.get("movielink"),str) and isinstance(bases,list):
        for b in bases:
            if isinstance(b,str): urls.append(urljoin(b,match["movielink"]))
    media=[validate_media(u,{"User-Agent":UA}) for u in urls[:4]]
    cls="PROVEN_MEDIA_BYTES" if any(m["proven"] for m in media) else ("RESOLVED_MEDIA_UNPROVEN" if urls else "NO_SOURCE_FOR_TEST_TITLE")
    return result(provider,t,"media" if urls else "catalog",cls,{"data_status":dr.get("status"),"config_status":cr.get("status"),"catalog_items":len(items) if isinstance(items,list) else None,"matched":bool(match),"download_bases":len(bases) if isinstance(bases,list) else 0},media)

PROBES=[probe_vixsrc,probe_vidlink,probe_vidnest,probe_vaplayer,probe_vidzee,probe_mp4hydra,probe_streamflix]

def main():
    out=[]
    for t in TESTS:
        for fn in PROBES:
            print("::group::"+t["title"]+" :: "+fn.__name__)
            try:r=fn(t)
            except Exception as e:r=result(fn.__name__,t,"exception","FAILED",{"error":repr(e)})
            out.append(r)
            print(json.dumps({"provider":r["provider"],"title":r["title"],"stage":r["stage"],"classification":r["classification"],"details":r["details"],"media":[{"host":m.get("host"),"kind":m.get("kind"),"status":m.get("status"),"bytes":m.get("bytes"),"segment_status":m.get("segment_status"),"segment_bytes":m.get("segment_bytes"),"proven":m.get("proven")} for m in r["media"]]},indent=2))
            print("::endgroup::")

    Path("movie-source-probe-v2.json").write_text(json.dumps(out,indent=2),encoding="utf-8")
    lines=["# Movies source probe v2","",
           "PROVEN_MEDIA_BYTES means the resolver produced a media URL and the runner successfully transferred file/DASH bytes or an HLS media segment. Resolved URLs and tokens are deliberately not written to the report.","",
           "| Provider | Test title | Stage | Classification | Proven media checks |",
           "|---|---|---|---|---:|"]
    for r in out:
        lines.append("| "+r["provider"]+" | "+r["title"]+" | "+r["stage"]+" | "+r["classification"]+" | "+str(sum(1 for m in r["media"] if m.get("proven")))+" |")
    lines+=["","## Evidence details",""]
    for r in out:
        lines.append("### "+r["provider"]+" — "+r["title"])
        lines.append("- Stage: "+r["stage"])
        lines.append("- Classification: "+r["classification"])
        lines.append("- Details: "+json.dumps(r["details"],ensure_ascii=False))
        for m in r["media"]:
            lines.append("- Media: host="+str(m.get("host"))+" kind="+str(m.get("kind"))+" status="+str(m.get("status"))+" bytes="+str(m.get("bytes"))+" segment_status="+str(m.get("segment_status"))+" segment_bytes="+str(m.get("segment_bytes"))+" proven="+str(m.get("proven")))
        lines.append("")
    Path("movie-source-probe-v2.md").write_text("\n".join(lines),encoding="utf-8")

if __name__=="__main__":
    main()
