#!/usr/bin/env python3
"""Validate the pinned CC0 Quaternius stylized humanoid before Android packaging."""
import argparse
import json
import struct
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("path", type=Path)
args = parser.parse_args()
raw = args.path.read_bytes()
assert 200_000 < len(raw) < 5_000_000, "Unexpected stylized human asset size"
magic, version, length = struct.unpack_from("<III", raw)
assert (magic, version, length) == (0x46546C67, 2, len(raw)), "Invalid GLB header"
json_length, kind = struct.unpack_from("<II", raw, 12)
assert kind == 0x4E4F534A, "Missing glTF JSON chunk"
gltf = json.loads(raw[20:20+json_length])
bin_offset = 20 + json_length
bin_length, bin_kind = struct.unpack_from("<II", raw, bin_offset)
assert bin_kind == 0x004E4942 and bin_offset + 8 + bin_length == len(raw), "Bad GLB binary chunk"
assert gltf["buffers"][0]["byteLength"] <= bin_length
skins = gltf["skins"]
assert len(skins) == 1, "Stylized character should use one retargetable skeleton"
names = {gltf["nodes"][i].get("name", "").lower() for i in skins[0]["joints"]}
assert len(skins[0]["joints"]) >= 60, "Full-body rig missing"
required = {
    "head", "upperarm_l", "upperarm_r", "lowerarm_l", "lowerarm_r",
    "thigh_l", "thigh_r", "calf_l", "calf_r",
}
assert required <= names, f"Missing pose controls: {required - names}"
assert len(gltf["meshes"]) >= 1 and len(gltf["materials"]) >= 1
skin_meshes = 0
for node in gltf["nodes"]:
    if "skin" not in node or "mesh" not in node:
        continue
    mesh = gltf["meshes"][node["mesh"]]
    for primitive in mesh["primitives"]:
        attr = primitive["attributes"]
        assert {"POSITION", "NORMAL", "JOINTS_0", "WEIGHTS_0"} <= attr.keys()
        skin_meshes += 1
assert skin_meshes >= 1, "No renderable skinned mesh"
for acc in gltf["accessors"]:
    view = gltf["bufferViews"][acc["bufferView"]]
    assert 0 <= view.get("byteOffset", 0) + view.get("byteLength", 0) <= bin_length
print(f"Validated Quaternius CC0 stylized hero: {len(skins[0]['joints'])} real pose bones, {skin_meshes} skin primitives")
