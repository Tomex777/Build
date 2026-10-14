"""Independent conversion of CC0 MakeHuman hair geometry and vertex mappings."""
import hashlib
import json
from pathlib import Path


def fitted_vertices(path, body, count):
    scales = [1., 1., 1.]
    bindings = []
    in_vertices = False
    for line in path.read_text().splitlines():
        fields = line.split()
        if not fields or fields[0].startswith("#"):
            continue
        if fields[0] in {"x_scale", "y_scale", "z_scale"}:
            axis = "xyz".index(fields[0][0])
            a, b = map(int, fields[1:3])
            scales[axis] = abs(body[a][axis] - body[b][axis]) / (float(fields[3]) * .1)
        elif fields[0] == "verts":
            in_vertices = True
        elif in_vertices and fields[0].lstrip("-").isdigit():
            if len(fields) == 1:
                bindings.append(list(body[int(fields[0])]))
            elif len(fields) == 9:
                ids = list(map(int, fields[:3]))
                weights = list(map(float, fields[3:6]))
                offsets = list(map(float, fields[6:9]))
                bindings.append([sum(body[j][axis] * w for j, w in zip(ids, weights)) + offsets[axis] * .1 * scales[axis] for axis in range(3)])
            else:
                raise ValueError("Unsupported hair vertex binding")
            if len(bindings) == count:
                break
    if len(bindings) != count:
        raise ValueError(f"Hair binding count mismatch: {path}")
    return bindings


def append_hair(gltf, binary, views, accessor, body, body_shapes, names, normals, source_dir):
    manifest = json.loads((source_dir / "prepared.json").read_text())
    for name, expected in manifest["files"].items():
        if hashlib.sha256((source_dir / name).read_bytes()).hexdigest() != expected:
            raise ValueError(f"Hair source hash mismatch: {name}")
    gltf["images"], gltf["textures"] = [], []
    gltf["samplers"] = [{"magFilter": 9729, "minFilter": 9987, "wrapS": 33071, "wrapT": 33071}]
    head = names.index("head")
    for style, folder in [("short", "short01"), ("bob", "bob01"), ("afro", "afro01")]:
        directory = source_dir / folder
        original, uv, faces = [], [], []
        for line in (directory / (folder + ".obj")).read_text().splitlines():
            fields = line.split()
            if not fields:
                continue
            if fields[0] == "v":
                original.append(fields[1:4])
            elif fields[0] == "vt":
                uv.append([float(fields[1]), 1. - float(fields[2])])
            elif fields[0] == "f":
                face = [tuple(int(v) - 1 for v in token.split("/")[:2]) for token in fields[1:]]
                faces.extend((face[0], face[i], face[i + 1]) for i in range(1, len(face) - 1))
        binding = directory / (folder + ".mhclo")
        positions = fitted_vertices(binding, body, len(original))
        body_triangles = [tuple(vertex[0] for vertex in face) for face in faces]
        base_normals = normals(positions, body_triangles)
        # Split UV seams while preserving smooth normals from the source vertex topology.
        unique = list(dict.fromkeys(vertex for face in faces for vertex in face))
        indices = {value: i for i, value in enumerate(unique)}
        attrs = {"POSITION": accessor([positions[v] for v, _ in unique], 5126, "VEC3", True),
                 "NORMAL": accessor([base_normals[v] for v, _ in unique], 5126, "VEC3"),
                 "TEXCOORD_0": accessor([uv[t] for _, t in unique], 5126, "VEC2"),
                 "JOINTS_0": accessor([[head, 0, 0, 0] for _ in unique], 5123, "VEC4"),
                 "WEIGHTS_0": accessor([[1., 0., 0., 0.] for _ in unique], 5126, "VEC4")}
        targets = []
        for label, changed_body in body_shapes:
            changed = fitted_vertices(binding, changed_body, len(original))
            changed_normals = normals(changed, body_triangles)
            targets.append({"POSITION": accessor([[changed[v][a] - positions[v][a] for a in range(3)] for v, _ in unique], 5126, "VEC3", True),
                            "NORMAL": accessor([[changed_normals[v][a] - base_normals[v][a] for a in range(3)] for v, _ in unique], 5126, "VEC3")})
        material = len(gltf["materials"])
        texture = len(gltf["textures"])
        png = (directory / "tint.png").read_bytes()
        binary.extend(b"\0" * ((-len(binary)) % 4))
        views.append({"buffer": 0, "byteOffset": len(binary), "byteLength": len(png)})
        binary.extend(png)
        gltf["images"].append({"bufferView": len(views) - 1, "mimeType": "image/png"})
        gltf["textures"].append({"sampler": 0, "source": texture})
        gltf["materials"].append({"name": "Mise hair " + style, "doubleSided": True, "alphaMode": "MASK", "alphaCutoff": .4,
                                  "pbrMetallicRoughness": {"baseColorFactor": [.03, .02, .015, 1.], "baseColorTexture": {"index": texture}, "metallicFactor": 0., "roughnessFactor": .75}})
        mesh = len(gltf["meshes"])
        gltf["meshes"].append({"name": "MiseHair-" + style, "weights": [0.] * len(targets), "extras": {"targetNames": [label for label, _ in body_shapes]},
                               "primitives": [{"attributes": attrs, "indices": accessor([indices[v] for face in faces for v in face], 5125, "SCALAR"), "material": material, "targets": targets}]})
        gltf["nodes"][0]["children"].append(len(gltf["nodes"]))
        gltf["nodes"].append({"name": "MiseHair-" + style, "mesh": mesh, "skin": 0})
        print(f"Hair {style}: {len(unique)} vertices, {len(faces)} triangles, head-bound with {len(targets)} body shapes")
    gltf["buffers"][0]["byteLength"] = len(binary)
