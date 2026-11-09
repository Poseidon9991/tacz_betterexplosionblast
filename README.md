# TaCZ Better Explosion

Forge 1.20.1 addon for Timeless and Classics Zero (TaCZ) that improves explosions caused by TaCZ guns and projectiles.

Early release (v0.1.0): cover-aware explosion damage and a basic client effect. The full visual and sound effects are still to come.

## How it works

TaCZ creates every explosive-round explosion through `ExplodeUtil.createExplosion`. This mod hooks that call (one mixin, server side only) and runs its own pipeline instead of TaCZ's:

1. TaCZ's own block pass works out which blocks the blast breaks. Block damage, drops and fire stay as TaCZ and its config define them.
2. Entity damage uses a different model. It reaches out to `reachMultiplier` times the explosion radius instead of stopping at the radius, falls off with distance, and is reduced by cover (a target fully behind cover still takes a `coverFloor` share of the blast). The damage source is the normal explosion source, so armour, Blast Protection and kill credit work. The shooter is hurt too, as in TaCZ. Knockback follows the same factor.
3. The blocks are destroyed.
4. A small packet tells players near the blast to play the effect.

The mod is required on both the server and the client. With `enabled = false` TaCZ's own explosion runs untouched.

## Config

Forge generates two files. The server file lives in each world's `serverconfig` folder, the client file in the `config` folder.

- `tacz_betterexplosion-server.toml`: `enabled` (true), `reachMultiplier` (2.0), `coverFloor` (0.25), `damageMultiplier` (1.0), `knockbackMultiplier` (1.0), `maxTargets` (128).
- `tacz_betterexplosion-client.toml`: `quality` (HIGH), `maxLiveParticles` (700), `adaptiveQuality` (true), `screenShake` (true), `shakeStrength` (1.0), `screenFlash` (true). The effect options take effect as the visual effects arrive in later versions; v0.1.0 plays the vanilla explosion sound and particles.

## Known gap

TaCZ Tweaks' explosion player-damage modifier does not apply to this mod's explosion damage.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft  | 1.20.1 |
| Forge      | 47.4.0+ |
| TaCZ       | 1.1.5+ (built against 1.1.8-hotfix2) |

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
