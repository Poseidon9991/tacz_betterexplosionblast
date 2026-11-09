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
import random
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


# --------------------------------------------------------------------------------------------------
# Volumetric smoke (cauliflower billows)
# --------------------------------------------------------------------------------------------------

SMOKE_SIZE = 128
SMOKE_BORDER = 6


def smoke_lobes(seed, count, spread, radius_min, radius_max, tall, central_radius):
    """Cauliflower lobe layout: `count` overlapping discs of varied radius biased toward the
    centre (deterministic per seed), plus one fat central lobe so the sprite centre is opaque.
    `tall` stacks the lobes vertically for the plume variant."""
    rng = random.Random(seed)
    lobes = []
    for _ in range(count):
        angle = rng.uniform(0.0, math.tau)
        distance = spread * (rng.random() ** 1.7)
        lx = math.cos(angle) * distance
        ly = math.sin(angle) * distance
        if tall:
            lx *= 0.55
            ly *= 1.45
        lobes.append((lx, ly, rng.uniform(radius_min, radius_max), rng.uniform(0.80, 1.18)))
    lobes.append((0.0, 0.0, central_radius, 1.05))
    return lobes


def smoke_cauli(seed, count, spread, radius_min, radius_max, ragged, wispy, tall, central_radius):
    """128x128 volumetric smoke: a union of disc lobes whose alpha is ~1.0 across the inner
    ~70 percent of each lobe and falls to 0 only in the outer ~30 percent with a small noisy
    edge. Luminance is baked volumetric shading: lit upper-left rim, dark lower-right core and
    gaps between lobes, plus subtle ambient-occlusion darkening where lobes overlap."""
    size = SMOKE_SIZE
    lobes = smoke_lobes(seed, count, spread, radius_min, radius_max, tall, central_radius)
    edge_seed = seed + 977

    def pixel(x, y):
        if min(x, size - 1 - x, y, size - 1 - y) < SMOKE_BORDER:
            return 0.75, 0.0
        u, v = unit_coords(x, y, size, size)
        n = fbm(u * 5.0 + 31.0, v * 5.0 - 17.0, edge_seed, 3)
        wisp = fbm(u * 9.0 - 11.0, v * 9.0 + 47.0, edge_seed + 5, 2)
        alpha = 0.0
        cover = 0
        shade = 1.0
        for (lx, ly, lr, lshade) in lobes:
            d = math.hypot(u - lx, v - ly) / lr
            edge = 1.0 + (n - 0.5) * ragged
            a = 1.0 - smoothstep(0.70, edge, d)
            if a <= 0.0:
                continue
            cover += 1
            if a > alpha:
                alpha = a
                shade = lshade
        if alpha <= 0.0:
            return 0.75, 0.0
        if wispy:
            alpha *= 0.92 + 0.08 * wisp
        light = clamp01(0.72 - 0.55 * (u + v))
        ao = 1.0 - 0.07 * min(cover - 1, 3) - 0.30 * max(0.0, 1.0 - alpha)
        lum = clamp01(0.19 + 0.82 * light * shade * ao)
        return lum, alpha

    return build(size, size, pixel)


def smoke_puff():
    """Baseline billow: 12 lobes, medium spread and contrast."""
    return smoke_cauli(101, 13, 0.55, 0.34, 0.52, 0.30, False, False, 0.58)


def smoke_puff_b():
    """Wispy variant: fewer, smaller, ragged lobes with a noisy alpha veil (core stays >= 0.9)."""
    return smoke_cauli(202, 12, 0.56, 0.32, 0.50, 0.50, True, False, 0.55)


def smoke_puff_c():
    """Densest variant: most lobes, largest radii, tightest edge."""
    return smoke_cauli(303, 14, 0.56, 0.34, 0.54, 0.22, False, False, 0.60)


def smoke_puff_d():
    """Tall plume variant: lobes stacked slightly vertically."""
    return smoke_cauli(404, 14, 0.56, 0.32, 0.52, 0.30, False, True, 0.58)


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
    "smoke_puff": (smoke_puff, (SMOKE_SIZE, SMOKE_SIZE)),
    "smoke_puff_b": (smoke_puff_b, (SMOKE_SIZE, SMOKE_SIZE)),
    "smoke_puff_c": (smoke_puff_c, (SMOKE_SIZE, SMOKE_SIZE)),
    "smoke_puff_d": (smoke_puff_d, (SMOKE_SIZE, SMOKE_SIZE)),
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


def assert_smoke(name, image):
    """Volumetric-smoke rules: opaque centre, dense inner half, shaded luminance range."""
    size = image.size[0]
    alpha = image.getchannel("A")
    lum = image.getchannel("R")
    centre = alpha.getpixel((size // 2, size // 2)) / 255.0
    assert centre >= 0.95, name + ": centre alpha {:.2f} below 0.95".format(centre)
    half = size // 4
    total = 0
    alpha_sum = 0.0
    min_lum = 1.0
    max_lum = 0.0
    for y in range(half, size - half):
        for x in range(half, size - half):
            a = alpha.getpixel((x, y)) / 255.0
            total += 1
            alpha_sum += a
            if a > 0.05:
                l = lum.getpixel((x, y)) / 255.0
                min_lum = min(min_lum, l)
                max_lum = max(max_lum, l)
    mean_alpha = alpha_sum / total
    assert mean_alpha >= 0.85, name + ": inner mean alpha {:.3f} below 0.85".format(mean_alpha)
    assert min_lum <= 0.36 and max_lum >= 0.93, (
        name + ": luminance range {:.3f}..{:.3f} does not span 0.35..0.95".format(min_lum, max_lum))


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, (factory, size) in TEXTURES.items():
        image = factory()
        assert_valid(name, image, size)
        assert_streak(name, image)
        if name.startswith("smoke_puff"):
            assert_smoke(name, image)
        path = OUT_DIR / (name + ".png")
        image.save(path, format="PNG", optimize=False, compress_level=9)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        print("{:<14} {:>3}x{:<3} {}".format(name + ".png", size[0], size[1], digest))


if __name__ == "__main__":
    main()
