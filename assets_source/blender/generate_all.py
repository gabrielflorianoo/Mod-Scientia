"""Scientia asset pipeline: procedural textures + Blender-built machine models + previews.

Run with Blender's Python (either works):
    blender -b -P assets_source/blender/generate_all.py -- [--no-render]
    python  assets_source/blender/generate_all.py [--no-render]      # with the `bpy` module installed (pip install bpy)

Outputs (all reproducible from a clean checkout):
    src/main/resources/assets/scientia/models/block/*.obj|.mtl|.json   Forge OBJ models
    src/main/resources/assets/scientia/textures/...                    atlas, items, microscope images
    assets_generated/previews/*.png                                     Cycles preview renders
    assets_generated/manifests/models.json                              stats, tiles, animated part pivots
"""
import argparse
import json
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)

import bpy  # noqa: E402

from common import export, render, textures, uv  # noqa: E402
import machines  # noqa: E402

# Models previewed together with their animated parts at several poses (animation check).
ANIMATION_POSES = {
    "thermal_generator": [("thermal_generator_rotor", "rot_x", a) for a in (0, 25)],
    "electric_motor": [("electric_motor_rotor", "rot_y", a) for a in (0, 45)],
    "precision_balance": [("precision_balance_pan", "move_y", d) for d in (0, -1.5)],
    "lab_heater": [("lab_heater_coil", "none", 0)],
    "reaction_vessel": [("reaction_vessel_stopper", "none", 0)],
}
PARTS = {p for poses in ANIMATION_POSES.values() for (p, _, _) in poses}


def parse_args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", default=os.path.join(ROOT, "src/main/resources/assets/scientia"))
    ap.add_argument("--generated", default=os.path.join(ROOT, "assets_generated"))
    ap.add_argument("--no-render", action="store_true", help="skip Cycles previews")
    ap.add_argument("--only", default="", help="comma-separated model names to build")
    return ap.parse_args(argv)


def main():
    args = parse_args()
    t0 = time.time()
    models_dir = os.path.join(args.assets, "models", "block")
    previews = os.path.join(args.generated, "previews")
    manifests = os.path.join(args.generated, "manifests")
    os.makedirs(previews, exist_ok=True)
    os.makedirs(manifests, exist_ok=True)

    written, _, _ = textures.write_all(args.assets)
    atlas_path = os.path.join(args.assets, "textures", "block", "lab_atlas.png")
    emission_path = os.path.join(args.generated, "atlas_emission.png")
    _, emission = textures.build_atlas()
    from common import raster
    raster.write_png(emission_path, emission)
    print(f"[textures] {len(written)} files")

    scene = render.reset_scene()
    col = bpy.data.collections.new("models")
    scene.collection.children.link(col)
    specs = machines.build_all()
    only = {s for s in args.only.split(",") if s}
    objects = {}
    manifest = {"models": {}, "parts": machines.PIVOTS, "atlas": {"size": textures.TILE * textures.ATLAS_TILES,
                                                                  "tiles": list(textures.TILE_INDEX)}}
    for name, builder in specs.items():
        if only and name not in only:
            continue
        obj = builder.to_object(col)
        uv.map_object(obj)
        stats = export.export_model(name, [obj], models_dir)
        manifest["models"][name] = stats
        objects[name] = obj
        bounds_ok = all(-1.0 <= v <= 2.0 for v in stats["min"] + stats["max"])
        print(f"[model] {name:28s} {stats['vertices']:5d} v {stats['faces']:5d} f  translucent={stats['translucent']}"
              + ("" if bounds_ok else "  !! OUT OF BOUNDS"))
        if not bounds_ok:
            raise SystemExit(f"model {name} exceeds Minecraft's -16..32 px model bounds")
    with open(os.path.join(manifests, "models.json"), "w") as fh:
        json.dump(manifest, fh, indent=1)

    if args.no_render:
        print(f"done in {time.time() - t0:.1f}s (previews skipped)")
        return

    mat = render.atlas_material(atlas_path, emission_path)
    for o in objects.values():
        o.data.materials.clear()
        o.data.materials.append(mat)
        o.hide_render = True
    cam = render.setup_stage(scene)
    rendered = []
    for name, obj in objects.items():
        if name in PARTS:
            continue
        obj.hide_render = False
        poses = ANIMATION_POSES.get(name, [(None, "none", 0)])
        for i, (part, kind, value) in enumerate(poses):
            p = objects.get(part) if part else None
            if p is not None:
                p.hide_render = False
                p.rotation_euler = (0, 0, 0)
                p.location = (0, 0, 0)
                pivot = machines.PIVOTS.get(part, {}).get("pivot_px")
                if kind in ("rot_x", "rot_y") and pivot:
                    # rotate about the pivot: Minecraft X -> Blender X, Minecraft Y -> Blender Z
                    from mathutils import Matrix, Vector
                    pv = Vector((pivot[0] / 16 - 0.5, -(pivot[2] / 16 - 0.5), pivot[1] / 16))
                    axis = "X" if kind == "rot_x" else "Z"
                    m = Matrix.Translation(pv) @ Matrix.Rotation(math.radians(value), 4, axis) @ Matrix.Translation(-pv)
                    p.matrix_world = m
                elif kind == "move_y":
                    p.location = (0, 0, value / 16)
            render.aim_camera(cam, distance=3.0 if name not in ("insulated_kiln", "insulated_kiln_active", "thermal_generator") else 3.4)
            suffix = "" if len(poses) == 1 else f"_pose{i}"
            path = os.path.join(previews, name + suffix + ".png")
            render.render(path)
            rendered.append(path)
            if p is not None:
                p.hide_render = True
        obj.hide_render = True
        print(f"[preview] {name}")
    render.contact_sheet(rendered, os.path.join(previews, "_contact_sheet.png"))
    print(f"done in {time.time() - t0:.1f}s, {len(rendered)} previews")


if __name__ == "__main__":
    main()
