#!/usr/bin/env python3
"""Map actual VRM 0.x blendshape bindings to stable artist-facing expressions.
No model topology edits or invented shapes: names come from source VRM groups.
"""
import json
import struct
import sys
from pathlib import Path

source, target = map(Path, sys.argv[1:3])
data = source.read_bytes()
assert data[:4] == b"glTF"
length, kind = struct.unpack_from("<II", data, 12)
assert kind == 0x4E4F534A
gltf = json.loads(data[20:20 + length])
vrm = gltf["extensions"]["VRM"]
groups = vrm["blendShapeMaster"]["blendShapeGroups"]
labels = {}
for group in groups:
    title = group.get("name", "").strip()
    if not title or title.lower() == "neutral":
        continue
    for bind in group.get("binds", []):
        if bind.get("mesh") != 0:
            continue
        index = bind["index"]
        if index < 0 or index >= len(gltf["meshes"][0]["primitives"][0].get("targets", [])):
            raise ValueError(f"Bad VRM morph target index: {index}")
        labels.setdefault(str(index), title.replace("_", " "))
required = {"Joy", "Blink", "Angry", "Sorrow"}
assert required <= set(labels.values()), f"Missing essential face expression: {required - set(labels.values())}"
assert len(labels) >= 12
target.write_text(json.dumps({"schema": 1, "mesh": "Face", "labels": labels},
    ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"Licensed VRM expression bindings: {len(labels)} on real skinned face targets")
