#!/usr/bin/env python3
"""Validates Scientia's resources against the Java registrations.

Checks: blockstates, block/item models, OBJ + MTL files (face sizes, indices, bounds), texture
existence and dimensions, animated part models used by renderers, lang keys used in code, loot tables,
crafting and workbench recipes, microscope images. Exit code 1 on any error.
"""
import glob
import json
import os
import re
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/scientia")
JAVA = os.path.join(ROOT, "src/main/java/com/scientia")
errors, warnings = [], []


def err(msg):
    errors.append(msg)


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def png_size(path):
    with open(path, "rb") as fh:
        head = fh.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    return struct.unpack(">II", head[16:24])


def res_path(rl, folder, ext):
    ns, _, path = rl.partition(":") if ":" in rl else ("minecraft", "", rl)
    return os.path.join(RES, "assets", ns, folder, path + ext)


def java_sources():
    out = {}
    for p in glob.glob(os.path.join(JAVA, "**/*.java"), recursive=True):
        with open(p, encoding="utf-8") as fh:
            out[p] = fh.read()
    return out


def check_texture(rl, where, expect=None):
    if rl.startswith("#"):
        return
    ns = rl.split(":")[0] if ":" in rl else "minecraft"
    if ns == "minecraft":
        return  # vanilla textures
    p = res_path(rl, "textures", ".png")
    if not os.path.exists(p):
        err(f"{where}: missing texture {rl}")
        return
    w, h = png_size(p)
    if w != h or w & (w - 1):
        err(f"{where}: texture {rl} is {w}x{h} (must be square power of two)")
    if expect and (w, h) != expect:
        err(f"{where}: texture {rl} is {w}x{h}, expected {expect}")


def check_obj(path):
    nv = nt = nn = 0
    faces = 0
    mtllib = None
    with open(path) as fh:
        for line in fh:
            t = line.split()
            if not t:
                continue
            if t[0] == "v":
                nv += 1
                for c in map(float, t[1:4]):
                    if c < -1 or c > 2:
                        err(f"{path}: vertex outside -16..32 px model bounds: {line.strip()}")
                        return
            elif t[0] == "vt":
                nt += 1
            elif t[0] == "vn":
                nn += 1
            elif t[0] == "mtllib":
                mtllib = t[1]
            elif t[0] == "f":
                faces += 1
                if not 3 <= len(t) - 1 <= 4:
                    err(f"{path}: face with {len(t) - 1} vertices (Forge OBJ supports triangles and quads)")
                for ref in t[1:]:
                    parts = ref.split("/")
                    if len(parts) != 3 or not all(parts):
                        err(f"{path}: face vertex '{ref}' must be v/vt/vn")
                        return
                    v, vt, vn = map(int, parts)
                    if not (0 < v <= nv and 0 < vt <= nt and 0 < vn <= nn):
                        err(f"{path}: face index out of range '{ref}'")
                        return
    if faces == 0:
        err(f"{path}: no faces")
    if not mtllib or not os.path.exists(os.path.join(os.path.dirname(path), mtllib)):
        err(f"{path}: missing mtllib {mtllib}")
    return faces


def check_model(rl, where, seen):
    if rl in seen:
        return
    seen.add(rl)
    ns = rl.split(":")[0] if ":" in rl else "minecraft"
    if ns == "minecraft":
        return
    p = res_path(rl, "models", ".json")
    if not os.path.exists(p):
        err(f"{where}: missing model {rl}")
        return
    m = load(p)
    if m.get("loader") == "forge:obj":
        objrl = m["model"]
        objp = os.path.join(RES, "assets", objrl.split(":")[0], objrl.split(":")[1])
        if not os.path.exists(objp):
            err(f"{rl}: OBJ file {objrl} missing")
        else:
            check_obj(objp)
            mtl = objp[:-4] + ".mtl"
            if os.path.exists(mtl):
                for line in open(mtl):
                    if line.startswith("map_Kd"):
                        ref = line.split()[1]
                        if ref.startswith("#") and ref[1:] not in m.get("textures", {}):
                            err(f"{rl}: MTL references {ref} but the model defines no such texture")
        if not m.get("flip_v"):
            err(f"{rl}: OBJ model should set flip_v (Blender UVs)")
    for k, t in m.get("textures", {}).items():
        check_texture(t, rl)
    if "parent" in m:
        check_model(m["parent"], rl, seen)
    for ov in m.get("overrides", []):
        check_model(ov["model"], rl, seen)


def main():
    src = java_sources()
    all_java = "\n".join(src.values())
    blocks_java = src[os.path.join(JAVA, "registry/ModBlocks.java")]
    items_java = src[os.path.join(JAVA, "registry/ModItems.java")]
    blocks = re.findall(r'reg\("([a-z_]+)"', blocks_java)
    items = re.findall(r'tool\("([a-z_]+)"', items_java)
    lang = load(os.path.join(ASSETS, "lang/en_us.json"))
    seen = set()
    for b in blocks:
        bs = os.path.join(ASSETS, "blockstates", b + ".json")
        if not os.path.exists(bs):
            err(f"block {b}: missing blockstate")
        else:
            data = load(bs)
            for v in data.get("variants", {}).values():
                for entry in (v if isinstance(v, list) else [v]):
                    check_model(entry["model"], f"blockstate {b}", seen)
            for part in data.get("multipart", []):
                check_model(part["apply"]["model"], f"blockstate {b}", seen)
        check_model(f"scientia:item/{b}", f"block item {b}", seen)
        if f"block.scientia.{b}" not in lang:
            err(f"block {b}: missing lang entry")
        if not os.path.exists(os.path.join(RES, "data/scientia/loot_tables/blocks", b + ".json")):
            err(f"block {b}: missing loot table")
    for i in items:
        check_model(f"scientia:item/{i}", f"item {i}", seen)
        if f"item.scientia.{i}" not in lang:
            err(f"item {i}: missing lang entry")
    # item textures are 16x16
    for p in glob.glob(os.path.join(ASSETS, "textures/item/*.png")):
        if png_size(p) != (16, 16):
            err(f"{p}: item textures must be 16x16")
    check_texture("scientia:block/lab_atlas", "atlas", (256, 256))
    check_texture("scientia:block/liquid", "liquid renderer")
    # animated part models used by MachineRenderers
    for rl in re.findall(r'ScientiaMod\.id\("(block/[a-z_]+)"\)', src[os.path.join(JAVA, "client/render/MachineRenderers.java")]):
        check_model("scientia:" + rl, "MachineRenderers", seen)
    # lang keys used in code
    keys = set(re.findall(r'translatable\("([a-z0-9_.]+)"', all_java))
    keys |= set(re.findall(r'"(scientia\.msg\.[a-z_]+)"', all_java))
    for k in sorted(keys):
        if k.endswith(".") or k.startswith("block.scientia.machine"):  # prefixes of dynamically built keys
            continue
        if k not in lang:
            err(f"lang: key '{k}' used in code but missing from en_us.json")
    # recipes reference real items
    known = {f"scientia:{x}" for x in blocks + items}
    for p in glob.glob(os.path.join(RES, "data/scientia/recipes/*.json")):
        text = open(p).read()
        for ref in re.findall(r'"(scientia:[a-z_]+)"', text):
            if ref not in known:
                err(f"{os.path.basename(p)}: unknown item {ref}")
    wr = load(os.path.join(RES, "data/scientia/science/workbench_recipes.json"))
    for r in wr:
        for ref in [r["result"]["item"]] + [s["item"] for s in r["ingredients"]]:
            if ref.startswith("scientia:") and ref not in known:
                err(f"workbench recipe {r['id']}: unknown item {ref}")
    mats = load(os.path.join(RES, "data/scientia/science/materials.json"))
    for m in mats:
        if m["item"].startswith("scientia:") and m["item"] not in known:
            err(f"material {m['id']}: unknown item {m['item']}")
    for s in load(os.path.join(RES, "data/scientia/science/microscope.json")):
        ns, path = s["texture"].split(":")
        p = os.path.join(RES, "assets", ns, path)
        if not os.path.exists(p):
            err(f"microscope {s['id']}: missing image {s['texture']}")
        if s["item"].startswith("scientia:") and s["item"] not in known:
            err(f"microscope {s['id']}: unknown item {s['item']}")
    for f in ("META-INF/mods.toml", "pack.mcmeta"):
        if not os.path.exists(os.path.join(RES, f)):
            err(f"missing {f}")
    print(f"Checked {len(blocks)} blocks, {len(items)} items, {len(seen)} models, {len(keys)} lang keys.")
    for w in warnings:
        print("warning:", w)
    for e in errors:
        print("ERROR:", e)
    if errors:
        sys.exit(1)
    print("All asset checks passed.")


if __name__ == "__main__":
    main()
