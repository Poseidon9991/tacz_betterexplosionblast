package com.poseidon.tacz_betterexplosion.explosion;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;

/**
 * Orchestrates the explosion passes. By the time this runs the mixin has already decided to replace
 * TaCZ's explosion, so a failing pass is logged and never falls back to TaCZ's original (no double
 * explosion).
 */
public final class BetterExplosions {

    private BetterExplosions() {
    }

    public static void detonate(ExplosionContext ctx) {
        boolean proceed = true;
        try {
            proceed = BlockPass.run(ctx);
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Block pass failed for explosion at {}", ctx.pos(), e);
        }
        if (!proceed) {
            return;
        }
        try {
            DamagePass.run(ctx);
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Damage pass failed for explosion at {}", ctx.pos(), e);
        }
    }
}
