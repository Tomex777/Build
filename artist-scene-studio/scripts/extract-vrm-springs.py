#!/usr/bin/env python3
"""Extract genuine VRM 0.x spring groups and colliders from the licensed GLB.

The scene renderer does not supply a VRM secondary-animation runtime; this
manifest lets Mise animate the EXACT rigged strands/fabric joints rather than
swinging an overlay or moving the whole character.
"""
import json
import struct
import sys
from pathlib import Path

source, destination = map(Path, sys.argv[1:3])
raw = source.read_bytes()
assert raw[:4] == b"glTF" and struct.unpack_from("<I", raw, 4)[0] == 2
size, kind = struct.unpack_from("<II", raw, 12)
assert kind == 0x4E4F534A
gltf = json.loads(raw[20:20 + size])
nodes = gltf["nodes"]
secondary = gltf["extensions"]["VRM"]["secondaryAnimation"]
used = set()
chains = []
for entry in secondary.get("boneGroups", []):
    comment = entry.get("comment", "").lower()
    roots = entry.get("bones", [])
    if not roots:
        continue
    if all(nodes[i].get("name", "").startswith("HairJoint-") for i in roots):
        category = "hair"
    elif any(x in comment for x in ("hood", "skirt", "sleeve", "bust", "cloth", "ribbon")):
        category = "fabric"
    else:
        continue
    for root in roots:
        name = nodes[root].get("name")
        if not name or root in used:
            continue
        used.add(root)
        children = nodes[root].get("children", [])
        child = children[0] if children else None
        tip = nodes[child].get("translation", [0, -.06, 0]) if child is not None else [0, -.06, 0]
        if sum(t * t for t in tip) < 0.000001:
            tip = [0, -.06, 0]
        chains.append({
            "name": name,
            "category": category,
            "tip": tip,
            "stiffness": float(entry.get("stiffiness", .5)),
            "drag": float(entry.get("dragForce", .3)),
            "radius": float(entry.get("hitRadius", .01)),
            "colliderGroups": entry.get("colliderGroups", []),
        })
colliders = [
    {"node": nodes[c["node"]].get("name", ""), "spheres": [
        {"offset": item.get("offset", {"x": 0, "y": 0, "z": 0}), "radius": item["radius"]}
        for item in c.get("colliders", [])
    ]}
    for c in secondary.get("colliderGroups", [])
]
assert sum(ch["category"] == "hair" for ch in chains) >= 6
assert len(chains) < 64 and len(colliders) < 32
destination.write_text(json.dumps({
    "schema": 1,
    "source": source.name,
    "chains": chains,
    "colliders": colliders,
}, separators=(",", ":")), encoding="utf-8")
print(f"Real VRM secondary animation: {len(chains)} spring roots, "
      f"{sum(ch['category'] == 'hair' for ch in chains)} hair groups, "
      f"{len(colliders)} collider groups")
