# TaCZ Better Explosion

Forge 1.20.1 addon for Timeless and Classics Zero (TaCZ) that improves explosions caused by TaCZ guns and projectiles.

v0.3.0: cover-aware explosion damage plus the full client effect — layered particles, distance-banded sound, screen shake and flash, all with performance throttles. 0.3.0 adds an underwater effect variant, TaCZ Tweaks player-damage compatibility and a round of damage-model hardening. 0.3.2 is a visual polish round plus real blast sounds: close/mid/far recorded explosions replace the vanilla ones (CREDITS.md). It includes: a dust shock ring racing outward along the ground, bigger and more numerous shrapnel streaks, and denser billowing smoke.

## How it works

TaCZ creates every explosive-round explosion through `ExplodeUtil.createExplosion`. This mod hooks that call (one mixin, server side only) and runs its own pipeline instead of TaCZ's:

1. TaCZ's own block pass works out which blocks the blast breaks. Block damage, drops and fire stay as TaCZ and its config define them.
2. Entity damage uses a different model. It reaches out to `reachMultiplier` times the explosion radius instead of stopping at the radius, falls off with distance, and is reduced by cover (a target fully behind cover still takes a `coverFloor` share of the blast). The damage source is the normal explosion source, so armour, Blast Protection and kill credit work. The shooter is hurt too, as in TaCZ. Knockback follows the same factor.
3. The blocks are destroyed.
4. A small packet tells players near the blast to play the effect.

On the client the packet drives a layered effect built from the tier's particle budget: flash (core plus halo, about 6 ticks), fireball, long-lived billowing smoke stem, a dust shock ring racing outward along the ground plus a scattered low dust cloud tinted by the ground block, debris, sparks and fast shrapnel streaks. The sound is layered too — real blast recordings in close, mid and far bands picked by distance and delayed by distance to simulate travel time (see CREDITS.md for sources and licenses). Screen shake scales with tier and proximity, and a longer white flash fires when the blast is close.

### Underwater variant

When the blast centre, or a point 0.1 block from it along any axis, is in water (TaCZ reports block impacts exactly on a block face, so the centre alone can read dry), the server sets bit 0 of the packet's `flags` byte and the client swaps in an underwater look. Damage, knockback and blocks are not affected, only the effect:

- the flash is smaller and dimmer, and there is no fireball;
- the smoke stem becomes a pale froth plume (no mushroom cap), and the ground dust becomes slow silt;
- sparks and shrapnel become bubbles (at most 48);
- the sound is lower (pitch x0.7) and softer (volume x0.8), and the screen shake is 1.3x stronger.

Budgets are throttled client-side: a global live-particle cap, a burst penalty for rapid consecutive explosions, an optional fps-based cut, distance culling (far explosions drop to flash, stem and sound; beyond the tier's range only sound), and the vanilla Particles setting is honoured.

The mod is required on both the server and the client. With `enabled = false` TaCZ's own explosion runs untouched.

## Config

Forge generates two files. The server file lives in each world's `serverconfig` folder, the client file in the `config` folder.

`tacz_betterexplosion-server.toml`:

| Key | Default | Range | Effect |
|---|---|---|---|
| `enabled` | true | — | Master switch; when false TaCZ explosions run untouched |
| `reachMultiplier` | 2.0 | 1.0–4.0 | Damage reach as a multiple of the explosion radius |
| `coverFloor` | 0.25 | 0.0–1.0 | Minimum damage fraction that gets through cover |
| `damageMultiplier` | 1.0 | 0.0–10.0 | Scales explosion damage |
| `knockbackMultiplier` | 1.0 | 0.0–10.0 | Scales explosion knockback |
| `maxTargets` | 128 | 1–512 | Maximum entities affected per explosion (nearest kept) |

`tacz_betterexplosion-client.toml`:

| Key | Default | Range | Effect |
|---|---|---|---|
| `quality` | HIGH | LOW / MEDIUM / HIGH | LOW: density 0.3, drops debris/sparks/shrapnel (keeps flash, fireball, stem, dust); MEDIUM: 0.65, drops debris/sparks (adds shrapnel); HIGH: 1.0, every layer |
| `maxLiveParticles` | 700 | 50–5000 | Cap on this mod's particles alive at once |
| `adaptiveQuality` | true | — | Cuts budgets automatically when the frame rate drops |
| `screenShake` | true | — | Camera shake for nearby explosions |
| `shakeStrength` | 1.0 | 0.0–2.0 | Scales the shake strength |
| `screenFlash` | true | — | White flash for explosions close by |

`quality = "MEDIUM"` in `config/tacz_betterexplosion-client.toml` reloads on file save — no restart needed.

## Effect packet

The server sends one small packet per explosion: `x, y, z` (3 doubles), `radius` (float), `flags` (byte) and `seed` (int), 33 bytes in that order. The client derives the tier from `radius` and seeds all its random offsets from `seed`, so every player sees the same blast. `flags` bits:

| Bit | Meaning |
|---|---|
| 0 (value 1) | Underwater: the blast centre, or a point 0.1 block from it, is in water (implemented in 0.3.0) |
| 1-7 | Reserved (ignored by the client) |

## Tiers and budgets

Explosions are classed by radius into tiers, each with a HIGH-quality particle budget and visibility ranges:

| Tier | Radius < | Budget (HIGH) | Packet range | Particle range |
|---|---|---|---|---|
| MINI | 2 | 14 | 64 | 48 |
| SMALL | 3.5 | 34 | 96 | 64 |
| MEDIUM | 6 | 66 | 160 | 96 |
| LARGE | 10 | 108 | 224 | 144 |
| HUGE | ∞ | 180 | 256 | 192 |

## Test command

`/betterexplosion test <tier> [count] [underwater]` (client command, `count` 1–50, default 1) spawns explosions at the look target through the real effect path and reports particles spawned, the median handler time and the peak live count — a dev/perf check for the budgets above. Add the optional `underwater` literal to send the explosions with the underwater flag set and preview the underwater variant (the chat line then ends with ` (underwater)`). Without it the flags are 0.

```
/betterexplosion test medium
/betterexplosion test medium 10
/betterexplosion test medium underwater
/betterexplosion test medium 10 underwater
```

## Compatibility

- **TaCZ Tweaks:** its explosion player-damage modifier is applied to the damage players take from this mod's explosions, as Tweaks does for TaCZ's own. This is a reflective soft compat: nothing happens when Tweaks is absent, and if Tweaks' internals change, the compat logs one warning and turns itself off (damage stays unmodified).
- **TACZ Attributes:** nothing needed, its modified values arrive already applied.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft  | 1.20.1 |
| Forge      | 47.4.0+ |
| TaCZ       | 1.1.7+ (built against 1.1.8-hotfix2) |

## Build

```
cd 1.20.1-forge
gradlew build
```

Output: `1.20.1-forge/build/libs/tacz_betterexplosion-<version>.jar`.

Compile-time dependency jar (not committed, see `.gitignore`) goes in `1.20.1-forge/libs/`:
`tacz-1.20.1-1.1.8-hotfix2.jar`.

## License

MIT — see `LICENSE`.
