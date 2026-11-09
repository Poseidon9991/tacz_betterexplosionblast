package com.poseidon.tacz_betterexplosion.explosion;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.net.Network;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Orchestrates the explosion passes in vanilla and TaCZ order: {@code explode()} (blocks computed),
 * entity damage, then {@code finalizeExplosion} (blocks destroyed, drops spawned), then the client effects
 * packet. By the time this runs the mixin has already decided to replace TaCZ's explosion, so a failing
 * pass is logged and never falls back to TaCZ's original (no double explosion).
 */
public final class BetterExplosions {

    private BetterExplosions() {
    }

    public static void detonate(ExplosionContext ctx) {
        // 1. Start event + explode(). A cancelled Start, or a failure here, ends the explosion.
        Optional<Explosion> started;
        try {
            started = BlockPass.begin(ctx);
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Block pass (begin) failed for explosion at {}", ctx.pos(), e);
            return;
        }
        if (started.isEmpty()) {
            return;
        }

        // 2. Entity damage, before the blocks are destroyed and the drops spawn.
        try {
            DamagePass.run(ctx);
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Damage pass failed for explosion at {}", ctx.pos(), e);
        }

        // 3. Destroy the blocks that explode() computed; runs even if the damage pass failed.
        try {
            BlockPass.finish(started.get());
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Block pass (finish) failed for explosion at {}", ctx.pos(), e);
        }

        // 4. Effects packet. Last, and on its own, so an effects failure can never block the gameplay above.
        try {
            Vec3 pos = ctx.pos();
            Network.sendFx(ctx.level(),
                    new ExplosionFx(pos.x, pos.y, pos.z, ctx.radius(), (byte) 0, ctx.level().random.nextInt()));
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Sending explosion fx failed for explosion at {}", ctx.pos(), e);
        }
    }
}
