"""Atlas UV mapping: every face is projected along its dominant normal axis and fitted into the
inner area of its tile (with a half-texel inset to avoid bleeding between neighbouring tiles)."""
import bmesh
from mathutils import Vector

from . import textures

ATLAS = textures.TILE * textures.ATLAS_TILES
INSET = 0.5  # texels


def map_object(obj):
    tiles = list(obj["tiles"])
    me = obj.data
    bm = bmesh.new()
    bm.from_mesh(me)
    layer = bm.faces.layers.int.get("tile")
    uvl = bm.loops.layers.uv.verify()
    for f in bm.faces:
        tile = tiles[f[layer]] if layer is not None else tiles[0]
        tx, ty = textures.tile_rect(tile)
        n = f.normal
        ax = max(range(3), key=lambda i: abs(n[i]))
        normal = Vector((0, 0, 0))
        normal[ax] = 1.0 if n[ax] > 0 else -1.0
        # Texture "up" is world up (Blender +Z) on side faces and north (Blender +Y) on top/bottom faces.
        up = Vector((0, 0, 1)) if ax != 2 else Vector((0, 1, 0))
        right = (-normal).cross(up)        # viewer looks along −normal; right = forward × up
        coords = [(l.vert.co.dot(right), l.vert.co.dot(up)) for l in f.loops]
        us = [c[0] for c in coords]
        vs = [c[1] for c in coords]
        u0, u1, v0, v1 = min(us), max(us), min(vs), max(vs)
        du, dv = max(u1 - u0, 1e-6), max(v1 - v0, 1e-6)
        for l, (u, v) in zip(f.loops, coords):
            fu = (u - u0) / du
            fv = (v - v0) / dv
            px = tx + INSET + fu * (textures.TILE - 2 * INSET)
            py = ty + INSET + (1 - fv) * (textures.TILE - 2 * INSET)  # image rows go down
            l[uvl].uv = (px / ATLAS, 1 - py / ATLAS)                  # Blender UV origin is bottom-left
    bm.to_mesh(me)
    bm.free()
