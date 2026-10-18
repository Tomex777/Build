#!/usr/bin/env python3
"""Losslessly join a CC0 oak's 622 individual bark pieces to one GLB mesh.

This is NOT a Blender conversion or a mesh approximation. It preserves every
original leaf triangle, bark twig, PBR material and hierarchy-world transform.
But it collapses 625 Android render draw objects to four material batches.

Original artist and CC0 source:
https://www.innerscene.com/tools/library/3d-parts/oak-street-tree-detailed-leaves-a273fef9
"""
import argparse
from collections import defaultdict
from pathlib import Path
import trimesh


def optimize(source: Path, target: Path) -> None:
    scene = trimesh.load(source, force="scene")
    grouped = defaultdict(list)
    before = 0
    for node in scene.graph.nodes_geometry:
        transform, geom = scene.graph[node]
        mesh = scene.geometry[geom].copy()
        mesh.apply_transform(transform)
        material = mesh.visual.material
        # The source groups leaves in three original shades. Don't combine
        # colors: textured or flat PBR material fidelity should be unchanged.
        group = "bark" if material.name == "bark" else "foliage-" + str(material.baseColorFactor.tolist())
        grouped[group].append(mesh)
        before += len(mesh.faces)

    assert len(scene.graph.nodes_geometry) > 600
    assert len(grouped) == 4, list(grouped)
    finished = trimesh.Scene()
    after = 0
    for i, (group, meshes) in enumerate(sorted(grouped.items())):
        batch = trimesh.util.concatenate(meshes)
        finished.add_geometry(batch, geom_name="Oak_" + str(i))
        after += len(batch.faces)
    assert after == before, (before, after)
    assert len(finished.geometry) == 4
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(finished.export(file_type="glb"))
    print(f"Optimized CC0 oak: {len(scene.graph.nodes_geometry)} native nodes ->"
          f" {len(finished.geometry)} draw meshes; {after} unchanged triangles;"
          f" {source.stat().st_size} -> {target.stat().st_size} bytes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    optimize(args.source, args.output)
