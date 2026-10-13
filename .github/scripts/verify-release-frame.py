import struct, sys, zlib
path, report = sys.argv[1:3]
data=open(path,'rb').read()
if not data.startswith(b'\x89PNG\r\n\x1a\n'):
    raise SystemExit('release screenshot is not PNG')
pos=8; width=height=depth=color=interlace=None; compressed=bytearray()
while pos+12 <= len(data):
    length=struct.unpack('>I',data[pos:pos+4])[0]
    kind=data[pos+4:pos+8]; payload=data[pos+8:pos+8+length]; pos += 12+length
    if kind==b'IHDR':
        width,height,depth,color,_,_,interlace=struct.unpack('>IIBBBBB',payload)
    elif kind==b'IDAT': compressed.extend(payload)
    elif kind==b'IEND': break
channels={0:1,2:3,4:2,6:4}.get(color)
if not width or not height or depth!=8 or interlace!=0 or channels is None:
    raise SystemExit('unsupported release screenshot format')
raw=zlib.decompress(bytes(compressed)); stride=width*channels; prior=bytearray(stride); offset=0
mn=255; mx=0; nonblack=0; unique=set(); step=max(1,(width*height)//100000)
def paeth(a,b,c):
    p=a+b-c; pa=abs(p-a); pb=abs(p-b); pc=abs(p-c)
    return a if pa<=pb and pa<=pc else b if pb<=pc else c
for y in range(height):
    filt=raw[offset]; offset+=1
    scan=bytearray(raw[offset:offset+stride]); offset+=stride
    recon=bytearray(stride)
    for i,v in enumerate(scan):
        left=recon[i-channels] if i>=channels else 0
        up=prior[i]; ul=prior[i-channels] if i>=channels else 0
        if filt==0: out=v
        elif filt==1: out=(v+left)&255
        elif filt==2: out=(v+up)&255
        elif filt==3: out=(v+((left+up)//2))&255
        elif filt==4: out=(v+paeth(left,up,ul))&255
        else: raise SystemExit('unsupported PNG filter')
        recon[i]=out
    for x in range(width):
        i=x*channels
        rgb=(recon[i],)*3 if color in (0,4) else tuple(recon[i:i+3])
        b=max(rgb); mn=min(mn,b); mx=max(mx,b)
        if b>12: nonblack+=1
        if (y*width+x)%step==0 and len(unique)<256: unique.add(rgb)
    prior=recon
fraction=nonblack/(width*height)
with open(report,'w') as f:
    f.write(f'size={width}x{height}\nbrightness_min={mn}\nbrightness_max={mx}\n')
    f.write(f'nonblack_fraction={fraction:.6f}\nsampled_unique_colors={len(unique)}\n')
# API 36 ATD/SwiftShader can lose only the host framebuffer after a valid UI
# hierarchy. Preserve that diagnostic instead of pretending it is a product frame.
if mx<=12 or mx-mn<=6 or fraction<0.01 or len(unique)<8:
    raise SystemExit(2)
