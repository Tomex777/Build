#!/usr/bin/env python3
"""Download VERIFIED CC0 preview recordings and MIT measured HRIRs for offline APK.
No keys, permissions, or runtime Internet connection are required by the Android app.
Requires Python 3, ffmpeg, public internet access during the build. Fail-closed on licensing.
"""
import html, pathlib, re, subprocess, tempfile, urllib.request, struct, json

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
AUDIO = ASSETS / 'audio'
AUDIO.mkdir(parents=True,exist_ok=True)
# Each entry was independently confirmed as a CC0 Freesound recording.
# Title/creator/license are also written to the in-app attribution manifest.
RECORDINGS = {
 'rain':       ('yzhamua',188655),
 'thunder':    ('roboroo',436790),
 'ocean':      ('Noted451',531015),
 'wind':       ('Sheyvan',479430),
 'fire':       ('StevenMyat_',676385),
 'chimes':     ('OPMartin',324325),
 'birds':      ('guidofm',509176),
 'night':      ('nick121087',476887),
 'stream':     ('ciccarelli',569457),
 'waterfall':  ('Masou',250943),
 'leaves':     ('Anya_Media',523389),
 'owl':        ('mael46',573771),
 'frogs':      ('MLaudio',521666),
 'traffic':    ('GustavoGE',404115),
 'drizzle':    ('Andron827',464334),
 'sleet':      ('Ambient-X',677545),
 'cafe':       ('aidansamuel',540299),
 'train':      ('marion3825',510822),
}
EVENTS={'thunder','owl','chimes'}

def get(url):
    request=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0 (EchoSpace offline asset build)'})
    with urllib.request.urlopen(request,timeout=45) as response:
        return response.read()

def ensure_recordings():
    credits=[]
    for kind,(user,id) in RECORDINGS.items():
        page=f'https://freesound.org/people/{user}/sounds/{id}/'
        page_text=html.unescape(get(page).decode('utf-8'))
        if 'Creative Commons 0' not in page_text:
            raise RuntimeError(f'FAIL CLOSED: {kind} is not confirmed CC0: {page}')
        links=re.findall(r'https://cdn\.freesound\.org/previews/[^\"\s<>]+-hq\.mp3',page_text)
        if not links:
            links=re.findall(r'https://cdn\.freesound\.org/previews/[^\"\s<>]+-lq\.mp3',page_text)
        if not links:
            raise RuntimeError(f'No public audio preview for {kind}: {page}')
        preview=links[0]
        with tempfile.NamedTemporaryFile(suffix='.mp3') as temp:
            temp.write(get(preview)); temp.flush()
            dest=AUDIO/(kind+'.pcm')
            # Mono 22.05kHz, signed 16bit little-endian; interpreted with interpolation
            # by the 44.1kHz HRTF spatializer. Capped to limit APK/memory cost.
            duration=20 if kind in EVENTS else 48
            subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',temp.name,
                 '-t',str(duration),'-af','highpass=f=36,volume=0.85',
                 '-ac','1','-ar','22050','-f','s16le',str(dest)],check=True)
        if dest.stat().st_size < 12000:
            raise RuntimeError(f'{kind}: too-short recording')
        credits.append(dict(kind=kind,creator=user,sound_id=id,license='CC0-1.0',url=page,
                            filename=dest.name,bytes=dest.stat().st_size,
                            seconds=round(dest.stat().st_size / 44100.0, 2)))
        print('CC0_RECORDING',kind,id,dest.stat().st_size,flush=True)
    (ASSETS/'recording_credits.json').write_text(json.dumps(credits,indent=2))
    return credits

def ensure_hrtf():
    # MIT KEMAR measured impulse responses (Gardner & Martin, MIT Media Lab, 1994).
    # Repackaged by Aristotel Digenis in mit-hrtf-lib (MIT-licensed source).
    # azimuths measured at 0,90,180 degrees, elevation 0, each ear 128 taps.
    url='https://raw.githubusercontent.com/jhonmark/mit-hrtf-lib/master/source/normal/mit_hrtf_normal_44100.h'
    header=get(url).decode('latin-1')
    # Seven actual measured azimuths, rather than three for the entire 180° arc.
    targets=['H0e000a.wav','H0e030a.wav','H0e060a.wav','H0e090a.wav',
             'H0e120a.wav','H0e150a.wav','H0e180a.wav']
    output=bytearray(b'EHR2')
    for target in targets:
        tag='// '+target
        if tag not in header: raise RuntimeError('Missing measured HRIR: '+target)
        block=header[header.index(tag):][:8000]
        pairs=re.findall(r'\{\s*([0-9,\s-]+)\s*\}',block)
        if len(pairs)<2: raise RuntimeError('HRIR missing ears: '+target)
        for a in pairs[:2]:
            vals=[int(v.strip()) for v in a.split(',') if v.strip()]
            if len(vals)!=128: raise RuntimeError(f'HRIR {target}: {len(vals)} taps, expected 128')
            output+=struct.pack('<128h',*vals)
    (ASSETS/'kemar_44100.hrir').write_bytes(output)
    print('MEASURED_HRIR',len(output),flush=True)

if __name__=='__main__':
    ensure_recordings(); ensure_hrtf()
    print('AUDIO_ASSETS_READY')
