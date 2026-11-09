# TaCZ Better Explosion - Design

Date: 2026-11-09
Status: draft for review
Target: Minecraft 1.20.1, Forge 47.4.0, TaCZ 1.1.7-hotfix2 and 1.1.8-hotfix2

## 1. Purpose

TaCZ explosive rounds (RPG, 40mm grenades, any bullet whose gun data has an `explosion` block) use a vanilla-style
explosion with two problems:

1. **Damage is all-or-nothing.** A target one block outside the radius, or one wall corner away, takes zero damage.
2. **Visuals are vanilla.** TaCZ sends a vanilla `ClientboundExplodePacket`, so the client shows vanilla puffs and the
   vanilla explode sound.

This mod replaces both: a wider, cover-aware damage model, and a layered, performance-budgeted visual/sound/shake
system inspired by the layering used in SuperbWarfare (SBW). It is for packs where every player has the mod
(required on server and client). Non-TaCZ explosions (TNT, creepers) are untouched.

## 2. Findings this design rests on

Read from the TaCZ jars (1.1.7-hotfix2 and 1.1.8-hotfix2 have an identical signature for the hook target) and the SBW
source (LGPL-3.0 / GPL-3.0, studied only; no SBW code or assets are copied).

- Every TaCZ explosion goes through one static method:
  `com.tacz.guns.util.ExplodeUtil.createExplosion(Entity owner, Entity exploder, float damage, float radius,
  boolean knockback, boolean destroyBlock, Vec3 pos)`.
  `EntityKineticBullet` calls it from three places (`onHitEntity`, `onHitBlock`, and the delayed-fuse branch of
  `onBulletTick`) with `owner = getOwner()` and `exploder = the bullet`.
- `createExplosion` returns immediately on a non-`ServerLevel`, builds a `ProjectileExplosion` (extends vanilla
  `Explosion`), posts Forge `ExplosionEvent.Start` (cancel returns early), calls `explode()` and
  `finalizeExplosion(true)` (the flag only gates client-side particles, so our `false` is equivalent on a server),
  then sends a `ClientboundExplodePacket` to players within
  `AmmoConfig.EXPLOSIVE_AMMO_VISIBLE_DISTANCE`.
- `ProjectileExplosion.explode()` computes blocks, posts `ExplosionEvent.Detonate` with the entity list from
  `level.getEntities(exploder, AABB(centre +- 2R))` (the owner is included, only the bullet is excluded), then for each
  entity samples 15 points on the hitbox, ray-casts each to the centre, and keeps the smallest distance among clear
  samples. `ratio = d / R`; skipped when `ratio > 1`; a target with no clear sample is skipped. Damage is
  `(1 - ratio) * damage`, linear. Knockback base is `damage * (2R) / 500`, dampened by Blast Protection, applied only if
  the gunpack flag and `AmmoConfig.EXPLOSIVE_AMMO_KNOCK_BACK` are both on.
- For a direct hit, TaCZ resets the target's invulnerability time to 0 before the bullet's own `hurt`, so the
  explosion's `hurt` right after it only adds damage above the bullet's.
- SBW damage: reach 2x radius, `(1 - d/reach) * seenPercent` with a minimum cover floor, curve `(p^2 + p) / 2`, forced
  hurt that ignores invulnerability frames. SBW visuals: one small packet, client builds layered particles
  (about 290 for LARGE and 345 for HUGE, counted from its presets, 150 of them just the shock ring), random per-explosion
  scale, distance-tiered sounds, screen shake.
- Other mods in the target pack: TaCZ Tweaks wraps `Entity.hurt` inside `ProjectileExplosion.explode()` for a
  player-damage modifier (our damage pass bypasses it, so `TweaksCompat` re-applies it, see section 5); TACZ
  Attributes modifies bullet explosion fields before the hook, so we receive modified values.

## 3. Scope

In scope: damage model, replacement visuals/sound/shake, config, tests, performance budget.

Out of scope: block crater redesign (block behaviour stays TaCZ's), shrapnel damage rays, hit indicators, dynamic lights,
real sound recordings, shockwave damage delay (under one tick at TaCZ ranges), any dependency on SBW, non-TaCZ
explosions.

## 4. Architecture

```
EntityKineticBullet -> ExplodeUtil.createExplosion(owner, exploder, damage, radius, knockback, destroyBlock, pos)
                         |  Mixin HEAD, cancellable; server only; skipped when config enabled=false
                         v
                   BetterExplosions.detonate(ExplosionContext)
                     1. BlockPass.begin    Start event + TaCZ's own ProjectileExplosion.explode()
                                           (blocks computed, nothing destroyed yet)
                     2. DamagePass         our damage model on entities
                     3. BlockPass.finish   finalizeExplosion(false): blocks destroyed, drops spawned
                     4. FxPacket           one small S2C packet to nearby players
```

Package `com.poseidon.tacz_betterexplosion`:

| Unit | Responsibility |
|---|---|
| `mixin/ExplodeUtilMixin` | The only mixin. `@Mixin(value = ExplodeUtil.class, remap = false)`, `@Inject(method = "createExplosion", at = @At("HEAD"), cancellable = true)`. Does nothing on a client level or when disabled. Otherwise builds an `ExplosionContext` record `(level, owner, exploder, damage, radius, knockback, destroyBlock, pos)`, calls `BetterExplosions.detonate`, then cancels. |
| `explosion/BetterExplosions` | Orchestrates the passes in the order `BlockPass.begin`, `DamagePass`, `BlockPass.finish` (the vanilla and TaCZ order). If Start is cancelled, or `begin` fails, nothing else runs. If `DamagePass` fails, the error is logged and `finish` still runs, so the already computed blocks are still processed. |
| `explosion/BlockPass` | Creates a marker subclass of TaCZ `ProjectileExplosion`. `begin` posts `ExplosionEvent.Start` and runs `explode()` (blocks computed, nothing destroyed yet); `finish` runs `finalizeExplosion(false)` (blocks destroyed, drops and fire spawned). A Detonate listener empties the entity list for the marker class only, so TaCZ does no entity damage. Block removal, drops, fire and TaCZ config stay as today. |
| `explosion/DamageModel` | Pure maths (no Minecraft types), unit-tested. |
| `explosion/DamagePass` | Builds the entity list, posts `ExplosionEvent.Detonate` through a tiny `Explosion` subclass so claim/protection mods can still remove targets, applies `DamageModel`, applies knockback. Runs between `BlockPass.begin` and `BlockPass.finish`: the drops and XP orbs that `finish` spawns are not yet in the world, so they are never hurt, and walls the explosion is about to break still shield targets from the cover rays. |
| `net/ExplosionFxPacket`, `net/Network` | `SimpleChannel` with a strict version string. |
| `client/fx/*` | Client-only: tier mapping, particle spawning, sound scheduling, shake, flash, throttles. Loaded only through the packet handler. |
| `config/ServerConfig`, `config/ClientConfig` | Section 7. |

TaCZ's own `ClientboundExplodePacket` is never sent. Blocks still sync through normal block updates.

Error handling: the cancel decision is made before any pass runs. A failure inside a pass is logged and never falls back
to TaCZ's original, so there is never a double explosion. The client handler is wrapped so an effects bug cannot crash
the game.

## 5. Damage model

Let `D = damage`, `R = radius`, `centre = pos`.

```
reach  = R * reachMultiplier                       default 2.0
d      = distance from centre to the closest point of the target's bounding box
p_dist = 1 - d / reach                              no damage when d >= reach
seen   = Explosion.getSeenPercent(centre, target)   0..1, vanilla ray visibility
cover  = coverFloor + (1 - coverFloor) * seen       default coverFloor 0.25
p      = p_dist * cover
damage = D * damageMultiplier * (p^2 + p) / 2
```

Worked example, D = 30, R = 5, target in the open:

| Distance | TaCZ today | New |
|---|---|---|
| 2 blocks | 18 | 21.6 |
| 5 blocks | 0 | 11.3 |
| 8 blocks | 0 | 3.6 |
| 10 blocks | 0 | 0 |

Behind full cover at 2 blocks: 3.6. Centre damage with a clear view is exactly `D` (unchanged).

Rules:

- Target set: `level.getEntities(exploder, AABB(centre +- reach + 1))`, minus entities where `ignoreExplosion()` is true,
  sorted by distance, capped at `maxTargets`. The owner is included (self-damage), as in TaCZ.
- Damage source: vanilla explosion source with the owner as indirect attacker and the exploder as direct
  (`damageSources().explosion(exploder, owner)`), so armour, Blast Protection and kill credit work.
- Invulnerability: set `invulnerableTime = 0` before `hurt`, so a direct hit is bullet damage plus the full explosion.
- Knockback applies only when `knockback` (gunpack flag) and `AmmoConfig.EXPLOSIVE_AMMO_KNOCK_BACK` are both true.
  Direction is centre to target bounding-box centre. Magnitude is `D * (2R) / 500 * p' * knockbackMultiplier`, where `p'`
  is `p` after `ProtectionEnchantment.getExplosionKnockbackAfterDampener` (living entities only). As in TaCZ's
  `ProjectileExplosion.explode()`, which pushes any `Entity`, knockback applies to every entity that is not a spectator,
  not only living ones; flying creative players are skipped; players get `hurtMarked = true` for velocity sync, and a
  zero push changes nothing. Entity distance is computed once per target. A negative or NaN `damageMultiplier` or
  `knockbackMultiplier` counts as 0, and targets with a NaN distance are dropped.
- TaCZ Tweaks: applied when TaCZ Tweaks is installed (reflective soft compat). For `Player` targets only, after the
  model damage is computed and before `hurt`, `TweaksCompat.playerDamage` evaluates Tweaks' player-damage modifier
  (`Config$Modifiers$PlayerDamage.INSTANCE.toTaCZ()` through `AttachmentPropertyManager.eval`), read on every call.
  Resolution is lazy and once; any failure logs one warning and disables the compat, leaving damage unmodified.

## 6. Effects (client)

The server sends `ExplosionFxPacket { double x, y, z; float radius; byte flags; int seed }` to players within
`max(AmmoConfig.EXPLOSIVE_AMMO_VISIBLE_DISTANCE, tier packet range)`. `flags` bit 0 marks an underwater blast (the
centre is in water; implemented in 0.3.0), the other bits are reserved. The client derives the tier from `radius`,
seeds a `RandomSource` from `seed`, and applies +-20% random scale.

| Tier | Radius R | Particle budget (quality HIGH) | Packet range | Particle range |
|---|---|---|---|---|
| MINI | < 2 | 12 | 64 | 48 |
| SMALL | < 3.5 | 28 | 96 | 64 |
| MEDIUM | < 6 | 55 | 160 | 96 |
| LARGE | < 10 | 90 | 224 | 144 |
| HUGE | >= 10 | 150 | 256 | 192 |

Layers:

1. **Flash**: 1-2 additive flare particles (a core and a larger, fainter halo) for ~6 ticks; brief white screen
   flash (8 ticks) for players very close.
2. **Fireball**: additive puffs expanding and rising, white to orange to dark red.
3. **Smoke stem**: dark, billowing, slowly spinning puffs (several sprite variants) rising for 3.5-7 seconds; HUGE and
   LARGE add a mushroom cap.
4. **Ground dust and debris**: a scattered low cloud over the ground, tinted by the ground block's map colour; chunks
   sprayed out using the impact block's texture. Skipped for air bursts (no ground within 1.5R of the centre, found once at
   spawn).
5. **Sparks and embers**: small bright streaks with gravity.
6. **Shrapnel**: fast, bright streaks flying out of the centre (upper hemisphere when there is ground, full sphere for air
   bursts).

Sound: the client plays close crack, mid thump and far rumble layers, each delayed by `distance / 340 m/s`
(about 17 blocks per tick). Our own sound events are registered and mapped in `sounds.json` to vanilla explosion and
thunder sound files with pitch and volume per tier; real recordings can replace them later by adding files and editing
that JSON.

Shake: camera roll/pitch/yaw kick via `ViewportEvent.ComputeCameraAngles`, scaled by tier and distance, decaying over
about 15 ticks.

Art: vanilla particle sprites where they fit. Soft fireball/smoke (with variants), flash and streak textures are a few small
original PNGs generated by a dev-time script (`tools/gen_textures.py`, output committed). Nothing is copied from SBW.

## 7. Performance requirements

Particle cost is a first-class requirement.

Cheap by construction:

- The flash is 1-2 particles.
- Fireball and spark particles are fullbright with a constant light value (no per-particle light lookup); smoke caches
  its light once at spawn.
- No particle does world collision in its tick. Debris finds the ground once at spawn, falls with plain gravity and
  disappears on contact.
- All glow particles use one additive render type (one draw batch).
- Overdraw control: sprite size is capped; a particle fades out when the camera is within about 1.5 blocks; lifetimes are
  short except the smoke stem.

Throttles (all client-side, evaluated at spawn time, O(1)):

- Global cap: `maxLiveParticles` (default 700) across all particles from this mod; spawns are trimmed near the cap.
- Burst throttle: every additional explosion within about 10 ticks gets x0.7 budget (floor 0.25). Damage is unaffected.
- Adaptive: below 30 FPS new effects get x0.5, below 20 FPS x0.25.
- The vanilla Particles setting is honoured: Decreased x0.5, Minimal x0.15.
- LOD and culling: beyond about 64 blocks only flash, stem and sound; beyond the tier's particle range only sound;
  explosions behind the camera and more than 24 blocks away get about 40%.
- Quality presets LOW / MEDIUM / HIGH / ULTRA map to density 0.35 / 0.7 / 1.0 / 1.5.

Server cost: one AABB query per explosion, at most `maxTargets` targets (default 128, nearest first), vanilla visibility
rays only, one packet of about 40 bytes to nearby players, nothing per tick.

Acceptance (milestone 2), at default quality: a client command `/betterexplosion test <tier> [count]` prints particles
spawned, spawn time in ms and peak live count. Targets: MEDIUM spawn handler median <= 1 ms; 20 MEDIUM explosions in
1 second never exceed the live cap; no world queries inside any particle tick (code-review rule).

## 8. Configuration

Server (`tacz_betterexplosion-server.toml`): `enabled` (true), `reachMultiplier` (2.0), `coverFloor` (0.25),
`damageMultiplier` (1.0), `knockbackMultiplier` (1.0), `maxTargets` (128). Getters return their defaults while the spec
is not loaded (same convention as the sibling compat mods).

Client (`tacz_betterexplosion-client.toml`): `quality` (HIGH), `maxLiveParticles` (700), `adaptiveQuality` (true),
`screenShake` (true), `shakeStrength` (1.0), `screenFlash` (true).

## 9. Compatibility

- TaCZ: `mods.toml` range becomes `[1.1.7,)` (the versions verified here). The mixin uses `require = 1` so a future TaCZ
  change fails loudly at launch instead of silently doing nothing.
- Forge events: `ExplosionEvent.Start` is honoured (cancel means no explosion at all, as in TaCZ); Detonate is posted for
  both the block pass and the damage pass.
- TACZ Attributes: no work needed, values arrive already modified.
- TaCZ Tweaks: applied when TaCZ Tweaks is installed (reflective soft compat, no compile dependency; on any reflection
  failure it logs once and disables itself).
- Dedicated server: client classes are never loaded. A unit test (`DistSafetyTest`) scans the sources and fails if any
  `net.minecraft.client`, `com.mojang.blaze3d` or `net.minecraftforge.client` import appears outside the `client`
  package; the author also boots a real dedicated server with TaCZ and the jar at each milestone gate (the dev
  environment does not load TaCZ as a mod, so `runServer` cannot prove it).
- Registries for particle types and sound events are common code, registered on both sides, so registry sync matches.

## 10. Testing

Unit tests (JUnit 5, no Minecraft bootstrap): `DamageModel` (exactly `D` at `d = 0` with full view; strictly decreasing
with distance; 0 at and beyond reach; cover floor; multipliers), tier boundaries, packet encode/decode round trip,
config defaults.

Build gate: `gradlew build` and `gradlew test` for every milestone, plus the dedicated-server boot in section 9.

In-game checklist (run by the author): launcher at 2, 5 and 8 blocks; a near miss; behind cover; owner self-damage;
direct hit stacking; with TaCZ Tweaks installed; `/betterexplosion test` budgets from section 7.

## 11. Milestones

- **M1 (v0.1.0)**: hook, `BlockPass`, `DamageModel` and `DamagePass`, both configs, packet and a basic client effect
  (vanilla explosion particle plus vanilla explode sound through our packet, so explosions are not silent after TaCZ's
  packet is cancelled), unit tests, `mods.toml` range fix.
- **M2 (v0.2.0, implemented — pending the author's in-game gate)**: the full layered effects, sound, shake,
  throttles, `/betterexplosion test`, generated textures.
  M2 tuning round 1 (no ring, longer flash/smoke, shrapnel) in 0.2.1, pending author gate.
- **M3 (v0.3.0)**: TaCZ Tweaks player-damage compat, damage-model hardening and the underwater variant are
  implemented; LOD retuning waits for the author's `/betterexplosion test` measurements.

## 12. Risks

- Balance: the wider zone raises overall lethality. `reachMultiplier`, `damageMultiplier` and `coverFloor` are the
  controls; defaults are documented in section 5.
- Particle cost: budgets, caps, throttles and LOD in section 7, checked by the acceptance command.
- Future TaCZ refactor: pinned range plus loud failure.
- Texture generation needs Pillow; it is dev-time only and the PNG output is committed.
