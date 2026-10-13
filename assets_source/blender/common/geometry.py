"""Procedural mesh construction in Minecraft pixel space.

All builders take coordinates in Minecraft model pixels (x east, y up, z south; one block = 16 px) and
create Blender objects in Blender space (Z up) using
    blender = (x/16 − 0.5, −(z/16 − 0.5), y/16)
so the model's front (north, −Z in Minecraft) faces Blender +Y. The exporter applies the exact inverse.
Every face carries a tile name (material) used for atlas UV mapping.
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector


def mc_to_bl(x, y, z):
    return Vector((x / 16.0 - 0.5, -(z / 16.0 - 0.5), y / 16.0))


class Builder:
    """Accumulates geometry for one model part into a single bmesh, recording a tile per face."""

    def __init__(self, name):
        self.name = name
        self.bm = bmesh.new()
        self.tiles = []          # tile name per face index
        self.tile_layer = self.bm.faces.layers.int.new("tile")
        self.tile_ids = {}

    # -- low level ------------------------------------------------------------------------
    def _tile_id(self, tile):
        if tile not in self.tile_ids:
            self.tile_ids[tile] = len(self.tile_ids)
            self.tiles.append(tile)
        return self.tile_ids[tile]

    def face(self, pts, tile, outward=None):
        """Adds a polygon. If `outward` (a direction in pixel space) is given, the winding is corrected so
        the face normal points that way; primitives therefore never need hand-checked windings."""
        if outward is not None:
            n = newell(pts)
            if n[0] * outward[0] + n[1] * outward[1] + n[2] * outward[2] < 0:
                pts = list(pts)[::-1]
        verts = [self.bm.verts.new(mc_to_bl(*p)) for p in pts]
        f = self.bm.faces.new(verts)
        f[self.tile_layer] = self._tile_id(tile)
        return f

    # -- primitives -------------------------------------------------------------------------
    def box(self, x0, y0, z0, x1, y1, z1, tile, faces=None, rot=None):
        """Axis-aligned box; `faces` maps 'north','south','east','west','up','down' to tiles; `rot` = (axis, deg, pivot)."""
        faces = faces or {}
        c = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
        if rot:
            c = [rotate(p, *rot) for p in c]
        quads = {
            "north": (c[1], c[0], c[3], c[2]), "south": (c[4], c[5], c[6], c[7]),
            "west": (c[0], c[4], c[7], c[3]), "east": (c[5], c[1], c[2], c[6]),
            "up": (c[3], c[7], c[6], c[2]), "down": (c[0], c[1], c[5], c[4]),
        }
        for k, q in quads.items():
            if faces.get(k, tile) is None:
                continue
            self.face(q, faces.get(k, tile))

    def cylinder(self, cx, cy, cz, r, h, tile, axis="y", seg=12, caps=True, cap_tile=None, r_top=None, rot=None):
        """Cylinder (or frustum when r_top differs) starting at (cx, cy, cz) and extending h along +axis."""
        r_top = r if r_top is None else r_top
        ring0, ring1 = [], []
        for i in range(seg):
            a = 2 * math.pi * (i + 0.5) / seg
            ring0.append(axis_point(cx, cy, cz, axis, 0, r * math.cos(a), r * math.sin(a)))
            ring1.append(axis_point(cx, cy, cz, axis, h, r_top * math.cos(a), r_top * math.sin(a)))
        if rot:
            ring0 = [rotate(p, *rot) for p in ring0]
            ring1 = [rotate(p, *rot) for p in ring1]
        c0, c1 = centroid(ring0), centroid(ring1)
        axis_dir = sub(c1, c0)
        for i in range(seg):
            j = (i + 1) % seg
            quad = (ring0[i], ring0[j], ring1[j], ring1[i])
            self.face(quad, tile, outward=sub(centroid(quad), lerp(c0, c1, 0.5)))
        if caps:
            ct = cap_tile or tile
            self.face(ring0, ct, outward=neg(axis_dir))
            self.face(ring1, ct, outward=axis_dir)

    def tube(self, cx, cy, cz, r_out, r_in, h, tile, axis="y", seg=16, inner_tile=None):
        """Hollow cylinder with open-ring caps (glassware walls, collars)."""
        inner_tile = inner_tile or tile
        o0, o1, i0, i1 = [], [], [], []
        for k in range(seg):
            a = 2 * math.pi * (k + 0.5) / seg
            ca, sa = math.cos(a), math.sin(a)
            o0.append(axis_point(cx, cy, cz, axis, 0, r_out * ca, r_out * sa))
            o1.append(axis_point(cx, cy, cz, axis, h, r_out * ca, r_out * sa))
            i0.append(axis_point(cx, cy, cz, axis, 0, r_in * ca, r_in * sa))
            i1.append(axis_point(cx, cy, cz, axis, h, r_in * ca, r_in * sa))
        c0, c1 = centroid(o0), centroid(o1)
        ad = sub(c1, c0)
        mid = lerp(c0, c1, 0.5)
        for k in range(seg):
            j = (k + 1) % seg
            q = (o0[k], o0[j], o1[j], o1[k])
            self.face(q, tile, outward=sub(centroid(q), mid))
            q = (i0[j], i0[k], i1[k], i1[j])
            self.face(q, inner_tile, outward=sub(mid, centroid(q)))
            self.face((o1[k], o1[j], i1[j], i1[k]), tile, outward=ad)
            self.face((i0[k], i0[j], o0[j], o0[k]), tile, outward=neg(ad))

    def lathe(self, cx, cz, profile, tile, seg=16, y0=0.0, closed_bottom=True, inner=None, inner_tile=None):
        """Surface of revolution about a vertical axis. profile = [(radius, y), ...] bottom to top.
        If `inner` (a second profile) is given, the shell is closed into a thin-walled vessel."""
        rings = []
        for (r, y) in profile:
            ring = []
            for k in range(seg):
                a = 2 * math.pi * (k + 0.5) / seg
                ring.append((cx + r * math.cos(a), y0 + y, cz + r * math.sin(a)))
            rings.append(ring)
        for a_ring, b_ring in zip(rings, rings[1:]):
            for k in range(seg):
                j = (k + 1) % seg
                q = (a_ring[k], a_ring[j], b_ring[j], b_ring[k])
                c = centroid(q)
                self.face(q, tile, outward=(c[0] - cx, 0.0, c[2] - cz) if abs(c[0] - cx) + abs(c[2] - cz) > 1e-6 else (0, 1, 0))
        if closed_bottom and profile[0][0] > 0:
            self.face(rings[0], tile, outward=(0, -1, 0))
        if inner:
            it = inner_tile or tile
            irings = []
            for (r, y) in inner:
                irings.append([(cx + r * math.cos(2 * math.pi * (k + 0.5) / seg), y0 + y, cz + r * math.sin(2 * math.pi * (k + 0.5) / seg))
                               for k in range(seg)])
            for a_ring, b_ring in zip(irings, irings[1:]):
                for k in range(seg):
                    j = (k + 1) % seg
                    q = (a_ring[k], a_ring[j], b_ring[j], b_ring[k])
                    c = centroid(q)
                    self.face(q, it, outward=(cx - c[0], 0.0, cz - c[2]))
            top_o, top_i = rings[-1], irings[-1]
            for k in range(seg):
                j = (k + 1) % seg
                self.face((top_o[k], top_o[j], top_i[j], top_i[k]), tile, outward=(0, 1, 0))
            if inner[0][0] > 0:
                self.face(irings[0], it, outward=(0, 1, 0))

    def sweep(self, path, r, tile, seg=8, caps=True):
        """A tube of radius r swept along a polyline path [(x, y, z), ...] (hoses, condenser tubing)."""
        rings = []
        for i, p in enumerate(path):
            a = Vector(path[max(0, i - 1)])
            b = Vector(path[min(len(path) - 1, i + 1)])
            d = (b - a).normalized()
            up = Vector((0, 1, 0)) if abs(d.y) < 0.9 else Vector((1, 0, 0))
            u = d.cross(up).normalized()
            v = d.cross(u).normalized()
            ring = []
            for k in range(seg):
                ang = 2 * math.pi * (k + 0.5) / seg
                q = Vector(p) + (u * math.cos(ang) + v * math.sin(ang)) * r
                ring.append((q.x, q.y, q.z))
            rings.append(ring)
        for i, (a_ring, b_ring) in enumerate(zip(rings, rings[1:])):
            mid = lerp(path[i], path[i + 1], 0.5)
            for k in range(seg):
                j = (k + 1) % seg
                q = (a_ring[k], a_ring[j], b_ring[j], b_ring[k])
                self.face(q, tile, outward=sub(centroid(q), mid))
        if caps:
            self.face(rings[0], tile, outward=sub(path[0], path[1]))
            self.face(rings[-1], tile, outward=sub(path[-1], path[-2]))

    def sphere(self, cx, cy, cz, r, tile, seg=12, rings=8):
        prof = []
        for i in range(rings + 1):
            t = math.pi * i / rings - math.pi / 2
            prof.append((max(0.0001, r * math.cos(t)), r * math.sin(t)))
        self.lathe(cx, cz, prof, tile, seg=seg, y0=cy, closed_bottom=False)

    # -- finish -----------------------------------------------------------------------------
    def to_object(self, collection):
        bmesh.ops.remove_doubles(self.bm, verts=self.bm.verts, dist=1e-6)
        mesh = bpy.data.meshes.new(self.name)
        self.bm.to_mesh(mesh)
        tile_attr = mesh.attributes.get("tile")
        obj = bpy.data.objects.new(self.name, mesh)
        collection.objects.link(obj)
        obj["tiles"] = list(self.tiles)
        self.bm.free()
        return obj


def axis_point(cx, cy, cz, axis, along, a, b):
    if axis == "y":
        return (cx + a, cy + along, cz + b)
    if axis == "x":
        return (cx + along, cy + a, cz + b)
    return (cx + a, cy + b, cz + along)


def rotate(p, axis, deg, pivot):
    """Rotates a pixel-space point about an axis through `pivot` (right-handed, degrees)."""
    a = math.radians(deg)
    x, y, z = p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]
    ca, sa = math.cos(a), math.sin(a)
    if axis == "x":
        y, z = y * ca - z * sa, y * sa + z * ca
    elif axis == "y":
        x, z = x * ca + z * sa, -x * sa + z * ca
    else:
        x, y = x * ca - y * sa, x * sa + y * ca
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def newell(pts):
    nx = ny = nz = 0.0
    n = len(pts)
    for i in range(n):
        x1, y1, z1 = pts[i]
        x2, y2, z2 = pts[(i + 1) % n]
        nx += (y1 - y2) * (z1 + z2)
        ny += (z1 - z2) * (x1 + x2)
        nz += (x1 - x2) * (y1 + y2)
    return (nx, ny, nz)


def centroid(pts):
    n = float(len(pts))
    return (sum(p[0] for p in pts) / n, sum(p[1] for p in pts) / n, sum(p[2] for p in pts) / n)


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def neg(a):
    return (-a[0], -a[1], -a[2])


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)
