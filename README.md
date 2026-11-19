# TaCZ Better Explosion

Forge 1.20.1 addon for Timeless and Classics Zero (TaCZ). It replaces the explosions TaCZ guns and projectiles make with a cover-aware damage model and a layered client effect (particles, distance-banded sound, screen shake and flash), and reworks rocket flight.

Current version (v0.3.2): cover-aware explosion damage, the full client effect with performance throttles, an underwater effect variant, TaCZ Tweaks player-damage compatibility, and a visual polish round with real recorded blast sounds for close, mid and far distances (see Credits), a dust shock ring along the ground, bigger shrapnel and denser smoke.

## What it improves over base TaCZ

Explosions:

- TaCZ damage stops at the explosion radius. This mod reaches out to `reachMultiplier` times the radius with distance falloff, and reduces damage through cover instead of treating line-of-sight as all or nothing.
- TaCZ plays one vanilla explosion effect per blast. This mod builds a layered effect per explosion type (standard, grenade, rocket, heavy) and per hit surface (floor, wall, ceiling, air), plus an underwater variant.
- TaCZ culls detonations beyond 64 blocks entirely. This mod keeps particles visible to 384 blocks by tier and shows a Far LOD flash and smoke pillar out to 512 blocks.
- Vanilla explosion sounds are replaced by real recorded blasts in close/mid/far bands, delayed by distance to simulate travel time, with indoor reverb tails in enclosed rooms or caves and near-miss shrapnel whiz sounds.
- Screen shake and a close-range white flash, both configurable.

Rockets and RPGs, where base TaCZ has the most gaps:

- Base TaCZ rockets fly at constant speed with a hardcoded 30-tick (1.5 s / 120-block) fuse, no motor acceleration and no smoke trail. This mod adds ignition at tick 3, sustainer acceleration (+3%/tick up to ~150 m/s) with gravity compensation, a dense interpolated smoke trail and nozzle flare, and a 140-tick (~7 s / 800+ block) fuse.
- Base TaCZ tracking range is 5 chunks (80 blocks), so rockets despawn from the client mid-flight; they also freeze in unloaded chunks. This mod raises tracking to 64 chunks (1024 blocks) with 1-tick sync and force-loads chunks ahead of the rocket via 40-tick TTL tickets, plus a server tick fallback.
- A 3D looping flight sound with Doppler pitch shift follows the rocket.

## How it works

TaCZ creates every explosive-round explosion through `ExplodeUtil.createExplosion`. This mod hooks that call (one mixin, server side only) and runs its own pipeline instead of TaCZ's:

1. TaCZ's own block pass works out which blocks the blast breaks. Block damage, drops and fire stay as TaCZ and its config define them.
2. Entity damage uses a different model. It reaches out to `reachMultiplier` times the explosion radius instead of stopping at the radius, falls off with distance, and is reduced by cover (a target fully behind cover still takes a `coverFloor` share of the blast). The damage source is the normal explosion source, so armour, Blast Protection and kill credit work. The shooter is hurt too, as in TaCZ. Knockback follows the same factor.
3. The blocks are destroyed.
4. A small packet tells players near the blast to play the effect.

On the client the packet drives a layered effect built from the tier's particle budget: flash (core plus halo, about 6 ticks), fireball, long-lived billowing smoke stem, a dust shock ring racing outward along the ground plus a scattered low dust cloud tinted by the ground block, debris, sparks and fast shrapnel streaks. The sound is layered too: real blast recordings in close, mid and far bands picked by distance and delayed by distance to simulate travel time (see Credits for sources and licenses). Screen shake scales with tier and proximity, and a longer white flash fires when the blast is close.

### Underwater variant

When the blast centre, or a point 0.1 block from it along any axis, is in water (TaCZ reports block impacts exactly on a block face, so the centre alone can read dry), the server sets bit 0 of the packet's `flags` byte and the client swaps in an underwater look. Damage, knockback and blocks are not affected, only the effect:

- the flash is smaller and dimmer, and there is no fireball;
- the smoke stem becomes a pale froth plume (no mushroom cap), and the ground dust becomes slow silt;
- sparks and shrapnel become bubbles (at most 48);
- the sound is lower (pitch x0.7) and softer (volume x0.8), and the screen shake is 1.3x stronger.

Budgets are throttled client-side: a global live-particle cap, a burst penalty for rapid consecutive explosions, an optional fps-based cut, distance culling (far explosions drop to flash, stem and sound; beyond the tier's range only sound), and the vanilla Particles setting is honoured.

The mod is required on both the server and the client. With `enabled = false` TaCZ's own explosion runs untouched.

## Rocket and RPG flight in detail

This mod brings TaCZ rocket and RPG flight up to SuperbWarfare-style dynamics:

- RPGs leave the launcher at initial speed, trigger an ignition burst at tick 3, and accelerate +3%/tick up to ~150 m/s during sustainer burn, with gravity compensation.
- An interpolated smoke puff trail and an engine nozzle flare follow the in-flight rocket every tick, without visual gaps.
- A 3D looping flight sound follows the projectile with a Doppler pitch shift.
- Self-destruct fuse is 140 ticks (~7 s / 800+ blocks), with impact detonation on any block or entity.
- Chunks ahead of fast rockets are force-loaded via 40-tick TTL tickets (inspired by SuperbWarfare), plus a server tick fallback, so rockets never freeze or disappear in unloaded border chunks.
- Entity tracking range is 64 chunks (1024 blocks) with 1-tick position sync and entity culling overrides.
- Detonations are not culled at distance. Between `particleRange` and `packetRange` (up to 288-512 blocks), Far LOD shows the bright flash and rising mushroom smoke pillar across the horizon.

## Explosion types and hit surface

The server classifies each explosion from the ammo that caused it (`EntityKineticBullet.getAmmoId()`) and from the
surface it hit, and sends both in the `flags` byte.

| Type | Detected from ammo id containing | Gameplay | Look |
|---|---|---|---|
| `STANDARD` | anything else | as configured | as configured |
| `GRENADE` | `40mm`, `grenade`, `m203`, `m320`, `hedp` | reach x1.35, knockback x0.8, cover matters more | small snappy fireball, light-grey short smoke, 2.4x shrapnel, more dust and debris, visual size x0.65, higher pitch |
| `ROCKET` | `rpg`, `rocket`, `missile`, `pg7`, `javelin` | damage x1.25, knockback x1.4, reach x0.9, concussion gets through cover | big fireball and sparks, thick dark smoke, little shrapnel, shake x1.3, visual size x1.3, lower pitch |
| `HEAVY` | `mortar`, `artillery`, `howitzer`, `bomb` | damage x1.2, knockback x1.6, reach x1.2 | huge long smoke, debris fountain, shake x1.8, lowest pitch |

Override or add ammo with the server config list `ammoTypes`, e.g. `["mypack:big_shell=HEAVY"]`.

The hit surface (found by probing the six neighbours of the impact point before the blocks are destroyed, using the
bullet's direction to break ties on edges) shapes the effect:

- **Floor:** smoke mushrooms upward, flat ground dust ring, ground dust cloud and debris.
- **Wall:** smoke jets out of the wall and rolls sideways, a vertical dust ring and scorch ring on the wall, dust splashing along the wall, debris spraying out; ground dust only when the blast is close to the floor, and then reduced.
- **Ceiling:** smoke is pressed against the ceiling and spreads flat along it (no buoyancy), a ring on the ceiling, dust sinking and chunks raining down.
- **Air** (entity hit or timed fuse): spherical burst, full-sphere shrapnel, ground dust only when the ground is close below.

## Config
Forge generates two files. The server file lives in each world's `serverconfig` folder, the client file in the `config` folder.

`tacz_betterexplosion-server.toml`:

| Key | Default | Range | Effect |
|---|---|---|---|
| `enabled` | true | — | Master switch; when false TaCZ explosions run untouched |
| `reachMultiplier` | 2.0 | 1.0–4.0 | Damage reach as a multiple of the explosion radius |
| `coverFloor` | 0.5 | 0.0–1.0 | Minimum damage fraction that gets through cover |
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
| `soundVolume` | 1.0 | 0.0–2.0 | Volume multiplier for explosion and rocket flight sounds |

`quality = "MEDIUM"` in `config/tacz_betterexplosion-client.toml` reloads on file save; no restart needed.

## Effect packet

The server sends one small packet per explosion: `x, y, z` (3 doubles), `radius` (float), `flags` (byte) and `seed` (int), 33 bytes in that order. The client derives the tier from `radius` and seeds all its random offsets from `seed`, so every player sees the same blast. `flags` bits:

| Bit | Meaning |
|---|---|
| 0 (value 1) | Underwater: the blast centre, or a point 0.1 block from it, is in water (implemented in 0.3.0) |
| 1-3 | `ExplosionType` id (0 standard, 1 grenade, 2 rocket, 3 heavy) |
| 4-6 | Hit surface: 0 air, else 1 + the face normal's 3D data value (1 down, 2 up, 3 north, 4 south, 5 west, 6 east) |
| 7 | Reserved (ignored by the client) |

## Tiers and budgets

Explosions are classed by radius into tiers, each with a HIGH-quality particle budget and visibility ranges:

| Tier | Radius < | Budget (HIGH) | Packet range | Particle range |
|---|---|---|---|---|
| MINI | 2 | 14 | 96 | 64 |
| SMALL | 3.5 | 34 | 192 | 128 |
| MEDIUM | 6 | 66 | 288 | 192 |
| LARGE | 10 | 108 | 384 | 256 |
| HUGE | ∞ | 180 | 512 | 384 |

## Client commands

`/betterexplosion listammo` inspects your held TaCZ gun or ammo and lists every loaded explosive ammo id with its detected `ExplosionType`, blast radius, and rocket-flight flag, so you can copy ids straight into `ammoTypes`.

`/betterexplosion testtype <STANDARD|GRENADE|ROCKET|HEAVY> <tier> [count]` spawns test blasts for one explosion type. Both test commands take the surface from the block face you aim at (floor, wall, ceiling) and use air when you aim at the sky.

`/betterexplosion test <tier> [count] [underwater]` (client command, `count` 1–50, default 1) spawns explosions at the look target through the real effect path and reports particles spawned, the median handler time and the peak live count, a dev/perf check for the budgets above. Add the optional `underwater` literal to send the explosions with the underwater flag set and preview the underwater variant (the chat line then ends with ` (underwater)`). Without it the flags are 0.

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

## Credits

Sound effects:

- `close_blast1.ogg`, `mid_blast.ogg`: "2 High Quality Explosions" by Michel Baradari (apollo-music.de), via OpenGameArt.org. License: [CC-BY 3.0](https://creativecommons.org/licenses/by/3.0/). Source: https://opengameart.org/content/2-high-quality-explosions
- `close_blast2.ogg`: "Big Explosion" (DeathFlash, Yo Frankie!) by Blender Foundation, via OpenGameArt.org. License: [CC-BY 3.0](https://creativecommons.org/licenses/by/3.0/). Source: https://opengameart.org/content/big-explosion
- `far_rumble.ogg`: "Muffled Distant Explosion" by NenadSimic, via OpenGameArt.org. License: [CC0](https://creativecommons.org/publicdomain/zero/1.0/). Source: https://opengameart.org/content/muffled-distant-explosion
- `rocket_fly.ogg`: rocket in-flight whoosh and propulsion audio (SuperbWarfare).
- `shrapnel_whiz1.ogg`, `shrapnel_whiz2.ogg`: procedurally synthesized supersonic fragment pass-by audio (MIT).

Source recordings were downmixed to mono, silence-trimmed, high-passed, EQ-shaped, soft-limited and encoded to Ogg Vorbis for in-game 3D playback.

## License

MIT. See `LICENSE`.
