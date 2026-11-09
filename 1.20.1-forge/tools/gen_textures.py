#!/usr/bin/env python3
"""Generates the particle textures of tacz_betterexplosion.

Pure Python 3 + Pillow, no other dependencies. Deterministic: every texture uses a fixed noise seed, and
running the script twice writes byte-identical PNGs.

The particles are tinted at runtime, so the colour channels are near-white/grey luminance and the artwork
lives in the alpha channel. The script can be started from any working directory; it writes into
src/main/resources/assets/tacz_betterexplosion/textures/particle/ relative to its own location.

Usage: python tools/gen_textures.py
"""

import hashlib
import math
from pathlib import Path

from PIL import Image

OUT_DIR = (
    Path(__file__).resolve().parent.parent
    / "src" / "main" / "resources" / "assets" / "tacz_betterexplosion" / "textures" / "particle"
)


# --------------------------------------------------------------------------------------------------
# Maths helpers
# --------------------------------------------------------------------------------------------------

def clamp01(x):
    return 0.0 if x < 0.0 else (1.0 if x > 1.0 else x)


def smoothstep(e0, e1, x):
    t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3.0 - 2.0 * t)


def lattice(ix, iy, seed):
    """Integer hash of a lattice point to [0, 1)."""
    n = (ix * 374761393 + iy * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return (n & 0xFFFFFF) / float(0x1000000)


def value_noise(x, y, seed):
    """Smooth 2D value noise in [0, 1]."""
    x0 = math.floor(x)
    y0 = math.floor(y)
    fx = x - x0
    fy = y - y0
    fx = fx * fx * (3.0 - 2.0 * fx)
    fy = fy * fy * (3.0 - 2.0 * fy)
    ix = int(x0)
    iy = int(y0)
    a = lattice(ix, iy, seed)
    b = lattice(ix + 1, iy, seed)
    c = lattice(ix, iy + 1, seed)
    d = lattice(ix + 1, iy + 1, seed)
    top = a + (b - a) * fx
    bottom = c + (d - c) * fx
    return top + (bottom - top) * fy


def fbm(x, y, seed, octaves):
    """Fractal value noise, normalised to [0, 1]."""
    total = 0.0
    norm = 0.0
    amplitude = 1.0
    frequency = 1.0
    for octave in range(octaves):
        total += amplitude * value_noise(x * frequency, y * frequency, seed + octave * 101)
        norm += amplitude
        amplitude *= 0.5
        frequency *= 2.0
    return total / norm


def edge_window(x, y, width, height, ramp):
    """0 on the outermost pixel ring, rising smoothly to 1 over `ramp` pixels."""
    distance = min(x, width - 1 - x, y, height - 1 - y)
    return smoothstep(0.0, float(ramp), float(distance))


def unit_coords(x, y, width, height):
    """Pixel centre mapped to [-1, 1] on both axes (v grows downwards, like the image rows)."""
    return (x + 0.5) / width * 2.0 - 1.0, (y + 0.5) / height * 2.0 - 1.0


def to_byte(value):
    return int(round(clamp01(value) * 255.0))


def build(width, height, pixel_fn):
    """pixel_fn(x, y) -> (luminance 0..1, alpha 0..1). Returns an RGBA image."""
    pixels = []
    for y in range(height):
        for x in range(width):
            lum, alpha = pixel_fn(x, y)
            lum_byte = to_byte(lum)
            pixels.append((lum_byte, lum_byte, lum_byte, to_byte(alpha)))
    image = Image.new("RGBA", (width, height))
    image.putdata(pixels)
    return image


# --------------------------------------------------------------------------------------------------
# Textures
# --------------------------------------------------------------------------------------------------

def fire_puff():
    """Near-white soft core, lumpy ragged edge."""
    size = 64

    def pixel(x, y):
        u, v = unit_coords(x, y, size, size)
        r = math.hypot(u, v)
        lumpy = fbm(u * 3.0 + 10.0, v * 3.0 + 10.0, 11, 3)
        ragged = r + (lumpy - 0.5) * 0.45
        radial = 1.0 - smoothstep(0.12, 0.95, ragged)
        alpha = radial * (0.75 + 0.25 * lumpy) * edge_window(x, y, size, size, 3)
        lum = 0.90 + 0.10 * radial * lumpy
        return lum, alpha

    return build(size, size, pixel)


def smoke_puff():
    """Light grey, lobed noise variation, lumpier alpha than the fire puff, max alpha 0.9."""
    size = 64
    raw = []
    for y in range(size):
        for x in range(size):
            u, v = unit_coords(x, y, size, size)
            r = math.hypot(u, v)
            lobes = fbm(u * 2.0 + 5.0, v * 2.0 + 5.0, 21, 3)
            detail = fbm(u * 5.0 + 20.0, v * 5.0 + 20.0, 22, 2)
            ragged = r + (lobes - 0.5) * 0.70
            radial = 1.0 - smoothstep(0.10, 0.90, ragged)
            alpha = radial * (0.45 + 0.55 * detail)
            lum = 0.75 + 0.25 * (0.6 * lobes + 0.4 * detail)
            raw.append((lum, alpha * edge_window(x, y, size, size, 3)))
    peak = max(a for _, a in raw)
    scale = 0.9 / peak if peak > 0.0 else 0.0

    def pixel(x, y):
        lum, alpha = raw[y * size + x]
        return lum, alpha * scale

    return build(size, size, pixel)


def flash():
    """Hard bright centre, soft halo, four faint cross streaks."""
    size = 64

    def pixel(x, y):
        u, v = unit_coords(x, y, size, size)
        r = math.hypot(u, v)
        core = 1.0 - smoothstep(0.08, 0.20, r)
        halo = 0.55 * math.exp(-((r / 0.38) ** 2) * 2.0)
        streak_h = math.exp(-((v / 0.035) ** 2)) * (1.0 - min(1.0, abs(u))) ** 2
        streak_v = math.exp(-((u / 0.035) ** 2)) * (1.0 - min(1.0, abs(v))) ** 2
        streak = 0.30 * max(streak_h, streak_v)
        alpha = min(1.0, core + halo + streak) * edge_window(x, y, size, size, 4)
        return 1.0, alpha

    return build(size, size, pixel)


def ring():
    """Thin soft ring at 0.9 of the half-width, gaussian cross-section, sigma 0.028 (about 6.6 percent FWHM)."""
    size = 128
    ring_radius = 0.9
    sigma = 0.028

    def pixel(x, y):
        u, v = unit_coords(x, y, size, size)
        r = math.hypot(u, v)
        profile = math.exp(-(((r - ring_radius) / sigma) ** 2) * 0.5)
        modulation = fbm(u * 4.0 + 30.0, v * 4.0 + 30.0, 31, 3)
        alpha = profile * (0.85 + 0.15 * modulation) * edge_window(x, y, size, size, 3)
        return 1.0, alpha

    return build(size, size, pixel)


def spark():
    """Vertical streak. HEAD = bottom of the image (rows near y = 63), TAIL fades to nothing at the top."""
    width = 16
    height = 64
    head = 0.90

    def pixel(x, y):
        u, _ = unit_coords(x, y, width, height)
        s = (y + 0.5) / height
        across = math.exp(-((u / 0.35) ** 2))
        if s <= head:
            along = (s / head) ** 2.2
        else:
            along = 1.0 - smoothstep(head, 1.0, s)
        alpha = across * along * edge_window(x, y, width, height, 2)
        lum = 0.85 + 0.15 * along
        return lum, alpha

    return build(width, height, pixel)


TEXTURES = {
    "fire_puff": (fire_puff, (64, 64)),
    "smoke_puff": (smoke_puff, (64, 64)),
    "flash": (flash, (64, 64)),
    "ring": (ring, (128, 128)),
    "spark": (spark, (16, 64)),
}


def assert_valid(name, image, size):
    assert image.mode == "RGBA", name + ": not RGBA"
    assert image.size == size, name + ": size " + str(image.size) + " != " + str(size)
    width, height = image.size
    alpha = image.getchannel("A")
    for x in range(width):
        assert alpha.getpixel((x, 0)) == 0 and alpha.getpixel((x, height - 1)) == 0, name + ": border row"
    for y in range(height):
        assert alpha.getpixel((0, y)) == 0 and alpha.getpixel((width - 1, y)) == 0, name + ": border column"


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, (factory, size) in TEXTURES.items():
        image = factory()
        assert_valid(name, image, size)
        path = OUT_DIR / (name + ".png")
        image.save(path, format="PNG", optimize=False, compress_level=9)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        print("{:<14} {:>3}x{:<3} {}".format(name + ".png", size[0], size[1], digest))


if __name__ == "__main__":
    main()
