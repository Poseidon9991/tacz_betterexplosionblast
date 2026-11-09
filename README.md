# TaCZ Better Explosion

Forge 1.20.1 addon for Timeless and Classics Zero (TaCZ) that improves explosions caused by TaCZ guns and projectiles.

v0.2.0: cover-aware explosion damage plus the full client effect — layered particles, distance-banded sound, screen shake and flash, all with performance throttles.

## How it works

TaCZ creates every explosive-round explosion through `ExplodeUtil.createExplosion`. This mod hooks that call (one mixin, server side only) and runs its own pipeline instead of TaCZ's:

1. TaCZ's own block pass works out which blocks the blast breaks. Block damage, drops and fire stay as TaCZ and its config define them.
2. Entity damage uses a different model. It reaches out to `reachMultiplier` times the explosion radius instead of stopping at the radius, falls off with distance, and is reduced by cover (a target fully behind cover still takes a `coverFloor` share of the blast). The damage source is the normal explosion source, so armour, Blast Protection and kill credit work. The shooter is hurt too, as in TaCZ. Knockback follows the same factor.
3. The blocks are destroyed.
4. A small packet tells players near the blast to play the effect.

On the client the packet drives a layered effect built from the tier's particle budget: flash, fireball, shock ring, smoke stem, dust, debris and sparks. The sound is layered too — close, mid and far bands picked by distance and delayed by distance to simulate travel time. Screen shake scales with tier and proximity, and a white flash fires when the blast is close.

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
| `quality` | HIGH | LOW / MEDIUM / HIGH / ULTRA | Scales the per-explosion particle budget |
| `maxLiveParticles` | 700 | 50–5000 | Cap on this mod's particles alive at once |
| `adaptiveQuality` | true | — | Cuts budgets automatically when the frame rate drops |
| `screenShake` | true | — | Camera shake for nearby explosions |
| `shakeStrength` | 1.0 | 0.0–2.0 | Scales the shake strength |
| `screenFlash` | true | — | White flash for explosions close by |

## Tiers and budgets

Explosions are classed by radius into tiers, each with a HIGH-quality particle budget and visibility ranges:

| Tier | Radius < | Budget (HIGH) | Packet range | Particle range |
|---|---|---|---|---|
| MINI | 2 | 12 | 64 | 48 |
| SMALL | 3.5 | 28 | 96 | 64 |
| MEDIUM | 6 | 55 | 160 | 96 |
| LARGE | 10 | 90 | 224 | 144 |
| HUGE | ∞ | 150 | 256 | 192 |

## Test command

`/betterexplosion test <tier> [count]` (client command, `count` 1–50, default 1) spawns explosions at the look target through the real effect path and reports particles spawned, the median handler time and the peak live count — a dev/perf check for the budgets above.

## Known gap

TaCZ Tweaks' explosion player-damage modifier does not apply to this mod's explosion damage.

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
