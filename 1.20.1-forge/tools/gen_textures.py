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


def warp(u, v, seed, amplitude, frequency):
    """Domain warp: shifts (u, v) by two independent smooth noise fields."""
    du = fbm(u * frequency + 7.1, v * frequency + 3.3, seed, 2) - 0.5
    dv = fbm(u * frequency + 1.7, v * frequency + 9.2, seed + 7, 2) - 0.5
    return u + amplitude * du, v + amplitude * dv


def soft_disc(r, inner, outer):
    """1 inside `inner`, smoothly 0 at `outer` (radii in unit coordinates); keeps the image border clear."""
    return 1.0 - smoothstep(inner, outer, r)


def normalised(size, raw, peak_alpha):
    """Scales the alpha channel of `raw` (list of (lum, alpha), row-major) so that its maximum is `peak_alpha`."""
    peak = max(a for _, a in raw)
    scale = peak_alpha / peak if peak > 0.0 else 0.0
    return build(size, size, lambda x, y: (raw[y * size + x][0], raw[y * size + x][1] * scale))


def smoke_puff_b():
    """Wispy smoke: low density, ragged lobes, warped detail with holes, max alpha 0.7."""
    size = 64
    raw = []
    for y in range(size):
        for x in range(size):
            u, v = unit_coords(x, y, size, size)
            wu, wv = warp(u, v, 41, 0.5, 1.6)
            r = math.hypot(u, v)
            lobes = fbm(wu * 2.6 + 15.0, wv * 2.6 + 15.0, 42, 3)
            detail = fbm(wu * 6.0 + 40.0, wv * 6.0 + 40.0, 43, 3)
            ragged = r + (lobes - 0.5) * 0.75
            radial = 1.0 - smoothstep(0.0, 0.95, ragged)
            wisp = 0.30 + 0.70 * smoothstep(0.32, 0.72, detail)
            alpha = (radial ** 1.3) * wisp * soft_disc(r, 0.62, 1.0) * edge_window(x, y, size, size, 3)
            lum = 0.80 + 0.20 * (0.5 * lobes + 0.5 * detail)
            raw.append((lum, alpha))
    return normalised(size, raw, 0.7)


def smoke_puff_c():
    """Dense billowy smoke: ridged billows; darker-looking lumps are encoded as lower luminance 0.55..0.85."""
    size = 64
    raw = []
    for y in range(size):
        for x in range(size):
            u, v = unit_coords(x, y, size, size)
            wu, wv = warp(u, v, 51, 0.25, 1.8)
            r = math.hypot(u, v)
            lobes = fbm(wu * 2.2 + 25.0, wv * 2.2 + 25.0, 52, 3)
            billow = abs(2.0 * fbm(wu * 5.0 + 60.0, wv * 5.0 + 60.0, 53, 3) - 1.0)
            fine = fbm(u * 8.0 + 80.0, v * 8.0 + 80.0, 54, 2)
            ragged = r + (lobes - 0.5) * 0.40
            dense = 1.0 - smoothstep(0.30, 0.92, ragged)
            alpha = min(1.0, dense * 1.15) * (0.82 + 0.18 * fine) * soft_disc(r, 0.70, 1.0)
            alpha *= edge_window(x, y, size, size, 3)
            light = 0.5 * (1.0 - v) * 0.5 + 0.5 * (1.0 - u) * 0.25
            lump = clamp01(0.85 * smoothstep(0.08, 0.55, billow) + 0.30 * light)
            lum = 0.55 + 0.30 * lump
            raw.append((lum, alpha))
    return normalised(size, raw, 0.95)


def fire_puff_b():
    """Turbulent fireball puff: hot near-white core, warped ragged body, thin fringe."""
    size = 64
    raw = []
    for y in range(size):
        for x in range(size):
            u, v = unit_coords(x, y, size, size)
            wu, wv = warp(u, v, 61, 0.85, 2.2)
            r = math.hypot(u, v)
            turbulence = fbm(wu * 3.4 + 70.0, wv * 3.4 + 70.0, 62, 4)
            ragged = r + (turbulence - 0.5) * 0.60
            body = 1.0 - smoothstep(0.05, 0.85, ragged)
            core = 1.0 - smoothstep(0.0, 0.30, ragged)
            alpha = min(1.0, 0.85 * body * (0.70 + 0.30 * turbulence) + 0.45 * core)
            alpha *= soft_disc(r, 0.55, 0.98) * edge_window(x, y, size, size, 3)
            lum = 0.80 + 0.20 * core
            raw.append((lum, alpha))
    return normalised(size, raw, 1.0)


def streak(core_half, halo_half, halo_alpha, tail_floor, tail_fade, power):
    """16x64 vertical streak. HEAD = bottom of the image (rows near y = 63), TAIL = top.

    Across: a hot core (alpha 1 for |u| <= core_half) plus a softer halo (alpha >= halo_alpha for
    |u| <= halo_half), both in unit coordinates where |u| = 1 is the image edge. Along: `tail_floor` at the
    tail rising to 1 at the head (power curve), the last `tail_fade` of the length fading to nothing, and a
    soft rounded head. The outermost pixel ring is transparent.
    """
    width = 16
    height = 64
    head = 0.90

    def pixel(x, y):
        u, _ = unit_coords(x, y, width, height)
        a = abs(u)
        s = (y + 0.5) / height
        core = 1.0 - smoothstep(core_half - 0.07, core_half + 0.07, a)
        halo = (halo_alpha + 0.10) * (1.0 - smoothstep(halo_half - 0.15, 1.0, a))
        across = min(1.0, core + halo)
        if s <= head:
            along = tail_floor + (1.0 - tail_floor) * (s / head) ** power
            if tail_fade > 0.0:
                along *= smoothstep(0.0, tail_fade, s)
        else:
            along = 1.0 - smoothstep(head, 1.0, s)
        alpha = across * along * edge_window(x, y, width, height, 2)
        lum = 0.88 + 0.12 * core * along
        return lum, alpha

    return build(width, height, pixel)


def spark():
    """Slow ember streak: bright head, halo >= 0.3 alpha across 60 percent of the width, tail fades to nothing."""
    return streak(core_half=0.22, halo_half=0.60, halo_alpha=0.32, tail_floor=0.0, tail_fade=0.0, power=1.4)


def shrapnel():
    """Very visible streak: hot core 35 percent wide (alpha >= 0.9), halo >= 0.35 across 70 percent of the
    width, tail fading to about 0.25 at the top and only vanishing in the last few percent of the length."""
    return streak(core_half=0.35, halo_half=0.70, halo_alpha=0.38, tail_floor=0.25, tail_fade=0.08, power=0.9)


TEXTURES = {
    "fire_puff": (fire_puff, (64, 64)),
    "fire_puff_b": (fire_puff_b, (64, 64)),
    "smoke_puff": (smoke_puff, (64, 64)),
    "smoke_puff_b": (smoke_puff_b, (64, 64)),
    "smoke_puff_c": (smoke_puff_c, (64, 64)),
    "flash": (flash, (64, 64)),
    "spark": (spark, (16, 64)),
    "shrapnel": (shrapnel, (16, 64)),
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


def assert_streak(name, image):
    """Visibility rules of the streaks, read at a head row (y = 56) and along the centre column."""
    alpha = image.getchannel("A")
    head_row = [alpha.getpixel((x, 56)) / 255.0 for x in range(16)]
    centre = [alpha.getpixel((8, y)) / 255.0 for y in range(64)]
    if name == "shrapnel":
        assert all(a >= 0.9 for a in head_row[5:11]), name + ": core below 0.9"
        assert all(a >= 0.35 for a in head_row[2:14]), name + ": halo below 0.35"
        assert all(a >= 0.1 for a in centre[3:59]), name + ": tail below the shader discard too early"
    if name == "spark":
        assert all(a >= 0.3 for a in head_row[3:13]), name + ": halo below 0.3"


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, (factory, size) in TEXTURES.items():
        image = factory()
        assert_valid(name, image, size)
        assert_streak(name, image)
        path = OUT_DIR / (name + ".png")
        image.save(path, format="PNG", optimize=False, compress_level=9)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        print("{:<14} {:>3}x{:<3} {}".format(name + ".png", size[0], size[1], digest))


if __name__ == "__main__":
    main()
