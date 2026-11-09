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
# Volumetric smoke (domain-warped fBm billows)
# --------------------------------------------------------------------------------------------------

SMOKE_SIZE = 128
SMOKE_BORDER = 6


def smoke_volumetric(seed, radius_x, radius_y, base_freq, warp_amp, warp_freq, edge_mod,
                     wispy, tall, lum_lo, lum_hi, ao_depth):
    """128x128 volumetric smoke from domain-warped fractal noise — no discs, no rim highlights.

    Alpha: an elliptical radial envelope (solid 1.0 core, smoothstep falloff over the outer
    ~22 percent only) whose edge radius is modulated +/-edge_mod by ridged billow noise, so the
    silhouette swirls like turbulent smoke. Luminance: gentle directional light (lit upper-left
    ~0.95 to shaded lower-right ~0.55) times (lum_lo + (lum_hi - lum_lo) * detail) where detail
    is a second warped fBm, darkened further in the deep valleys of the billow noise (ambient
    occlusion). `tall` stretches the noise vertically for the plume variant."""
    size = SMOKE_SIZE
    billow_seed = seed + 101
    detail_seed = seed + 307
    warp_seed = seed + 55

    def pixel(x, y):
        if min(x, size - 1 - x, y, size - 1 - y) < SMOKE_BORDER:
            return 0.75, 0.0
        u = (x + 0.5) / size - 0.5
        v = (y + 0.5) / size - 0.5
        wu = u + warp_amp * (fbm(u * warp_freq + 3.7, v * warp_freq + 9.1, warp_seed, 2) - 0.5) * 2.0
        wv = v + warp_amp * (fbm(u * warp_freq + 5.3, v * warp_freq + 1.7, warp_seed + 13, 2) - 0.5) * 2.0
        nv = wv / 1.4 if tall else wv
        n = fbm(wu * base_freq + 11.0, nv * base_freq - 7.0, billow_seed, 5)
        billow = 1.0 - abs(2.0 * n - 1.0)
        t = math.hypot(u / radius_x, v / radius_y) / (1.0 + (billow - 0.5) * 2.0 * edge_mod)
        alpha = 1.0 - smoothstep(0.78, 1.0, t)
        if alpha <= 0.0:
            return 0.75, 0.0
        if wispy:
            veil = fbm(wu * base_freq * 2.3 + 40.0, nv * base_freq * 2.3 + 3.0, detail_seed, 3)
            alpha *= 0.95 + 0.05 * veil
        detail = fbm(wu * base_freq * 1.9 + 23.0, nv * base_freq * 1.9 - 31.0, detail_seed + 3, 4)
        detail = clamp01((detail - 0.05) / 0.75)
        light_t = clamp01((u + v) * 0.55 + 0.5)
        base = 1.05 - 0.49 * light_t
        lum = base * (lum_lo + (lum_hi - lum_lo) * detail)
        valley = clamp01((0.55 - billow) / 0.55)
        lum *= 1.0 - ao_depth * valley * valley
        return clamp01(lum), alpha

    return build(size, size, pixel)


def smoke_puff():
    """Balanced billow: medium warp, +/-22% edge modulation."""
    return smoke_volumetric(101, 0.46, 0.46, 3.4, 0.10, 2.2, 0.22, False, False, 0.62, 1.00, 0.45)


def smoke_puff_b():
    """Wispy variant: more warp, lower base frequency, +/-30% edge modulation, alpha veil."""
    return smoke_volumetric(202, 0.45, 0.45, 2.6, 0.16, 1.8, 0.30, True, False, 0.60, 1.00, 0.45)


def smoke_puff_c():
    """Densest variant: larger solid core, lowest edge modulation, strongest shading contrast."""
    return smoke_volumetric(303, 0.48, 0.48, 3.8, 0.08, 2.4, 0.12, False, False, 0.52, 1.00, 0.60)


def smoke_puff_d():
    """Tall plume variant: narrower envelope, noise and envelope stretched vertically."""
    return smoke_volumetric(404, 0.38, 0.47, 3.2, 0.10, 2.2, 0.22, False, True, 0.62, 1.00, 0.45)


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
    """Volumetric-smoke rules: opaque centre, dense inner half, shaded luminance range, no specular."""
    size = image.size[0]
    alpha = image.getchannel("A")
    lum = image.getchannel("R")
    centre = alpha.getpixel((size // 2, size // 2)) / 255.0
    assert centre >= 0.95, name + ": centre alpha {:.2f} below 0.95".format(centre)
    for cx in (0, size - 1):
        for cy in (0, size - 1):
            assert alpha.getpixel((cx, cy)) == 0, name + ": corner not transparent"
    half = size // 4
    total = 0
    alpha_sum = 0.0
    min_lum = 1.0
    max_lum = 0.0
    white = 0
    for y in range(half, size - half):
        for x in range(half, size - half):
            a = alpha.getpixel((x, y)) / 255.0
            total += 1
            alpha_sum += a
    for y in range(size):
        for x in range(size):
            a = alpha.getpixel((x, y)) / 255.0
            if a > 0.05:
                l = lum.getpixel((x, y)) / 255.0
                min_lum = min(min_lum, l)
                max_lum = max(max_lum, l)
                if l >= 0.998 and a > 0.5:
                    white += 1
    mean_alpha = alpha_sum / total
    assert mean_alpha >= 0.85, name + ": inner mean alpha {:.3f} below 0.85".format(mean_alpha)
    assert min_lum <= 0.55 and max_lum >= 0.9, (
        name + ": luminance range {:.3f}..{:.3f} outside 0.55..0.9".format(min_lum, max_lum))
    assert white <= 4, name + ": {} saturated-white pixels (specular)".format(white)


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
