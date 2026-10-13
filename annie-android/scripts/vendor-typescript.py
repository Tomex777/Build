#!/usr/bin/env python3
"""Prepare pinned compiler assets at build time; the resulting APK runs offline."""
import hashlib
import io
from pathlib import Path
import tarfile
import urllib.request

VERSION = "5.9.3"
ARCHIVE_SHA = "10e108c9cf7d5f2879053dff18515fb405abf2ccef63eaaf017d9c571687a1d3"
COMPILER_SHA = "3ae902c92cc44dace175c0e69e13a4b0899f6983c6121d76b9ab8dd5795e7675"
ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets/typescript"

def prepare():
    target = ASSETS / "typescript.js"
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == COMPILER_SHA:
        return
    with urllib.request.urlopen(f"https://registry.npmjs.org/typescript/-/typescript-{VERSION}.tgz", timeout=60) as response:
        data = response.read(5000001)
    if len(data) > 5000000 or hashlib.sha256(data).hexdigest() != ARCHIVE_SHA:
        raise RuntimeError("TypeScript archive failed integrity verification")
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        member = archive.extractfile("package/lib/typescript.js")
        if member is None:
            raise RuntimeError("TypeScript compiler missing from pinned archive")
        compiler = member.read(10000001)
    if hashlib.sha256(compiler).hexdigest() != COMPILER_SHA:
        raise RuntimeError("TypeScript compiler failed integrity verification")
    ASSETS.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".tmp")
    temporary.write_bytes(compiler)
    temporary.replace(target)

if __name__ == "__main__":
    prepare()
    print(f"TypeScript {VERSION} compiler verified")
