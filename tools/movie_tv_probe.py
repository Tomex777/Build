#!/usr/bin/env python3
import json, re, html as htmlmod
from pathlib import Path
from urllib.parse import urlencode, urljoin, urlparse, quote
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError

UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131 Safari/537.36"
T={"title":"The Beverly Hillbillies S01E01","tmdb":"1930","imdb":"tt0055662","season":"1","episode":"1"}
VIDNEST_ALPHABET="RB0fpH8ZEyVLkv7c2i6MAJ5u3IKFDxlS1NTsnGaqmXYdUrtzjwObCgQP94hoeW+/="
BACKENDS=[("MoviesAPI","moviesapi"),("HollyMovieHD","hollymoviehd"),("AllMovies","allmovies"),("VidLink","vidlink"),("KlikXXI","klikxxi"),("Movies4F","movies4f"),("MovieBox","moviebox"),("Videasy","videasy"),("Movies5F","movies5f")]
opener=build_opener(HTTPRedirectHandler())

def req(url,headers=None,range_header=None,max_body=1000000):
    h={"User-Agent":UA,"Accept":"*/*","Accept-Encoding":"identity"}
    if headers:h.update(headers)
    if range_header:h["Range"]=range_header
    try:
        with opener.open(Request(url,headers=h),timeout=20) as r:
            return {"status":getattr(r,"status",200),"url":r.geturl(),"headers":dict(r.headers.items()),"data":r.read(max_body)}
    except HTTPError as e:
        try:b=e.read(min(max_body,300000))
        except:b=b""
        return {"status":e.code,"url":getattr(e,"url",url),"headers":dict(getattr(e,"headers",{}) or {}),"data":b,"error":str(e)}
    except Exception as e:
        return {"status":None,"url":url,"headers":{},"data":b"","error":repr(e)}

def txt(r):return r.get("data",b"").decode("utf-8","replace")

def validate(url,headers):
    r=req(url,headers,range_header="bytes=0-262143",max_body=350000)
    ct=r.get("headers",{}).get("Content-Type","")
    b=r.get("data",b""); s=b.decode("utf-8","replace"); base=r.get("url") or url
    rec={"host":urlparse(base).netloc,"status":r.get("status"),"bytes":len(b),"kind":"unknown","segment_status":None,"segment_bytes":0}
    if "#EXTM3U" in s or ".m3u8" in url.lower() or "mpegurl" in ct.lower():
        if "#EXTM3U" not in s:
            r=req(url,headers,max_body=1000000); s=txt(r); base=r.get("url") or url
            rec["status"]=r.get("status");rec["bytes"]=len(r.get("data",b""))
        if "#EXTM3U" in s:
            rec["kind"]="hls"
            lines=[x.strip() for x in s.splitlines() if x.strip()]
            uris=[x for x in lines if not x.startswith("#")]
            child=next((x for x in uris if ".m3u8" in x.lower()),None)
            if child:
                cr=req(urljoin(base,child),headers,max_body=1000000)
                cs=txt(cr)
                if "#EXTM3U" in cs:
                    base=cr.get("url") or urljoin(base,child)
                    lines=[x.strip() for x in cs.splitlines() if x.strip()]
                    uris=[x for x in lines if not x.startswith("#")]
            seg=next((x for x in uris if ".m3u8" not in x.lower()),None)
            if seg:
                sr=req(urljoin(base,seg),headers,range_header="bytes=0-262143",max_body=350000)
                rec["segment_status"]=sr.get("status");rec["segment_bytes"]=len(sr.get("data",b""))
    elif "video/" in ct.lower() or any(x in url.lower() for x in [".mp4",".mkv",".webm"]):
        rec["kind"]="file"
    rec["proven"]=((rec["kind"]=="hls" and rec["segment_status"] in (200,206) and rec["segment_bytes"]>0) or (rec["kind"]=="file" and rec["status"] in (200,206) and rec["bytes"]>0))
    return rec

def vixsrc():
    api="https://vixsrc.to/api/tv/{}/{}/{}".format(T["tmdb"],T["season"],T["episode"])
    h={"Referer":"https://vixsrc.to","Origin":"https://vixsrc.to","Accept":"application/json,*/*"}
    ar=req(api,h)
    try:d=json.loads(txt(ar))
    except:d={}
    src=d.get("src") if isinstance(d,dict) else None
    if not src:return {"provider":"VixSrc","classification":"NO_SOURCE","details":{"api_status":ar.get("status")},"media":[]}
    er=req(urljoin("https://vixsrc.to",src),{**h,"Accept":"text/html,*/*"})
    html=txt(er)
    token=re.search(r'token["\']\s*:\s*["\']([^"\']+)',html)
    exp=re.search(r'expires["\']\s*:\s*["\']([^"\']+)',html)
    pl=re.search(r'url\s*:\s*["\']([^"\']+)',html)
    if not(token and exp and pl):return {"provider":"VixSrc","classification":"EMBED_ONLY","details":{"api_status":ar.get("status"),"embed_status":er.get("status")},"media":[]}
    base=htmlmod.unescape(pl.group(1)).replace("\\/","/")
    master=base+("&" if "?" in base else "?")+"token="+quote(token.group(1),safe="")+"&expires="+quote(exp.group(1),safe="")+"&h=1"
    m=validate(master,{"Referer":api,"Origin":"https://vixsrc.to","User-Agent":UA})
    return {"provider":"VixSrc","classification":"PROVEN_MEDIA_BYTES" if m["proven"] else "RESOLVED_MEDIA_UNPROVEN","details":{"api_status":ar.get("status"),"embed_status":er.get("status")},"media":[m]}

def vidlink():
    enc=req("https://enc-dec.app/api/enc-vidlink?"+urlencode({"text":T["tmdb"]}),{"Accept":"application/json"})
    try:e=json.loads(txt(enc))
    except:e={}
    code=e.get("result") if isinstance(e,dict) else None
    if not code:return {"provider":"VidLink","classification":"NO_SOURCE","details":{"enc_status":enc.get("status")},"media":[]}
    api="https://vidlink.pro/api/b/tv/{}/{}/{}?multiLang=0".format(code,T["season"],T["episode"])
    ar=req(api,{"Referer":"https://vidlink.pro","Origin":"https://vidlink.pro","Accept":"application/json,*/*"})
    try:d=json.loads(txt(ar))
    except:d={}
    q=((d.get("stream") or {}).get("qualities") if isinstance(d,dict) else None) or {}
    urls=[(k,v.get("url")) for k,v in q.items() if isinstance(v,dict) and isinstance(v.get("url"),str)]
    media=[]
    for k,u in urls[:4]:
        m=validate(u,{"Referer":"https://vidlink.pro/","Origin":"https://vidlink.pro","User-Agent":UA});m["quality"]=k;media.append(m)
    cls="PROVEN_MEDIA_BYTES" if any(x["proven"] for x in media) else ("RESOLVED_MEDIA_UNPROVEN" if urls else "NO_SOURCE")
    return {"provider":"VidLink","classification":cls,"details":{"enc_status":enc.get("status"),"api_status":ar.get("status"),"quality_count":len(urls)},"media":media}

def vidnest_decode(data):
    lookup={c:i for i,c in enumerate(VIDNEST_ALPHABET)};out=bytearray()
    for i in range(0,len(data),4):
        ch=data[i:i+4]
        while len(ch)<4:ch+="="
        v=[lookup.get(c,64) for c in ch]
        out.append((v[0]<<2)|(v[1]>>4))
        if v[2]!=64:out.append(((v[1]&15)<<4)|(v[2]>>2))
        if v[3]!=64:out.append(((v[2]&3)<<6)|v[3])
    s=out.decode("utf-8","ignore")
    try:return json.loads(s)
    except:return None

def urls_in(x):
    out=[]
    def walk(v):
        if isinstance(v,dict):
            for z in v.values():walk(z)
        elif isinstance(v,list):
            for z in v:walk(z)
        elif isinstance(v,str) and v.startswith(("http://","https://")):out.append(v)
    walk(x);return out

def first_media(d):
    if isinstance(d,dict):
        for k in ("sources","streams"):
            a=d.get(k)
            if isinstance(a,list):
                for x in a:
                    if isinstance(x,dict) and isinstance(x.get("url"),str):return x["url"]
        if isinstance(d.get("url"),str):return d["url"]
        for u in urls_in(d):
            if any(z in u.lower() for z in [".m3u8",".mp4",".mkv",".webm","stream","video"]):return u
    return None

def vidnest():
    resolved=[];statuses=[]
    for name,path in BACKENDS:
        u="https://new.vidnest.fun/{}/tv/{}/{}/{}".format(path,T["tmdb"],T["season"],T["episode"])
        r=req(u,{"Origin":"https://vidnest.fun","Referer":"https://vidnest.fun/","Accept":"application/json,*/*"})
        statuses.append([name,r.get("status")])
        if r.get("status")!=200:continue
        try:d=json.loads(txt(r))
        except:continue
        if d.get("encrypted") is True:d=vidnest_decode(d.get("data",""))
        u2=first_media(d)
        if u2:resolved.append([name,u2])
    media=[]
    for name,u in resolved[:5]:
        m=validate(u,{"Referer":"https://vidnest.fun/","Origin":"https://vidnest.fun","User-Agent":UA});m["backend"]=name;media.append(m)
    cls="PROVEN_MEDIA_BYTES" if any(x["proven"] for x in media) else ("RESOLVED_MEDIA_UNPROVEN" if resolved else "NO_SOURCE")
    return {"provider":"VidNest","classification":cls,"details":{"backend_status":statuses,"resolved_count":len(resolved)},"media":media}

def vaplayer():
    q=urlencode({"imdb":T["imdb"],"type":"tv","season":T["season"],"episode":T["episode"]})
    ref="https://nextgencloudfabric.com/embed/tv/{}/{}/{}".format(T["imdb"],T["season"],T["episode"])
    r=req("https://streamdata.vaplayer.ru/api.php?"+q,{"Referer":ref,"Origin":"https://nextgencloudfabric.com","Accept":"application/json,*/*"})
    try:d=json.loads(txt(r))
    except:d={}
    us=((d.get("data") or {}).get("stream_urls") if isinstance(d,dict) else None) or []
    media=[validate(u,{"Referer":"https://nextgencloudfabric.com/","Origin":"https://nextgencloudfabric.com","User-Agent":UA}) for u in us[:4] if isinstance(u,str)]
    cls="PROVEN_MEDIA_BYTES" if any(x["proven"] for x in media) else ("RESOLVED_MEDIA_UNPROVEN" if us else "NO_SOURCE")
    return {"provider":"VaPlayer","classification":cls,"details":{"api_status":r.get("status"),"url_count":len(us)},"media":media}

def main():
    rows=[]
    for fn in (vixsrc,vidlink,vidnest,vaplayer):
        try:r=fn()
        except Exception as e:r={"provider":fn.__name__,"classification":"FAILED","details":{"error":repr(e)},"media":[]}
        rows.append(r)
        safe={**r,"media":[{"host":m.get("host"),"kind":m.get("kind"),"status":m.get("status"),"bytes":m.get("bytes"),"segment_status":m.get("segment_status"),"segment_bytes":m.get("segment_bytes"),"proven":m.get("proven")} for m in r.get("media",[])]}
        print(json.dumps(safe,indent=2))
    Path("tv-source-probe.json").write_text(json.dumps({"test":T,"results":rows},indent=2),encoding="utf-8")
    lines=["# TV source probe","", "Test: The Beverly Hillbillies S01E01 (public-domain Season 1 test case).","",
           "| Provider | Classification | Proven media checks |","|---|---|---:|"]
    for r in rows:lines.append("| "+r["provider"]+" | "+r["classification"]+" | "+str(sum(1 for m in r.get("media",[]) if m.get("proven")))+" |")
    lines+=["","## Evidence",""]
    for r in rows:
        lines.append("### "+r["provider"]);lines.append("- Classification: "+r["classification"]);lines.append("- Details: "+json.dumps(r["details"]))
        for m in r.get("media",[]):lines.append("- Media: host="+str(m.get("host"))+" kind="+str(m.get("kind"))+" status="+str(m.get("status"))+" bytes="+str(m.get("bytes"))+" segment_status="+str(m.get("segment_status"))+" segment_bytes="+str(m.get("segment_bytes"))+" proven="+str(m.get("proven")))
        lines.append("")
    Path("tv-source-probe.md").write_text("\n".join(lines),encoding="utf-8")

if __name__=="__main__":main()
