#!/usr/bin/env python3
"""Reject Android screenshots obscured by bright System UI 'not responding' dialogs.

Sound Atlas is a dark fullscreen Canvas application. Pixel in viewport center
must therefore not be near white in a healthy editor/sheet screenshot.
Pure Python stdlib PNG parser: avoids a runtime pip dependency in emulator CI.
"""
import struct
import sys
import zlib
from pathlib import Path

def center_rgb(path):
    data=Path(path).read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):raise ValueError("Not PNG")
    i=8;compressed=[];width=height=channels=0
    while i<len(data):
        n=struct.unpack_from(">I",data,i)[0];i+=4
        typ=data[i:i+4];i+=4
        chunk=data[i:i+n];i+=n+4
        if typ==b"IHDR":
            width,height,bits,color,compression,filtermode,interlace=struct.unpack(">IIBBBBB",chunk)
            if bits!=8 or color not in (2,6) or interlace:raise ValueError("Unsupported PNG")
            channels=3 if color==2 else 4
        elif typ==b"IDAT":compressed.append(chunk)
        elif typ==b"IEND":break
    raw=zlib.decompress(b"".join(compressed))
    stride=width*channels;previous=bytearray(stride);offset=0
    for _ in range(height//2+1):
        mode=raw[offset];offset+=1
        row=bytearray(raw[offset:offset+stride]);offset+=stride
        if mode==1:
            for x in range(channels,stride):row[x]=(row[x]+row[x-channels])&255
        elif mode==2:
            for x in range(stride):row[x]=(row[x]+previous[x])&255
        elif mode==3:
            for x in range(stride):
                left=row[x-channels] if x>=channels else 0
                row[x]=(row[x]+(left+previous[x])//2)&255
        elif mode==4:
            for x in range(stride):
                a=row[x-channels] if x>=channels else 0
                b=previous[x];c=previous[x-channels] if x>=channels else 0
                p=a+b-c;pa=abs(p-a);pb=abs(p-b);pc=abs(p-c)
                row[x]=(row[x]+(a if pa<=pb and pa<=pc else b if pb<=pc else c))&255
        elif mode!=0:raise ValueError("PNG filter unsupported")
        previous=row
    index=(width//2)*channels
    return tuple(previous[index:index+3])

if __name__=="__main__":
    rgb=center_rgb(sys.argv[1])
    print("Screenshot center pixel:",rgb)
    if min(rgb)>215:
        sys.exit("INVALID_SCREEN: bright platform dialog obscures Sound Atlas")
