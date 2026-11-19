package com.poseidon.tacz_betterexplosion.explosion;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.config.ServerConfig;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionType;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.Direction;
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
        // Type and hit surface are read first: the blocks are gone by the time the effects packet is built.
        ExplosionType type = ExplosionType.STANDARD;
        int surface = 0;
        try {
            type = classify(ctx);
            surface = surfaceCode(ctx.level(), ctx.pos(), ctx.exploder().getDeltaMovement());
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Explosion type/surface detection failed at {}", ctx.pos(), e);
        }

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
            DamagePass.run(ctx, type);
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
            byte flags = ExplosionFx.pack(underwater, type, surface);
            Network.sendFx(ctx.level(),
                    new ExplosionFx(pos.x, pos.y, pos.z, ctx.radius(), flags, ctx.level().random.nextInt()));
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Sending explosion fx failed for explosion at {}", ctx.pos(), e);
        }
    }

    private static ExplosionType classify(ExplosionContext ctx) {
        if (ctx.exploder() instanceof EntityKineticBullet bullet && bullet.getAmmoId() != null) {
            return ExplosionType.classify(bullet.getAmmoId().toString(), ServerConfig.ammoTypes());
        }
        return ExplosionType.STANDARD;
    }

    /** Distance, in blocks, of the surface probes. */
    private static final double SURFACE_PROBE = 0.05;

    /**
     * The surface the blast hit, as {@code 1 + normal.get3DDataValue()} (0 = open air). TaCZ reports the exact hit
     * point on a block face, so each of the six directions is probed a hair away from it: a solid block on one
     * side means the face normal points the other way. With several candidates (edges and corners) the face the
     * bullet was flying into wins; with no usable motion floors win over walls over ceilings.
     */
    static int surfaceCode(Level level, Vec3 pos, Vec3 motion) {
        Direction best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Direction probe : Direction.values()) {
            BlockPos p = BlockPos.containing(pos.add(
                    probe.getStepX() * SURFACE_PROBE, probe.getStepY() * SURFACE_PROBE, probe.getStepZ() * SURFACE_PROBE));
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                continue;
            }
            Direction normal = probe.getOpposite();
            double score = motion.lengthSqr() > 1.0e-4
                    ? motion.normalize().dot(new Vec3(normal.getStepX(), normal.getStepY(), normal.getStepZ()))
                    : (normal == Direction.UP ? -2.0 : normal == Direction.DOWN ? 2.0 : 0.0);
            if (score < bestScore) {
                bestScore = score;
                best = normal;
            }
        }
        return best == null ? 0 : best.get3DDataValue() + 1;
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
