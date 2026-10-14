#!/usr/bin/env python3
"""Build a skinned, morphable GLB from pinned CC0 MakeHuman graphical assets.

Independent converter; no MakeHuman application code is used. Python standard library only.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def sources(directory):
    manifest = json.loads((ROOT / "scripts/humanoid-assets.json").read_text())
    directory.mkdir(parents=True, exist_ok=True)
    for name, entry in manifest["files"].items():
        path = directory / name
        if not path.exists():
            url = f'https://raw.githubusercontent.com/makehumancommunity/makehuman/{manifest["revision"]}/{entry["path"]}'
            with urllib.request.urlopen(url, timeout=60) as response:
                path.write_bytes(response.read())
        data = path.read_bytes()
        def digest(value):
            return hashlib.sha1(b"blob " + str(len(value)).encode() + b"\0" + value).hexdigest()
        # Text recovery through patch tools can add one final newline; verify original bytes.
        if digest(data) != entry["sha"] and data.endswith(b"\n") and digest(data[:-1]) == entry["sha"]:
            data = data[:-1]
            path.write_bytes(data)
        if digest(data) != entry["sha"]:
            raise ValueError(f"Source hash mismatch: {name}")
    return manifest


def read_obj(path):
    vertices, faces, group = [], [], ""
    for line in path.read_text().splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "v":
            vertices.append(tuple(float(v) * .1 for v in parts[1:4]))
        elif parts[0] == "g":
            group = parts[1]
        elif parts[0] == "f" and group == "body":
            indices = [int(p.split("/")[0]) - 1 for p in parts[1:]]
            faces.extend((indices[0], indices[i], indices[i + 1]) for i in range(1, len(indices) - 1))
    if not faces:
        raise ValueError("Body faces missing")
    return vertices, faces


def target(path, count):
    offsets = [[0., 0., 0.] for _ in range(count)]
    for line in path.read_text().splitlines():
        values = line.split()
        if not values or values[0].startswith("#"):
            continue
        i = int(values[0])
        offsets[i] = [float(v) * .1 for v in values[1:4]]
    return offsets


def normals(vertices, triangles):
    values = [[0., 0., 0.] for _ in vertices]
    for a, b, c in triangles:
        u = [vertices[b][i] - vertices[a][i] for i in range(3)]
        v = [vertices[c][i] - vertices[a][i] for i in range(3)]
        n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
        for index in (a, b, c):
            for i in range(3):
                values[index][i] += n[i]
    return [[v / (math.sqrt(sum(x*x for x in n)) or 1.) for v in n] for n in values]


def anime_vertex(point):
    """Original anime-inspired mannequin proportions, derived only from CC0 body geometry.

    The exact same function is applied to skin, the bone head locations, hair-bound
    source body, and morph target endpoints. This avoids torn skin or detached hair.
    """
    x, y, z = point
    def ease(a, b):
        t = max(0., min(1., (y - a) / (b - a)))
        return t * t * (3. - 2. * t)
    head = ease(1.34, 1.59)
    waist = ease(.75, 1.08) * (1. - ease(1.13, 1.42))
    # Slightly longer legs, a narrow torso, a larger round anime head.
    x *= 1. - .12 * waist + .22 * head
    z *= 1. - .10 * waist + .18 * head
    y += .025 * ease(.1, .91) + .065 * ease(1.37, 1.80)
    return [x, y, z]


def append_anime_face(gltf, accessor, positions, centers, names):
    """Facial features use the same real head bone as the skin and hair.

    The shapes are 3D triangles, not screen-space stickers; they turn, pose and
    persist with the character. Iris/catchlights create a recognizably stylized face.
    """
    if "eye.L" not in centers or "eye.R" not in centers or "head" not in names:
        raise ValueError("CC0 MakeHuman default skeleton lacks anime face anchors")
    head_joint = names.index("head")
    eye_vertices, indices, palette = [], [], []
    # The eye anchors are bone heads. Find the outer skin surface near each anchor,
    # so facial marks cannot disappear inside the source MakeHuman mesh.
    for name in ("eye.L", "eye.R"):
        anchor = centers[name]
        relevant = [p[2] for p in positions
                    if abs(p[0] - anchor[0]) < .035 and abs(p[1] - anchor[1]) < .045]
        front_z = max(relevant) + .009 if relevant else anchor[2] + .04
        def ellipse(cx, cy, rx, ry, z, material, segments=12):
            offset = len(eye_vertices)
            eye_vertices.append((cx, cy, z))
            for n in range(segments):
                a = 2 * math.pi * n / segments
                eye_vertices.append((cx + rx * math.cos(a), cy + ry * math.sin(a), z))
            for n in range(segments):
                palette.append(material)
                indices.append((offset, offset + n + 1, offset + (n + 1) % segments + 1))
        ellipse(anchor[0], anchor[1], .028, .031, front_z, 0)
        ellipse(anchor[0], anchor[1] - .004, .019, .024, front_z + .002, 1)
        ellipse(anchor[0] - .007, anchor[1] + .006, .005, .007, front_z + .004, 2)
        ellipse(anchor[0], anchor[1] + .049, .028, .005, front_z + .001, 1)
    attrs = {
        "POSITION": accessor(eye_vertices, 5126, "VEC3", True),
        "NORMAL": accessor([[0., 0., 1.] for _ in eye_vertices], 5126, "VEC3"),
        "JOINTS_0": accessor([[head_joint, 0, 0, 0] for _ in eye_vertices], 5123, "VEC4"),
        "WEIGHTS_0": accessor([[1., 0., 0., 0.] for _ in eye_vertices], 5126, "VEC4"),
    }
    # Share a single skinned vertex stream but use per-material triangle index sets.
    mat_start = len(gltf["materials"])
    for label, color in (
        ("Eye white", [1., .985, .94, 1.]),
        ("Ink iris", [.043, .070, .145, 1.]),
        ("Eye glint", [1., 1., 1., 1.]),
    ):
        gltf["materials"].append({
            "name": "Mise Anime " + label, "doubleSided": True,
            "pbrMetallicRoughness": {
                "baseColorFactor": color, "metallicFactor": 0., "roughnessFactor": .94,
            },
        })
    primitives = []
    for material in range(3):
        triangles = [tri for tri, category in zip(indices, palette) if category == material]
        primitives.append({
            "attributes": attrs,
            "indices": accessor([i for tri in triangles for i in tri], 5125, "SCALAR"),
            "material": mat_start + material,
        })
    mesh_index = len(gltf["meshes"])
    gltf["meshes"].append({"name": "AnimeFace", "primitives": primitives})
    node_index = len(gltf["nodes"])
    gltf["nodes"][0]["children"].append(node_index)
    gltf["nodes"].append({"name": "AnimeFace", "mesh": mesh_index, "skin": 0})


def build(directory, output, anime=False):
    manifest = sources(directory)
    source, faces = read_obj(directory / "base.obj")
    average = target(directory / "average.target", len(source))
    vertices = [[v[i] + average[j][i] for i in range(3)] for j, v in enumerate(source)]
    used = sorted({i for face in faces for i in face})
    floor = min(vertices[i][1] for i in used)
    for v in vertices:
        v[1] -= floor
    undistorted = [p.copy() for p in vertices]
    if anime:
        vertices = [anime_vertex(p) for p in vertices]
    mapping = {old: new for new, old in enumerate(used)}
    triangles = [tuple(mapping[i] for i in face) for face in faces]
    positions = [vertices[i] for i in used]
    base_normals = normals(positions, triangles)
    rig = json.loads((directory / "rig.json").read_text())
    weights = json.loads((directory / "weights.json").read_text())["weights"]
    names = list(rig["bones"])
    bone_indices = {name: i for i, name in enumerate(names)}
    centers = {}
    for name, bone in rig["bones"].items():
        joint = rig["joints"][bone["head"]]
        centers[name] = [sum(vertices[j][i] for j in joint)/len(joint) for i in range(3)]
    nodes = [{"name": "Humanoid", "children": [1]}, {"name": "Body", "mesh": 0, "skin": 0}]
    for name in names:
        parent = rig["bones"][name]["parent"]
        origin = centers[parent] if parent else [0., 0., 0.]
        nodes.append({"name": name, "translation": [centers[name][i]-origin[i] for i in range(3)]})
    for name in names:
        parent = rig["bones"][name]["parent"]
        parent_node = bone_indices[parent] + 2 if parent else 0
        nodes[parent_node].setdefault("children", []).append(bone_indices[name] + 2)
    influences = [[] for _ in source]
    for name, entries in weights.items():
        for index, weight in entries:
            if weight > 0:
                influences[index].append((bone_indices[name], weight))
    joints, skin_weights = [], []
    for old in used:
        items = sorted(influences[old], key=lambda v: -v[1])[:4]
        if not items:
            raise ValueError(f"Unweighted body vertex {old}")
        total = sum(w for _, w in items)
        joints.append([j for j, _ in items] + [0]*(4-len(items)))
        skin_weights.append([w/total for _, w in items] + [0.]*(4-len(items)))

    binary, views, accessors = bytearray(), [], []
    def accessor(rows, component, kind, bounds=False):
        while len(binary) % 4:
            binary.append(0)
        start = len(binary)
        codes = {5126: "f", 5123: "H", 5125: "I"}
        flat = [value for row in rows for value in (row if isinstance(row, (tuple, list)) else [row])]
        binary.extend(struct.pack("<" + codes[component]*len(flat), *flat))
        views.append({"buffer": 0, "byteOffset": start, "byteLength": len(binary)-start})
        entry = {"bufferView": len(views)-1, "componentType": component, "count": len(rows), "type": kind}
        if bounds:
            entry["min"] = [min(row[i] for row in rows) for i in range(len(rows[0]))]
            entry["max"] = [max(row[i] for row in rows) for i in range(len(rows[0]))]
        accessors.append(entry)
        return len(accessors)-1

    attributes = {"POSITION": accessor(positions, 5126, "VEC3", True), "NORMAL": accessor(base_normals, 5126, "VEC3"),
                  "JOINTS_0": accessor(joints, 5123, "VEC4"), "WEIGHTS_0": accessor(skin_weights, 5126, "VEC4")}
    indices = accessor([i for tri in triangles for i in tri], 5125, "SCALAR")
    inverse = []
    for name in names:
        x, y, z = centers[name]
        inverse.append([1.,0.,0.,0., 0.,1.,0.,0., 0.,0.,1.,0., -x,-y,-z,1.])
    bind = accessor(inverse, 5126, "MAT4")
    shapes = []
    hair_shapes = []
    for label, files, subtract_average in [
        ("Body fat", ["fat.target"], True), ("Muscularity", ["muscle.target"], True),
        ("Pointed ears", ["ear-left.target", "ear-right.target"], False),
        ("Ear size", ["ears-left-size.target", "ears-right-size.target"], False),
    ]:
        targets = [target(directory / file, len(source)) for file in files]
        def altered(j):
            before = [undistorted[j][i] +
                sum(t[j][i] for t in targets) -
                (average[j][i] if subtract_average else 0.) for i in range(3)]
            return anime_vertex(before) if anime else before
        hair_shapes.append((label, [altered(j) for j in range(len(vertices))]))
        changed = [altered(j) for j in used]
        delta = [[p[i] - positions[n][i] for i in range(3)] for n, p in enumerate(changed)]
        changed_normals = normals(changed, triangles)
        normal_delta = [[n[i]-b[i] for i in range(3)] for n, b in zip(changed_normals, base_normals)]
        shapes.append((label, {"POSITION": accessor(delta, 5126, "VEC3", True), "NORMAL": accessor(normal_delta, 5126, "VEC3")}))
    # Real GLB materials turn the skinned mesh into an anime-style fitted outfit.
    # Triangle-based region assignment preserves the exact skin weights and morph
    # attributes; every region follows all 163 bones and all four body shapes.
    if anime:
        anime_materials = [
            ("Warm porcelain skin", [.91, .71, .60, 1.]),
            ("Sea glass top", [.18, .52, .58, 1.]),
            ("Deep indigo outfit", [.10, .15, .31, 1.]),
            ("Ivory cuffs and shoes", [.93, .91, .84, 1.]),
        ]
        materials = [{
            "name": label,
            "pbrMetallicRoughness": {
                "baseColorFactor": color, "metallicFactor": 0., "roughnessFactor": .92,
            },
        } for label, color in anime_materials]
        groups = [[] for _ in anime_materials]
        for tri in triangles:
            center = [sum(positions[j][i] for j in tri)/3 for i in range(3)]
            x, y, z = center
            # Built-in costume rather than a nude figure: dress body and legs while
            # leaving the head and hands skin-toned for pose reference readability.
            if y < .13:
                region = 3
            elif y < .88:
                region = 2
            elif y < 1.40 and abs(x) < .36:
                region = 1
            elif y < 1.45 and abs(x) >= .36:
                region = 3
            else:
                region = 0
            groups[region].extend(tri)
        primitives = [{
            "attributes": attributes, "indices": accessor(group, 5125, "SCALAR"),
            "material": material,
            "targets": [shape[1] for shape in shapes],
        } for material, group in enumerate(groups) if group]
    else:
        materials = [{"name": "Neutral clay", "pbrMetallicRoughness": {
            "baseColorFactor": [.62, .65, .69, 1.], "metallicFactor": 0., "roughnessFactor": .85,
        }}]
        primitives = [{"attributes": attributes, "indices": indices,
                       "material": 0, "targets": [shape[1] for shape in shapes]}]

    gltf = {"asset": {"version": "2.0", "generator": "Mise independent CC0 humanoid converter"},
            "scene": 0, "scenes": [{"nodes": [0]}], "nodes": nodes,
            "buffers": [{"byteLength": len(binary)}], "bufferViews": views, "accessors": accessors,
            "skins": [{"inverseBindMatrices": bind, "joints": list(range(2, len(nodes)))}],
            "materials": materials,
            "meshes": [{"name": "Humanoid", "weights": [0.]*len(shapes), "extras": {"targetNames": [shape[0] for shape in shapes]},
                        "primitives": primitives}],
            "extras": {"sourceRevision": manifest["revision"], "license": "CC0-1.0"}}
    from humanoid_hair import append_hair
    append_hair(gltf, binary, views, accessor, vertices, hair_shapes, names, normals, ROOT / "scripts/hair-source")
    if anime:
        append_anime_face(gltf, accessor, positions, centers, names)
        gltf["buffers"][0]["byteLength"] = len(binary)
        gltf["asset"]["generator"] = "Mise original anime study from pinned CC0 MakeHuman body"
        gltf["extras"]["style"] = "mise-anime-figure-v1"
    encoded = json.dumps(gltf, separators=(",", ":")).encode()
    encoded += b" "*((-len(encoded)) % 4)
    binary.extend(b"\0"*((-len(binary)) % 4))
    data = struct.pack("<III", 0x46546c67, 2, 28+len(encoded)+len(binary))
    data += struct.pack("<II", len(encoded), 0x4e4f534a) + encoded
    data += struct.pack("<II", len(binary), 0x004e4942) + binary
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    license_path = ROOT / "app/src/main/assets/licenses/makehuman-cc0.txt"
    license_path.parent.mkdir(parents=True, exist_ok=True)
    license_path.write_bytes((directory / "LICENSE.ASSETS.md").read_bytes())
    print(f"Built {output}: {len(used)} vertices, {len(triangles)} triangles, {len(names)} bones, {len(shapes)} shapes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=Path("/tmp/mise-humanoid-source"))
    parser.add_argument("--output", type=Path, default=ROOT / "app/src/main/assets/models/mise_humanoid.glb")
    parser.add_argument("--anime", action="store_true", help="Create a stylized original anime 3D character GLB")
    args = parser.parse_args()
    build(args.source_dir, args.output, anime=args.anime)

