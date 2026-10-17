#!/usr/bin/env python3
"""Shrink VRoid GPU skin palettes without removing any 3D geometry or rig nodes.

Some Android OpenGL/Filament targets render an avatar with 119 bones PER skin
invisibly. Only joint indices actually referenced by each skinned mesh need GPU
palette entries. Preserve the complete transform hierarchy and the authored
model's meshes/texture pixels; compact JOINTS_0 plus inverse bind matrices
together, so rigging remains physically correct on API 26 and API 36.
"""
import json
import struct
import sys
from pathlib import Path

source, target = map(Path, sys.argv[1:3])
raw = source.read_bytes()
magic, version, total = struct.unpack_from("<III", raw)
assert (magic, version, total) == (0x46546C67, 2, len(raw))
json_size, json_type = struct.unpack_from("<II", raw, 12)
assert json_type == 0x4E4F534A
scene = json.loads(raw[20:20 + json_size])
bin_header = 20 + json_size
bin_size, bin_type = struct.unpack_from("<II", raw, bin_header)
assert bin_type == 0x004E4942 and bin_header + 8 + bin_size == len(raw)
binary = bytearray(raw[bin_header + 8:])
original_mesh_count = len(scene["meshes"])
original_images = len(scene.get("images", []))

for skin_id, skin in enumerate(scene["skins"]):
    accessors = set()
    for node in scene["nodes"]:
        if node.get("skin") != skin_id or "mesh" not in node:
            continue
        for primitive in scene["meshes"][node["mesh"]]["primitives"]:
            if "JOINTS_0" in primitive["attributes"]:
                assert "JOINTS_1" not in primitive["attributes"]
                accessors.add(primitive["attributes"]["JOINTS_0"])
    if not accessors:
        continue

    def view_info(accessor_index):
        accessor = scene["accessors"][accessor_index]
        view = scene["bufferViews"][accessor["bufferView"]]
        width = {5121: 1, 5123: 2}[accessor["componentType"]]
        stride = view.get("byteStride", width * 4)
        offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
        fmt = "<4B" if width == 1 else "<4H"
        return accessor["count"], offset, stride, fmt

    used = set()
    for accessor_id in accessors:
        count, offset, stride, fmt = view_info(accessor_id)
        for i in range(count):
            used.update(struct.unpack_from(fmt, binary, offset + i * stride))
    original_joint_nodes = skin["joints"][:]
    assert used and max(used) < len(original_joint_nodes)
    indices = sorted(used)
    remap = {old: new for new, old in enumerate(indices)}
    # Every vertex index including zero-weight slots must be mapped; leaving a
    # stale number in a zero-weight slot can cause undefined GPU reads.
    for accessor_id in accessors:
        count, offset, stride, fmt = view_info(accessor_id)
        for i in range(count):
            pos = offset + i * stride
            old = struct.unpack_from(fmt, binary, pos)
            struct.pack_into(fmt, binary, pos, *(remap[x] for x in old))
    skin["joints"] = [original_joint_nodes[i] for i in indices]

    original_accessor = scene["accessors"][skin["inverseBindMatrices"]]
    view = scene["bufferViews"][original_accessor["bufferView"]]
    assert original_accessor["count"] == len(original_joint_nodes)
    assert original_accessor["type"] == "MAT4" and original_accessor["componentType"] == 5126
    src_offset = view.get("byteOffset", 0) + original_accessor.get("byteOffset", 0)
    src_stride = view.get("byteStride", 64)
    matrices = b"".join(binary[src_offset + i * src_stride:src_offset + i * src_stride + 64] for i in indices)
    while len(binary) % 4:
        binary.append(0)
    offset = len(binary)
    binary.extend(matrices)
    new_view = len(scene["bufferViews"])
    scene["bufferViews"].append({"buffer": 0, "byteOffset": offset, "byteLength": len(matrices)})
    new_accessor = len(scene["accessors"])
    scene["accessors"].append({
        "bufferView": new_view, "byteOffset": 0, "componentType": 5126,
        "count": len(indices), "type": "MAT4",
    })
    skin["inverseBindMatrices"] = new_accessor
    print(f"GPU palette {skin_id}: {len(original_joint_nodes)} -> {len(indices)} joints")

assert max(len(s["joints"]) for s in scene["skins"]) <= 96, "Anime skin too complex for old Android GPU"
assert len(scene["meshes"]) == original_mesh_count and len(scene.get("images", [])) == original_images
while len(binary) % 4:
    binary.append(0)
scene["buffers"][0]["byteLength"] = len(binary)
scene_json = json.dumps(scene, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
scene_json += b" " * ((-len(scene_json)) % 4)
result = (
    struct.pack("<III", magic, version, 12 + 8 + len(scene_json) + 8 + len(binary))
    + struct.pack("<II", len(scene_json), json_type)
    + scene_json
    + struct.pack("<II", len(binary), bin_type)
    + binary
)
target.write_bytes(result)
print(f"Compact anime GLB: {len(result)} bytes, {original_mesh_count} meshes, "
      f"{original_images} images, skinned nodes still editable")
