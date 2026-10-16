#!/usr/bin/env python3
"""Prepare authentic VRoid anime VRM for Filament WITHOUT touching any geometry.

The licensed source GLB uses JSON-escaped MIME types, e.g. image\\/png. That is
legal JSON, but Filament's Android cgltf path treats the backslash literally,
logging 'Missing texture provider for image\\/png'. Normalize only the
serialized JSON chunk; preserve the binary image/mesh/skin data byte-for-byte.
"""
import json
import struct
import sys
from pathlib import Path

source, target = map(Path, sys.argv[1:3])
data = source.read_bytes()
magic, version, total = struct.unpack_from("<III", data)
assert (magic, version, total) == (0x46546C67, 2, len(data))
json_length, json_magic = struct.unpack_from("<II", data, 12)
assert json_magic == 0x4E4F534A
old = data[20:20 + json_length].rstrip(b" ")
old_gltf = json.loads(old)
assert "VRM" in old_gltf.get("extensions", {})
body = old.replace(b"image\\/png", b"image/png").replace(
    b"image\\/jpeg", b"image/jpeg"
)
assert b"image\\/png" not in body and b"image\\/jpeg" not in body
assert json.loads(body) == old_gltf, "Metadata may not be changed by MIME normalizer"
body += b" " * ((-len(body)) % 4)
tail = data[20 + json_length:]
new_total = 12 + 8 + len(body) + len(tail)
result = (
    struct.pack("<III", magic, version, new_total)
    + struct.pack("<II", len(body), json_magic)
    + body
    + tail
)
assert result[20 + len(body):] == data[20 + json_length:]
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(result)
print(f"Normalized VRoid escaped MIME IDs: {len(data)} -> {len(result)} bytes "
      "(preserved rig/mesh/texture binaries exactly)")
