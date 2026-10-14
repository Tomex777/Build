#!/usr/bin/env python3
"""Validate the real skinned 3D anime GLB produced for every Android build.

Rejects placeholder meshes, flat images, missing skin weights, malformed binary
ranges, or lost pose-controller morph targets; uses Python standard library.
"""
import argparse
import json
import struct
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("path", type=Path)
args = p.parse_args()
data = args.path.read_bytes()
assert len(data) > 1_000_000, "Anime asset is suspiciously small"
magic, version, declared_length = struct.unpack_from("<III", data)
assert (magic, version, declared_length) == (0x46546C67, 2, len(data))
json_length, json_kind = struct.unpack_from("<II", data, 12)
assert json_kind == 0x4E4F534A
gltf = json.loads(data[20:20 + json_length])
bin_header = 20 + json_length
bin_length, bin_kind = struct.unpack_from("<II", data, bin_header)
assert bin_kind == 0x004E4942
assert bin_header + 8 + bin_length == len(data)
assert gltf["buffers"][0]["byteLength"] <= bin_length
assert gltf["extras"]["style"] == "mise-anime-figure-v1"
assert gltf["extras"]["license"] == "CC0-1.0"
names = [node.get("name") for node in gltf["nodes"]]
assert "AnimeFace" in names
assert all("MiseHair-" + name in names for name in ["short", "bob", "afro"])
assert len(gltf["skins"]) == 1
skin = gltf["skins"][0]
assert len(skin["joints"]) >= 150, "Anime character lost its full human skeleton"
assert any(gltf["nodes"][node].get("name") == "head" for node in skin["joints"])
assert all("mesh" in node for node in gltf["nodes"] if node.get("name") in ("AnimeFace", "Body"))
body = next(gltf["meshes"][node["mesh"]] for node in gltf["nodes"] if node.get("name") == "Body")
assert len(body["primitives"]) >= 3, "Anime figure outfit materials were lost"
assert set(body["extras"]["targetNames"]) == {"Body fat", "Muscularity", "Pointed ears", "Ear size"}
assert len(set(pr["material"] for pr in body["primitives"])) >= 3
for mesh in gltf["meshes"]:
    for primitive in mesh["primitives"]:
        assert {"POSITION", "NORMAL", "JOINTS_0", "WEIGHTS_0"} <= primitive["attributes"].keys(), mesh["name"]
        assert "indices" in primitive
        if mesh.get("name") == "Humanoid":
            assert len(primitive.get("targets", [])) == 4
for index, accessor in enumerate(gltf["accessors"]):
    view = gltf["bufferViews"][accessor["bufferView"]]
    start = view["byteOffset"]
    end = start + view["byteLength"]
    assert 0 <= start < end <= bin_length, ("accessor outside GLB buffer", index)
print(f"Validated real anime GLB: {len(skin['joints'])} bones, {len(body['primitives'])} outfit materials, "
      f"{len(gltf['meshes'])} real meshes, editable 3D face and 4 body shapes")
