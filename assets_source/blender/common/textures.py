"""Procedural texture generation: the shared 256×256 block atlas (16 px tiles), item icons,
microscope specimen images and a few standalone block textures.

Every tile is drawn at Minecraft's native 16 px resolution so the models read clearly in-game.
"""
import math
import os

import numpy as np

from . import raster as R

TILE = 16
ATLAS_TILES = 16  # 16 × 16 tiles = 256 × 256 px

# 3×5 pixel font for labels (digits and a few symbols).
FONT = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
    "V": ["101", "101", "101", "101", "010"], "+": ["000", "010", "111", "010", "000"],
    "-": ["000", "000", "111", "000", "000"], "A": ["010", "101", "111", "101", "101"],
    "H": ["101", "101", "111", "101", "101"], "p": ["000", "110", "101", "110", "100"],
}


def text(img, x, y, s, color):
    for ch in s:
        glyph = FONT.get(ch)
        if glyph:
            for gy, row in enumerate(glyph):
                for gx, c in enumerate(row):
                    if c == "1":
                        R.rect(img, x + gx, y + gy, x + gx + 1, y + gy + 1, color)
        x += 4


def _shade(t, base, amount, seed, cells=4, streak=None):
    h, w = t.shape[:2]
    n = R.value_noise(h, w, seed, cells) - 0.5
    if streak == "h":
        n = n * 0.4 + (R.noise(h, 1, seed + 7) - 0.5) * 0.9
    elif streak == "v":
        n = n * 0.4 + (R.noise(1, w, seed + 7) - 0.5) * 0.9
    c = np.array(R.rgb(base), dtype=np.float32)[:3] / 255.0
    t[..., :3] = np.clip(c + n[..., None] * amount, 0, 1)
    t[..., 3] = 1.0


def _bevel(t, light=0.10, dark=0.12):
    t[0, :, :3] = np.clip(t[0, :, :3] + light, 0, 1)
    t[:, 0, :3] = np.clip(t[:, 0, :3] + light * 0.6, 0, 1)
    t[-1, :, :3] = np.clip(t[-1, :, :3] - dark, 0, 1)
    t[:, -1, :3] = np.clip(t[:, -1, :3] - dark * 0.6, 0, 1)


def _solid(base, amount=0.04, seed=1, bevel=True, streak=None, cells=4):
    def f(t):
        _shade(t, base, amount, seed, cells, streak)
        if bevel:
            _bevel(t)
    return f


def _glass(tint=(200, 235, 245), alpha=70, edge=150):
    def f(t):
        t[...] = 0
        t[..., 0], t[..., 1], t[..., 2] = tint[0] / 255, tint[1] / 255, tint[2] / 255
        t[..., 3] = alpha / 255
        t[0, :, 3] = t[-1, :, 3] = t[:, 0, 3] = t[:, -1, 3] = edge / 255
        for i in range(16):
            j = 15 - i
            if 2 <= i <= 6:
                t[j - 3 if j - 3 >= 0 else 0, i, :] = (1, 1, 1, 0.45)
    return f


def _emissive(base, scan=True):
    def f(t):
        _shade(t, base, 0.06, 3, 4)
        if scan:
            t[::2, :, :3] *= 0.92
    return f


def _brick(t):
    _shade(t, 0xA9533A, 0.10, 11, 6)
    mortar = np.array(R.rgb(0x8C7F72), dtype=np.float32)[:3] / 255
    for y in (0, 8):
        t[y, :, :3] = mortar
    for x, y0, y1 in ((0, 0, 8), (8, 8, 16)):
        t[y0:y1, x, :3] = mortar


def _wood(t):
    _shade(t, 0x9C6B3C, 0.05, 21, 3, streak="h")
    for y in (3, 9, 14):
        t[y, :, :3] *= 0.85


def _vent(t):
    _shade(t, 0x2B3036, 0.03, 31)
    for y in range(3, 14, 3):
        t[y:y + 1, 2:14, :3] = 0.06
        t[y + 1:y + 2, 2:14, :3] *= 1.25
    _bevel(t)


def _hazard(t):
    for y in range(16):
        for x in range(16):
            t[y, x] = (0.95, 0.75, 0.10, 1) if ((x + y) // 4) % 2 == 0 else (0.08, 0.08, 0.08, 1)


def _screen(kind):
    def f(t):
        _shade(t, 0x0E1A1E, 0.02, 41)
        t[4::4, :, :3] += 0.04
        t[:, 4::4, :3] += 0.04
        if kind == "graph":
            pts = [(1, 13), (4, 11), (7, 7), (10, 7), (13, 4), (15, 3)]
            for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
                R.line(t, x0, y0, x1, y1, R.rgb(0x6CFFE8), 1.0)
        elif kind == "spectrum":
            for x in range(1, 15):
                nm = 380 + (x - 1) * 26
                c = _wavelength_rgb(nm)
                t[10:13, x, :3] = c
            for x, hgt in ((3, 7), (9, 9), (11, 5)):
                t[10 - hgt:10, x, :3] = (0.9, 0.95, 1.0)
        elif kind == "digits":
            text(t, 2, 5, "88", R.rgb(0x6CFFE8))
        _bevel(t, 0.05, 0.05)
    return f


def _wavelength_rgb(nm):
    r = g = b = 0.0
    if 380 <= nm < 440:
        r, b = -(nm - 440) / 60, 1
    elif nm < 490:
        g, b = (nm - 440) / 50, 1
    elif nm < 510:
        g, b = 1, -(nm - 510) / 20
    elif nm < 580:
        r, g = (nm - 510) / 70, 1
    elif nm < 645:
        r, g = 1, -(nm - 645) / 65
    elif nm <= 750:
        r = 1
    return (r, g, b)


def _dial(t):
    t[...] = (0.12, 0.13, 0.15, 1)
    m = R.circle_mask(16, 16, 8, 8, 7)
    R.blend(t, m, (238, 238, 232, 255))
    for k in range(9):
        a = math.radians(225 - k * 33.75)
        R.line(t, 8 + math.cos(a) * 5.2, 8 - math.sin(a) * 5.2, 8 + math.cos(a) * 6.6, 8 - math.sin(a) * 6.6, (40, 40, 40, 255), 0.8)
    R.line(t, 8, 8, 11.5, 4.5, (220, 40, 40, 255), 1.0)


def _solar(t):
    _shade(t, 0x1C2C5A, 0.05, 51)
    t[0::8, :, :3] = (0.75, 0.78, 0.82)
    t[:, 0::8, :3] = (0.75, 0.78, 0.82)
    t[4::8, :, :3] = (0.45, 0.50, 0.60)


def _coil(t):
    _shade(t, 0xC87533, 0.08, 61)
    t[::2, :, :3] *= 0.75


def _pcb(t):
    _shade(t, 0x1F6B3A, 0.04, 71)
    for (x0, y0, x1, y1) in ((2, 3, 13, 3), (13, 3, 13, 12), (3, 8, 9, 8), (9, 8, 9, 14)):
        R.line(t, x0, y0, x1, y1, R.rgb(0xD4B44A), 1.0)
    for (x, y) in ((2, 3), (13, 12), (3, 8), (9, 14)):
        R.rect(t, x - 1, y - 1, x + 2, y + 2, R.rgb(0xE8D070))


def _knob(t):
    _shade(t, 0x1A1B1E, 0.05, 81)
    t[:, ::2, :3] *= 0.6


def _label(s, bg, fg):
    def f(t):
        _shade(t, bg, 0.03, 91)
        text(t, (16 - 4 * len(s) + 1) // 2, 5, s, R.rgb(fg))
        _bevel(t, 0.04, 0.06)
    return f


def _paper(t):
    _shade(t, 0xF2F0E8, 0.02, 101)
    t[3::3, 1:15, :3] = (0.70, 0.78, 0.90)


def _band(c):
    return _solid(c, 0.04, 5, bevel=False)


TILES = [
    ("graphite", _solid(0x2B3036, 0.05, 1)),
    ("steel", _solid(0x9AA4AE, 0.10, 2, streak="h")),
    ("steel_dark", _solid(0x5C646C, 0.08, 3, streak="h")),
    ("chrome", _solid(0xC9D0D6, 0.12, 4, streak="v")),
    ("white_enamel", _solid(0xE6EAED, 0.03, 5)),
    ("ceramic", _solid(0xD3CEC4, 0.06, 6, cells=8)),
    ("glass", _glass()),
    ("glass_frost", _glass((228, 238, 244), 150, 190)),
    ("copper", _solid(0xC87533, 0.10, 7)),
    ("brass", _solid(0xC9A84A, 0.10, 8)),
    ("rubber", _solid(0x1E1F22, 0.04, 9, bevel=False)),
    ("wood", _wood),
    ("brick", _brick),
    ("emissive_cyan", _emissive(0x4FD8E8)),
    ("emissive_amber", _emissive(0xFFB347)),
    ("emissive_red", _emissive(0xFF5A2A, scan=False)),
    ("emissive_green", _emissive(0x6BFF7A, scan=False)),
    ("screen", _screen("plain")),
    ("screen_graph", _screen("graph")),
    ("screen_spectrum", _screen("spectrum")),
    ("screen_digits", _screen("digits")),
    ("dial", _dial),
    ("vent", _vent),
    ("hazard", _hazard),
    ("resistor_body", _solid(0xD8C79A, 0.05, 10, bevel=False)),
    ("band_black", _band(0x1A1A1A)),
    ("band_brown", _band(0x7A4520)),
    ("band_red", _band(0xC0302A)),
    ("band_yellow", _band(0xE8C820)),
    ("band_violet", _band(0x7A3AB8)),
    ("band_gold", _band(0xD8B040)),
    ("solar_cell", _solar),
    ("coil", _coil),
    ("pcb", _pcb),
    ("knob", _knob),
    ("label_12v", _label("12V", 0xE6EAED, 0x1A1A1A)),
    ("label_plus", _label("+", 0xC0302A, 0xFFFFFF)),
    ("label_minus", _label("-", 0x1E1F22, 0xFFFFFF)),
    ("lens", _glass((150, 200, 255), 120, 180)),
    ("paper", _paper),
    ("wood_dark", _solid(0x5A3A22, 0.05, 12, streak="h")),
    ("filament", _solid(0x404040, 0.03, 13, bevel=False)),
    ("blue_plastic", _solid(0x2F6FB8, 0.04, 14)),
    ("red_plastic", _solid(0xB8302F, 0.04, 15)),
    ("graphite_rod", _solid(0x1C1C1C, 0.08, 16, bevel=False)),
    ("glass_amber", _glass((255, 200, 120), 110, 170)),
    ("ash_grate", _emissive(0x6A2A10, scan=False)),
]
EMISSIVE = {"emissive_cyan", "emissive_amber", "emissive_red", "emissive_green", "screen_graph", "screen_spectrum",
            "screen_digits", "glass_amber"}
TRANSLUCENT = {"glass", "glass_frost", "lens", "glass_amber"}
TILE_INDEX = {name: i for i, (name, _) in enumerate(TILES)}


def tile_rect(name):
    """Pixel rectangle (x0, y0) of a tile in the atlas (origin top-left)."""
    i = TILE_INDEX[name]
    return (i % ATLAS_TILES) * TILE, (i // ATLAS_TILES) * TILE


def build_atlas():
    size = TILE * ATLAS_TILES
    atlas = R.canvas(size, size, (255, 0, 255, 255))  # magenta marks unused tiles
    emission = R.canvas(size, size, (0, 0, 0, 255))
    for name, fn in TILES:
        t = R.canvas(TILE, TILE)
        fn(t)
        x, y = tile_rect(name)
        atlas[y:y + TILE, x:x + TILE] = t
        if name in EMISSIVE:
            emission[y:y + TILE, x:x + TILE, :3] = t[..., :3]
    return atlas, emission


# ---------------------------------------------------------------------------------------- items

def _beaker(filled):
    t = R.canvas(16, 16)
    glass, edge = (200, 235, 245, 110), (230, 245, 250, 230)
    R.rect(t, 3, 3, 13, 15, glass)
    R.rect(t, 2, 2, 14, 3, edge)
    R.rect(t, 3, 3, 4, 15, edge)
    R.rect(t, 12, 3, 13, 15, edge)
    R.rect(t, 3, 14, 13, 15, edge)
    R.rect(t, 13, 2, 15, 4, edge)
    if filled:
        R.rect(t, 4, 7, 12, 14, (63, 118, 228, 220))
        R.rect(t, 4, 7, 12, 8, (140, 185, 255, 240))
    for y in (6, 9, 12):
        R.rect(t, 9, y, 12, y + 1, (255, 255, 255, 200))
    return t


def _thermometer():
    t = R.canvas(16, 16)
    R.line(t, 4, 12, 12, 4, (225, 235, 240, 255), 3.0)
    R.line(t, 4.5, 11.5, 11.5, 4.5, (220, 50, 40, 255), 1.0)
    m = R.circle_mask(16, 16, 4, 12, 2.4)
    R.blend(t, m, (220, 50, 40, 255))
    R.rect(t, 11, 2, 14, 5, (60, 70, 80, 255))
    return t


def _multimeter():
    t = R.canvas(16, 16)
    R.rect(t, 3, 1, 13, 15, (240, 176, 64, 255))
    R.rect(t, 4, 2, 12, 14, (40, 44, 50, 255))
    R.rect(t, 5, 3, 11, 6, (108, 255, 232, 255))
    text(t, 5, 3, "88", (10, 40, 40, 255))
    m = R.circle_mask(16, 16, 8, 10, 2.2)
    R.blend(t, m, (200, 200, 200, 255))
    R.line(t, 8, 10, 9.5, 8.5, (30, 30, 30, 255), 1.0)
    R.rect(t, 5, 13, 6, 15, (200, 40, 40, 255))
    R.rect(t, 10, 13, 11, 15, (20, 20, 20, 255))
    return t


def _ph_meter():
    t = R.canvas(16, 16)
    R.rect(t, 5, 1, 11, 8, (47, 111, 184, 255))
    R.rect(t, 6, 2, 10, 5, (108, 255, 232, 255))
    text(t, 6, 2, "p", (10, 40, 40, 255))
    R.rect(t, 7, 8, 9, 15, (190, 200, 210, 255))
    R.rect(t, 7, 13, 9, 15, (220, 240, 255, 255))
    return t


def _journal():
    t = R.canvas(16, 16)
    R.rect(t, 2, 1, 14, 15, (28, 35, 43, 255))
    R.rect(t, 3, 2, 13, 14, (40, 50, 60, 255))
    R.rect(t, 2, 1, 4, 15, (79, 216, 232, 255))
    R.blend(t, R.circle_mask(16, 16, 9, 7, 3.2), (79, 216, 232, 255))
    R.blend(t, R.circle_mask(16, 16, 9, 7, 2.2), (40, 50, 60, 255))
    R.rect(t, 8, 6, 10, 8, (240, 176, 64, 255))
    R.rect(t, 6, 11, 12, 12, (139, 152, 165, 255))
    return t


def _calculator():
    t = R.canvas(16, 16)
    R.rect(t, 3, 1, 13, 15, (50, 56, 64, 255))
    R.rect(t, 4, 2, 12, 5, (160, 200, 150, 255))
    for yy in range(6, 14, 2):
        for xx in range(4, 12, 2):
            R.rect(t, xx, yy, xx + 1, yy + 1, (220, 225, 230, 255) if not (xx == 10 and yy == 12) else (240, 176, 64, 255))
    return t


def _canister(filled):
    t = R.canvas(16, 16)
    body = (154, 164, 174, 255) if not filled else (90, 170, 110, 255)
    R.rect(t, 5, 4, 11, 15, body)
    R.rect(t, 5, 4, 6, 15, (200, 210, 220, 255))
    R.rect(t, 7, 1, 9, 4, (80, 80, 80, 255))
    R.rect(t, 6, 2, 10, 3, (60, 60, 60, 255))
    R.rect(t, 5, 8, 11, 10, (240, 176, 64, 255) if filled else (110, 118, 126, 255))
    return t


def _mortar():
    t = R.canvas(16, 16)
    R.blend(t, R.ellipse_mask(16, 16, 8, 11, 6, 4), (190, 190, 182, 255))
    R.rect(t, 2, 7, 14, 11, (0, 0, 0, 0))
    R.blend(t, R.ellipse_mask(16, 16, 8, 9, 6, 2), (120, 118, 112, 255))
    R.line(t, 9, 9, 13, 2, (210, 205, 195, 255), 2.0)
    return t


def _powder(color, crystals=False):
    t = R.canvas(16, 16)
    base = R.rgb(color)
    R.blend(t, R.ellipse_mask(16, 16, 8, 11, 6.5, 3.5), base)
    R.blend(t, R.ellipse_mask(16, 16, 8, 9, 4.5, 3.5), base)
    n = R.noise(16, 16, color & 255)
    t[..., :3] *= (0.85 + 0.3 * n[..., None])
    if crystals:
        for (x, y) in ((5, 7), (9, 5), (11, 9), (7, 11)):
            R.rect(t, x, y, x + 2, y + 2, (255, 255, 255, 255))
            R.rect(t, x + 1, y + 1, x + 2, y + 2, (200, 210, 220, 255))
    return t


ITEMS = {
    "beaker": lambda: _beaker(False), "beaker_filled": lambda: _beaker(True),
    "thermometer": _thermometer, "multimeter": _multimeter, "ph_meter": _ph_meter,
    "science_journal": _journal, "calculator": _calculator,
    "gas_canister": lambda: _canister(False), "gas_canister_filled": lambda: _canister(True),
    "mortar_and_pestle": _mortar,
    "salt_crystals": lambda: _powder(0xF4F6F8, True), "calcite_powder": lambda: _powder(0xEDE9DF),
    "quicklime": lambda: _powder(0xE9E7E0, True), "slaked_lime": lambda: _powder(0xF7F7F2),
    "copper_oxide_powder": lambda: _powder(0x26221F),
}


# ---------------------------------------------------------------------------------------- microscope

def _micro_base(seed, color):
    img = R.canvas(96, 96, R.rgb(color))
    v = R.value_noise(96, 96, seed, 6)
    img[..., :3] *= (0.92 + 0.12 * v[..., None])
    vign = R.circle_mask(96, 96, 48, 48, 47, soft=6)
    img[..., :3] *= vign[..., None]
    return img


def micro_sand():
    img = _micro_base(1, 0xD9D4C8)
    rng = np.random.default_rng(1)
    for _ in range(22):
        cx, cy, r = rng.uniform(8, 88), rng.uniform(8, 88), rng.uniform(6, 13)
        pts = [(cx + r * (0.75 + 0.25 * rng.random()) * math.cos(a), cy + r * (0.75 + 0.25 * rng.random()) * math.sin(a))
               for a in np.linspace(0, 2 * math.pi, 9, endpoint=False)]
        m = R.polygon_mask(96, 96, pts)
        R.blend(img, m, (226, 205, 160, 255), 0.75)
        R.blend(img, R.circle_mask(96, 96, cx - r * 0.3, cy - r * 0.3, r * 0.25, 2), (255, 250, 235, 255), 0.6)
    return img


def micro_salt():
    img = _micro_base(2, 0x30343A)
    rng = np.random.default_rng(2)
    for _ in range(14):
        cx, cy, s = rng.uniform(10, 86), rng.uniform(10, 86), rng.uniform(5, 11)
        R.rect(img, cx - s, cy - s, cx + s, cy + s, (235, 240, 245, 255))
        R.rect(img, cx - s + 2, cy - s + 2, cx + s - 2, cy + s - 2, (200, 210, 220, 255))
        R.rect(img, cx - s, cy - s, cx + s, cy - s + 1.5, (255, 255, 255, 255))
    return img


def micro_sugar():
    img = _micro_base(3, 0x30343A)
    rng = np.random.default_rng(3)
    for _ in range(12):
        cx, cy, s = rng.uniform(12, 84), rng.uniform(12, 84), rng.uniform(6, 11)
        k = s * 0.5
        pts = [(cx - s, cy - s * 0.6), (cx + s - k, cy - s * 0.6), (cx + s, cy + s * 0.6), (cx - s + k, cy + s * 0.6)]
        R.blend(img, R.polygon_mask(96, 96, pts), (238, 236, 228, 255))
        R.line(img, pts[0][0], pts[0][1], pts[1][0], pts[1][1], (255, 255, 255, 255), 1.2)
    return img


def _voronoi(seed, n, size=96):
    rng = np.random.default_rng(seed)
    pts = rng.uniform(0, size, (n, 2))
    yy, xx = np.mgrid[0:size, 0:size] + 0.5
    d = np.stack([(xx - p[0]) ** 2 + (yy - p[1]) ** 2 for p in pts])
    order = np.sort(d, axis=0)
    edge = np.sqrt(order[1]) - np.sqrt(order[0])
    return np.argmin(d, axis=0), edge, pts


def micro_leaf():
    img = _micro_base(4, 0x9CC77A)
    cells, edge, pts = _voronoi(4, 26)
    R.blend(img, (edge < 1.6).astype(np.float32), (60, 110, 50, 255), 0.9)
    for p in pts[::4]:
        R.blend(img, R.ellipse_mask(96, 96, p[0], p[1], 4.5, 2.6, 0.5), (70, 130, 60, 255))
        R.blend(img, R.ellipse_mask(96, 96, p[0], p[1], 1.6, 0.8, 0.5), (30, 50, 25, 255))
    return img


def micro_kelp():
    img = _micro_base(5, 0xB8A060)
    for y in range(0, 96, 12):
        off = 0 if (y // 12) % 2 == 0 else 8
        for x in range(-8, 96, 16):
            R.rect(img, x + off, y, x + off + 15, y + 1, (90, 70, 30, 255))
            R.rect(img, x + off, y, x + off + 1, y + 12, (90, 70, 30, 255))
    rng = np.random.default_rng(5)
    for _ in range(160):
        x, y = rng.uniform(2, 94, 2)
        R.blend(img, R.circle_mask(96, 96, x, y, 1.4), (120, 80, 20, 255))
    return img


def micro_feather():
    img = _micro_base(6, 0xE8E4DA)
    R.line(img, 10, 88, 86, 8, (120, 100, 80, 255), 3.0)
    for k in range(0, 100, 7):
        x0, y0 = 10 + k * 0.76, 88 - k * 0.8
        R.line(img, x0, y0, x0 + 30, y0 + 22, (150, 135, 115, 255), 1.0)
        R.line(img, x0, y0, x0 - 22, y0 - 26, (150, 135, 115, 255), 1.0)
    return img


def micro_iron():
    img = _micro_base(7, 0xB0B6BC)
    cells, edge, _ = _voronoi(7, 30)
    shade = (cells % 5) / 5.0
    img[..., :3] *= (0.85 + 0.15 * shade[..., None])
    R.blend(img, (edge < 1.3).astype(np.float32), (40, 44, 48, 255))
    return img


def micro_bone():
    img = _micro_base(8, 0xE6DCC8)
    for (cx, cy) in ((30, 32), (66, 36), (46, 70), (80, 74), (14, 74)):
        for r in range(14, 2, -3):
            ring = np.clip(1 - np.abs(np.sqrt((np.mgrid[0:96, 0:96][1] + 0.5 - cx) ** 2 + (np.mgrid[0:96, 0:96][0] + 0.5 - cy) ** 2) - r), 0, 1)
            R.blend(img, ring, (170, 150, 120, 255), 0.6)
        R.blend(img, R.circle_mask(96, 96, cx, cy, 2.5), (90, 60, 40, 255))
    return img


MICROSCOPE = {"sand_grains": micro_sand, "salt_crystals": micro_salt, "sugar_crystals": micro_sugar, "leaf_epidermis": micro_leaf,
              "kelp_cells": micro_kelp, "feather_barbs": micro_feather, "iron_grains": micro_iron, "bone": micro_bone}


def standalone():
    """Small textures used by JSON models, particles and liquid rendering."""
    out = {}
    steel = R.canvas(16, 16)
    _solid(0x9AA4AE, 0.10, 2, streak="h")(steel)
    out["block/steel"] = steel
    liquid = R.canvas(16, 16)
    v = R.value_noise(16, 16, 77, 4)
    liquid[..., :3] = (0.86 + 0.14 * v)[..., None]
    liquid[..., 3] = 1.0
    out["block/liquid"] = liquid
    wire = R.canvas(16, 16)
    _shade(wire, 0xC87533, 0.10, 7)
    wire[5:11, :, :3] = np.clip(wire[5:11, :, :3] * 1.1, 0, 1)
    out["block/copper_wire"] = wire
    insul = R.canvas(16, 16)
    _shade(insul, 0x2B2F36, 0.04, 8)
    out["block/wire_insulation"] = insul
    return out


def write_all(assets_dir):
    """Writes every texture into the mod's assets directory and returns the list of files."""
    written = []
    tex = os.path.join(assets_dir, "textures")
    for d in ("block", "item", "gui/microscope"):
        os.makedirs(os.path.join(tex, d), exist_ok=True)
    atlas, emission = build_atlas()
    p = os.path.join(tex, "block", "lab_atlas.png")
    R.write_png(p, atlas)
    written.append(p)
    for name, img in standalone().items():
        p = os.path.join(tex, name + ".png")
        R.write_png(p, img)
        written.append(p)
    for name, fn in ITEMS.items():
        p = os.path.join(tex, "item", name + ".png")
        R.write_png(p, fn())
        written.append(p)
    for name, fn in MICROSCOPE.items():
        p = os.path.join(tex, "gui", "microscope", name + ".png")
        R.write_png(p, fn())
        written.append(p)
    return written, atlas, emission
