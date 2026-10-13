"""Tiny numpy raster toolkit and dependency-free PNG writer (deterministic output)."""
import struct
import zlib

import numpy as np


def canvas(w, h, rgba=(0, 0, 0, 0)):
    img = np.zeros((h, w, 4), dtype=np.float32)
    img[:, :] = np.array(rgba, dtype=np.float32) / 255.0
    return img


def rgb(hexcolor, a=255):
    return ((hexcolor >> 16) & 255, (hexcolor >> 8) & 255, hexcolor & 255, a)


def rect(img, x0, y0, x1, y1, color):
    """Fills [x0, x1) × [y0, y1) with an RGBA colour (0-255), clipped to the image."""
    h, w = img.shape[:2]
    x0, x1 = max(0, int(x0)), min(w, int(x1))
    y0, y1 = max(0, int(y0)), min(h, int(y1))
    if x0 < x1 and y0 < y1:
        img[y0:y1, x0:x1] = np.array(color, dtype=np.float32) / 255.0


def blend(img, mask, color, alpha=1.0):
    """Alpha-blends a colour where mask (float 0..1, same HxW) is set."""
    c = np.array(color, dtype=np.float32) / 255.0
    a = (mask * alpha * c[3])[..., None]
    img[..., :3] = img[..., :3] * (1 - a) + c[:3] * a
    img[..., 3:4] = np.maximum(img[..., 3:4], a)


def circle_mask(h, w, cx, cy, r, soft=0.7):
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    return np.clip((r - d) / soft + 0.5, 0, 1)


def ellipse_mask(h, w, cx, cy, rx, ry, angle=0.0, soft=0.7):
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    ca, sa = np.cos(angle), np.sin(angle)
    dx, dy = xx - cx, yy - cy
    u = (dx * ca + dy * sa) / rx
    v = (-dx * sa + dy * ca) / ry
    d = np.sqrt(u * u + v * v)
    return np.clip((1 - d) * min(rx, ry) / soft + 0.5, 0, 1)


def polygon_mask(h, w, pts):
    """Even-odd polygon fill (pixel centres)."""
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    inside = np.zeros((h, w), dtype=bool)
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        cond = ((y1 > yy) != (y2 > yy))
        with np.errstate(divide="ignore", invalid="ignore"):
            xint = (x2 - x1) * (yy - y1) / (y2 - y1 + 1e-12) + x1
        inside ^= cond & (xx < xint)
    return inside.astype(np.float32)


def line(img, x0, y0, x1, y1, color, width=1.0):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy + 1e-12
    t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / L2, 0, 1)
    px, py = x0 + t * dx, y0 + t * dy
    d = np.sqrt((xx - px) ** 2 + (yy - py) ** 2)
    blend(img, np.clip(width / 2 - d + 0.5, 0, 1), color)


def noise(h, w, seed, scale=1.0):
    rng = np.random.default_rng(seed)
    return rng.random((h, w)).astype(np.float32) * scale


def value_noise(h, w, seed, cells=4):
    """Smooth value noise in [0, 1]."""
    rng = np.random.default_rng(seed)
    grid = rng.random((cells + 1, cells + 1)).astype(np.float32)
    ys = np.linspace(0, cells, h, endpoint=False)
    xs = np.linspace(0, cells, w, endpoint=False)
    yi, xi = np.floor(ys).astype(int), np.floor(xs).astype(int)
    fy, fx = (ys - yi)[:, None], (xs - xi)[None, :]
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a = grid[yi][:, xi]
    b = grid[yi][:, xi + 1]
    c = grid[yi + 1][:, xi]
    d = grid[yi + 1][:, xi + 1]
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy


def write_png(path, img):
    """Writes an RGBA float image (H×W×4, 0..1) as an 8-bit PNG."""
    data = (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)
    h, w = data.shape[:2]
    raw = b"".join(b"\x00" + data[y].tobytes() for y in range(h))

    def chunk(tag, payload):
        c = struct.pack(">I", len(payload)) + tag + payload
        return c + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def read_png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")
    return struct.unpack(">II", head[16:24])
