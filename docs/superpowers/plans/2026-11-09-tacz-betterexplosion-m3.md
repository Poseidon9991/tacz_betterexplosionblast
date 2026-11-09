# TaCZ Better Explosion Implementation Plan (M3, v0.3.0)

**Goal:** Close the known gaps left after M2: TaCZ Tweaks player-damage compatibility, an underwater effect variant, and the hardening items deferred from earlier reviews.

**Spec:** `docs/superpowers/specs/2026-11-09-tacz-betterexplosion-design.md` sections 5, 6, 9. M1/M2 plan: `2026-11-09-tacz-betterexplosion.md`.

**Scope decisions:**
- **Included:** TaCZ Tweaks compat (task 11), server-side hardening of deferred minors (task 11), underwater variant (task 12), v0.3.0 docs/version (task 12).
- **Not included:** LOD retuning. The spec plans it from the author's `/betterexplosion test` measurements, which have not been reported yet; tuning blind would be guessing. Revisit when numbers exist.
- **Not included:** a custom particle shader (soft-edge clipping at alpha 0.1), dynamic lights, real sound recordings.

## Global Constraints
Same as the M1/M2 plan: MC 1.20.1, Forge 47.4.0, Java 17; client imports only under `P/client`; no SuperbWarfare code/assets; commits with the existing global git identity, plain messages, never push. Defaults, tier table and configs unchanged.

## Task 11: TaCZ Tweaks compat and server hardening
Files: new `P/compat/TweaksCompat.java` (+ test with fake classes), `P/explosion/DamagePass.java`, `P/explosion/DamageModel.java`, `P/explosion/Targets.java`, tests for `DamageModel`, `Targets`, `ExplosionFx`.
- `TweaksCompat.playerDamage(float)`: when mod `tacztweaks` is loaded, applies the same player-damage modifier that TaCZ Tweaks wraps around TaCZ's explosion `hurt` (`Config$Modifiers$PlayerDamage.INSTANCE.toTaCZ()` evaluated with `AttachmentPropertyManager.eval(Modifier, double)`), resolved reflectively once; any failure logs once and disables the compat (damage unchanged).
- `DamagePass` calls it for `Player` targets only (as Tweaks does).
- Hardening: negative/NaN multipliers clamp to 0 in `DamageModel`; `Targets.nearest` excludes NaN distances explicitly; knockback parity with TaCZ for non-living entities (decided from TaCZ bytecode); no `hurtMarked` for a zero knockback vector; distance computed once per target; `ExplosionFx` wire layout pinned by a test.

## Task 12: Underwater variant, v0.3.0 docs
Files: `P/fx/ExplosionFx.java` (flag constant + accessor), `P/explosion/BetterExplosions.java` (set flag), `P/client/fx/ExplosionVfx.java`, `ExplosionSounds.java`, `ClientFxHandler.java` (shake multiplier), `gradle.properties` (0.3.0), README, spec sections 6/9/11.
- Server sets `flags` bit 0 when the blast centre is in water.
- Client underwater variant: no fireball, dimmer/smaller flash, pale froth plume instead of smoke stem, silt dust, bubbles instead of sparks/shrapnel, lower/softer sound, stronger shake.

## Finish
Final whole-branch review on the finished branch, deploy `tacz_betterexplosion-0.3.0.jar` to `New zombie\mods`, report.
