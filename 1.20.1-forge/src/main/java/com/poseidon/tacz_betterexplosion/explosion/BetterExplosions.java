package com.poseidon.tacz_betterexplosion.explosion;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.net.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Orchestrates the explosion passes in vanilla and TaCZ order: {@code explode()} (blocks computed),
 * entity damage, then {@code finalizeExplosion} (blocks destroyed, drops spawned), then the client effects
 * packet. By the time this runs the mixin has already decided to replace TaCZ's explosion, so a failing
 * pass is logged and never falls back to TaCZ's original (no double explosion). The effects packet carries
 * {@link ExplosionFx#FLAG_UNDERWATER} when the blast centre, or a point 0.1 block from it, is in water;
 * damage and blocks are unaffected.
 */
public final class BetterExplosions {

    /** Distance, in blocks, of the extra underwater probes from the blast centre. */
    private static final double PROBE = 0.1;
    /** The six extra points sampled for water: the centre moved by {@code +-PROBE} along each axis. */
    private static final Vec3[] WATER_PROBE_OFFSETS = {
            new Vec3(PROBE, 0, 0), new Vec3(-PROBE, 0, 0),
            new Vec3(0, PROBE, 0), new Vec3(0, -PROBE, 0),
            new Vec3(0, 0, PROBE), new Vec3(0, 0, -PROBE)};

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
            boolean underwater = isUnderwater(ctx.level(), pos);
            byte flags = underwater ? ExplosionFx.FLAG_UNDERWATER : 0;
            Network.sendFx(ctx.level(),
                    new ExplosionFx(pos.x, pos.y, pos.z, ctx.radius(), flags, ctx.level().random.nextInt()));
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Sending explosion fx failed for explosion at {}", ctx.pos(), e);
        }
    }

    /**
     * Whether the blast counts as underwater: water at {@code pos} or at any of the six points
     * {@link #WATER_PROBE_OFFSETS} away from it. TaCZ passes a block impact's exact hit location, which lies on
     * a block face and floors into the solid block for bottom, west and north faces, so the centre alone can
     * read dry for a fully submerged blast. Short-circuits on the first water hit.
     */
    private static boolean isUnderwater(Level level, Vec3 pos) {
        if (isWater(level, pos)) {
            return true;
        }
        for (Vec3 offset : WATER_PROBE_OFFSETS) {
            if (isWater(level, pos.add(offset))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWater(Level level, Vec3 pos) {
        return level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
    }
}
