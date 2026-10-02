"""Independently verify public APK/AAB bytes; never reads signing credentials."""
import argparse, base64, hashlib, json, re, struct, subprocess, tempfile, zipfile
from pathlib import Path

def digest(data):
    return hashlib.sha256(data).hexdigest()

def headers(data):
    lines=data.decode('utf-8').replace('\r\n','\n').splitlines()
    unfolded=[]
    for line in lines:
        if line.startswith(' '): unfolded[-1]+=line[1:]
        elif line: unfolded.append(line)
    return dict(line.split(': ',1) for line in unfolded)

def verify(path, jar):
    result={'file':path.name,'sha256':digest(path.read_bytes())}
    with zipfile.ZipFile(path) as z:
        if path.suffix=='.apk':
            run=subprocess.run(['java','-jar',str(jar),'verify','--verbose','--print-certs',str(path)],capture_output=True,text=True,check=True)
            result['signature_verification']=run.stdout
            result['signer_sha256']=re.search(r'certificate SHA-256 digest: ([a-f0-9]+)',run.stdout).group(1)
            result['signer_sha1']=re.search(r'certificate SHA-1 digest: ([a-f0-9]+)',run.stdout).group(1)
            result['abis']=sorted({n.split('/')[1] for n in z.namelist() if n.startswith('lib/')})
            native=[]
            for n in z.namelist():
                if not n.startswith(('lib/arm64-v8a/','lib/x86_64/')) or not n.endswith('.so'): continue
                data=z.read(n); assert data[:5]==b'\x7fELF\x02',n
                endian='<' if data[5]==1 else '>'
                phoff=struct.unpack_from(endian+'Q',data,32)[0]
                entsize,count=struct.unpack_from(endian+'HH',data,54)
                align=[struct.unpack_from(endian+'Q',data,phoff+i*entsize+48)[0] for i in range(count) if struct.unpack_from(endian+'I',data,phoff+i*entsize)[0]==1]
                assert align and min(align)>=16384,(n,align)
                native.append({'library':n,'elf_load_alignment':align})
            result['native_16kb']=native
        else:
            manifest=z.read('META-INF/MANIFEST.MF')
            for section in manifest.replace(b'\r\n',b'\n').split(b'\n\n')[1:]:
                h=headers(section)
                if 'Name' not in h: continue
                assert base64.b64decode(h['SHA-256-Digest'])==hashlib.sha256(z.read(h['Name'])).digest(),h['Name']
            names=[n for n in z.namelist() if n.upper().endswith('.SF') and n.startswith('META-INF/')]
            assert len(names)==1,names
            sf=z.read(names[0]); h=headers(sf.split(b'\r\n\r\n')[0])
            assert base64.b64decode(h['SHA-256-Digest-Manifest'])==hashlib.sha256(manifest).digest()
            with tempfile.TemporaryDirectory() as td:
                td=Path(td); (td/'signature.sf').write_bytes(sf)
                (td/'signature.rsa').write_bytes(z.read(names[0][:-3]+'.RSA'))
                subprocess.run(['openssl','cms','-verify','-inform','DER','-in',str(td/'signature.rsa'),'-content',str(td/'signature.sf'),'-noverify','-binary','-out',str(td/'verified')],check=True,capture_output=True)
            cert=subprocess.run(['keytool','-printcert','-jarfile',str(path)],check=True,capture_output=True,text=True).stdout
            result['signer_sha256']=re.search(r'SHA256: ([A-F0-9:]+)',cert).group(1).replace(':','').lower()
            result['certificate']=cert
            result['signature_verification']='PASS: PKCS7 signature, full manifest digest and every manifest entry SHA256 verified'
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('--apksigner-jar',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('files',nargs='+',type=Path);a=p.parse_args()
    records=[verify(f,a.apksigner_jar) for f in a.files]
    a.output.write_text(json.dumps(records,indent=2)+'\n')
    print(f'Verified {len(records)} release artifacts')
