#!/usr/bin/env python3
"""Fail-closed validation for the actual CC0 VRoid anime model (binary VRM/glTF).

The .vrm source has been copied verbatim to mise_anime.glb; no painting or
static image is substituted for the editable skinned 3D character.
"""
import argparse
import json
import struct
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("path", type=Path)
args = parser.parse_args()
raw = args.path.read_bytes()
assert 1_000_000 < len(raw) < 40_000_000, "Unexpected VRoid model size"
magic, version, length = struct.unpack_from("<III", raw)
assert (magic, version, length) == (0x46546C67, 2, len(raw)), "Invalid binary glTF header"
json_length, kind = struct.unpack_from("<II", raw, 12)
assert kind == 0x4E4F534A, "Missing JSON chunk"
gltf = json.loads(raw[20:20 + json_length])
bin_offset = 20 + json_length
bin_length, bin_kind = struct.unpack_from("<II", raw, bin_offset)
assert bin_kind == 0x004E4942, "Missing binary 3D geometry chunk"
assert bin_offset + 8 + bin_length == len(raw), "Malformed GLB length"
assert gltf["buffers"][0]["byteLength"] <= bin_length
assert "VRM" in gltf.get("extensions", {}), "Expected authentic VRoid VRM metadata"
skins = gltf.get("skins", [])
joints = {i for skin in skins for i in skin.get("joints", [])}
assert len(joints) >= 40, f"Not a poseable humanoid: {len(joints)} joints"
names = {gltf["nodes"][i].get("name", "").lower() for i in joints}
normalized = {n.replace("_", "").replace("-", "").replace(".", "") for n in names}
for semantic in ("head", "upperarm", "lowerarm", "upperleg", "lowerleg"):
    assert any(semantic in n for n in normalized), f"Missing anime rig control {semantic}: {sorted(names)}"
assert len(gltf.get("materials", [])) >= 1, "No colored/textured character materials"
assert len(gltf.get("images", [])) >= 1, "This must be a textured anime avatar"
assert len(gltf.get("meshes", [])) >= 1
skinned_primitives = [
    primitive
    for node in gltf["nodes"] if "skin" in node and "mesh" in node
    for primitive in gltf["meshes"][node["mesh"]]["primitives"]
    if {"POSITION", "NORMAL", "JOINTS_0", "WEIGHTS_0"} <= primitive["attributes"].keys()
]
assert skinned_primitives, "Character lacks skinned 3D meshes"
for acc in gltf["accessors"]:
    if "bufferView" not in acc:
        continue  # Sparse/zero-initialized glTF accessors may legitimately omit it.
    view = gltf["bufferViews"][acc["bufferView"]]
    assert 0 <= view.get("byteOffset", 0) + view["byteLength"] <= bin_length
print(f"Validated REAL VRoid anime avatar: {len(joints)} poseable bones, "
      f"{len(skinned_primitives)} skinned primitives, "
      f"{len(gltf['images'])} texture images, {len(gltf['materials'])} materials")
print("Full skeleton bone names:", ", ".join(sorted(names)))
