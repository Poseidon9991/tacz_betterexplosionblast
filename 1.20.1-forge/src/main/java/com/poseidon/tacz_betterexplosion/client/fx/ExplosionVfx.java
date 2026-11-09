package com.poseidon.tacz_betterexplosion.client.fx;

import com.poseidon.tacz_betterexplosion.ModParticles;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import com.poseidon.tacz_betterexplosion.fx.FxBudget;
import com.poseidon.tacz_betterexplosion.particle.DebrisOption;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Spawns the layered explosion particles of one {@link ExplosionFx}. One {@link RandomSource} seeded with
 * {@code fx.seed()} drives every offset so all clients see the same blast.
 *
 * <p>Ground: {@link #findGround} scans down once at spawn. When it returns {@code NaN} the DUST and DEBRIS
 * layers are skipped and RING spawns at the centre instead of on the surface.
 *
 * <p>Spawn contract: {@code ParticleEngine.createParticle} returns the particle already added to the engine
 * (never call {@code add} on it); the providers hand out unit particles ({@code quadSize == 1}), so
 * {@code scale(s)} and {@code setLifetime(n)} afterwards set the real size and life before the first tick.
 */
public final class ExplosionVfx {

    /** Deepest block scan of {@link #findGround}. */
    private static final int GROUND_SCAN_CAP = 24;

    private ExplosionVfx() {}

    /**
     * Top surface Y of the first block with a non-empty collision shape at most {@code ceil(1.5 * radius)}
     * blocks (capped at {@value #GROUND_SCAN_CAP}) below the centre, or {@link Double#NaN} when there is none.
     */
    public static double findGround(ClientLevel level, double x, double y, double z, float radius) {
        int maxDepth = Math.min(GROUND_SCAN_CAP, Mth.ceil(1.5F * radius));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int i = 0; i <= maxDepth; i++) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(Direction.Axis.Y);
            }
            pos.move(Direction.DOWN);
        }
        return Double.NaN;
    }

    /**
     * Spawns every layer per the {@code split} counts and returns the particles actually created.
     * {@code groundY} is {@link #findGround}'s result; {@code groundState} is the block at the ground position
     * (unused when {@code groundY} is {@code NaN}).
     */
    public static int spawn(
            ExplosionFx fx,
            ClientLevel level,
            EnumMap<FxBudget.Layer, Integer> split,
            double groundY,
            BlockState groundState) {
        RandomSource rand = RandomSource.create(fx.seed());
        float factor = 0.8F + rand.nextFloat() * 0.4F;
        ExplosionTier tier = ExplosionTier.fromRadius(fx.radius());
        float r = fx.radius();
        double cx = fx.x();
        double cy = fx.y();
        double cz = fx.z();
        boolean ground = !Double.isNaN(groundY);
        ParticleEngine engine = Minecraft.getInstance().particleEngine;

        int spawned = 0;
        spawned += spawnFlash(engine, split.get(FxBudget.Layer.FLASH), rand, cx, cy, cz, r, factor);
        spawned += spawnRing(engine, split.get(FxBudget.Layer.RING), cx, cy, cz, r, factor, ground, groundY);
        spawned += spawnFireball(engine, split.get(FxBudget.Layer.FIREBALL), rand, cx, cy, cz, r, factor);
        spawned += spawnStem(engine, split.get(FxBudget.Layer.STEM), rand, cx, cy, cz, r, factor, tier);
        if (ground) {
            spawned += spawnDust(engine, split.get(FxBudget.Layer.DUST), rand, cx, cz, groundY, r, factor);
            spawned += spawnDebris(
                    engine, split.get(FxBudget.Layer.DEBRIS), rand, cx, cz, groundY, r, groundState);
        }
        spawned += spawnSparks(engine, split.get(FxBudget.Layer.SPARKS), rand, cx, cy, cz);

        return spawned;
    }

    /** Bright centre flashes: jitter {@code 0.2R} per axis, fixed size, no velocity, default lifetime. */
    private static int spawnFlash(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, float factor) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            spawned += spawn(
                    engine, ModParticles.FLASH.get(),
                    cx + (rand.nextDouble() - 0.5) * 0.4 * r,
                    cy + (rand.nextDouble() - 0.5) * 0.4 * r,
                    cz + (rand.nextDouble() - 0.5) * 0.4 * r,
                    0.0, 0.0, 0.0,
                    r * 1.2F * factor,
                    0);
        }
        return spawned;
    }

    /** Ground shockwave ring, or at the centre when no ground was found. No velocity, default lifetime. */
    private static int spawnRing(
            ParticleEngine engine,
            int n, double cx, double cy, double cz, float r, float factor, boolean ground, double groundY) {
        int spawned = 0;
        double y = ground ? groundY + 0.03 : cy;
        for (int i = 0; i < n; i++) {
            spawned += spawn(
                    engine, ModParticles.RING.get(), cx, y, cz, 0.0, 0.0, 0.0, 1.5F * r * factor, 0);
        }
        return spawned;
    }

    /** Fireball puffs drifting outward from a shell around the centre. */
    private static int spawnFireball(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, float factor) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double[] dir = unitDir(rand);
            double dist = rand.nextDouble() * 0.5 * r;
            double speed = 0.05 + rand.nextDouble() * 0.15;
            spawned += spawn(
                    engine, ModParticles.FIRE_PUFF.get(),
                    cx + dir[0] * dist, cy + dir[1] * dist, cz + dir[2] * dist,
                    dir[0] * speed, dir[1] * speed + 0.02, dir[2] * speed,
                    r * (0.5F + rand.nextFloat() * 0.7F) * factor,
                    8 + rand.nextInt(13));
        }
        return spawned;
    }

    /**
     * Rising smoke column. For LARGE and HUGE explosions the top third of the puffs spawns wider as the
     * mushroom cap ({@code 0.8R} xz spread, {@code 0.5R} up, 1.4x the scale).
     */
    private static int spawnStem(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, float factor,
            ExplosionTier tier) {
        int spawned = 0;
        boolean capTier = tier == ExplosionTier.LARGE || tier == ExplosionTier.HUGE;
        int capStart = capTier ? n - Math.max(1, n / 3) : n;
        for (int i = 0; i < n; i++) {
            double x;
            double y;
            double z;
            float scale;
            if (i >= capStart) {
                x = cx + (rand.nextDouble() - 0.5) * 0.8 * r;
                y = cy + 0.5 * r;
                z = cz + (rand.nextDouble() - 0.5) * 0.8 * r;
                scale = r * (0.6F + rand.nextFloat() * 0.5F) * factor * 1.4F;
            } else {
                x = cx + (rand.nextDouble() - 0.5) * 0.3 * r;
                y = cy + rand.nextDouble() * 0.3 * r;
                z = cz + (rand.nextDouble() - 0.5) * 0.3 * r;
                scale = r * (0.6F + rand.nextFloat() * 0.5F) * factor;
            }
            spawned += spawn(
                    engine, ModParticles.SMOKE_PUFF.get(), x, y, z,
                    (rand.nextDouble() - 0.5) * 0.02, 0.02 + rand.nextDouble() * 0.04,
                    (rand.nextDouble() - 0.5) * 0.02,
                    scale, 0);
        }
        return spawned;
    }

    /** Evenly spaced dust ring creeping outward on the ground. */
    private static int spawnDust(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cz, double groundY, float r, float factor) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double angle = i * (Mth.TWO_PI / n) + (rand.nextDouble() - 0.5) * 0.4;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double ring = r * (0.7 + rand.nextDouble() * 0.5) * factor;
            spawned += spawn(
                    engine, ModParticles.SMOKE_PUFF.get(),
                    cx + dx * ring, groundY + 0.1, cz + dz * ring,
                    dx * 0.02, 0.03 + rand.nextDouble() * 0.03, dz * 0.02,
                    r * 0.25F * (0.8F + rand.nextFloat() * 0.4F),
                    15 + rand.nextInt(11));
        }
        return spawned;
    }

    /** Ground block chunks tossed up; skipped when there are none to spawn or the ground state is air. */
    private static int spawnDebris(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cz, double groundY, float r,
            BlockState groundState) {
        if (n <= 0 || groundState.isAir()) {
            return 0;
        }
        DebrisOption option = new DebrisOption(groundState);
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            spawned += spawn(
                    engine, option,
                    cx + (rand.nextDouble() - 0.5) * 0.6 * r, groundY + 0.3 + rand.nextDouble() * 0.2,
                    cz + (rand.nextDouble() - 0.5) * 0.6 * r,
                    (rand.nextDouble() - 0.5) * 0.6, 0.25 + rand.nextDouble() * 0.35,
                    (rand.nextDouble() - 0.5) * 0.6,
                    0.8F + rand.nextFloat() * 0.7F,
                    0);
        }
        return spawned;
    }

    /** Fast streaks biased upward from the centre. */
    private static int spawnSparks(
            ParticleEngine engine, int n, RandomSource rand, double cx, double cy, double cz) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double[] dir = unitDir(rand);
            dir[1] += 0.4;
            double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            double speed = (0.4 + rand.nextDouble() * 0.8) / len;
            spawned += spawn(
                    engine, ModParticles.SPARK.get(), cx, cy, cz,
                    dir[0] * speed, dir[1] * speed, dir[2] * speed,
                    0.6F + rand.nextFloat() * 0.8F,
                    0);
        }
        return spawned;
    }

    /**
     * Creates one particle through the engine ({@code createParticle} already adds it), applies the scale and
     * an optional lifetime override, and returns 1 when the particle exists, else 0 (e.g. air debris).
     */
    private static int spawn(
            ParticleEngine engine,
            ParticleOptions options,
            double x, double y, double z,
            double xd, double yd, double zd,
            float scale,
            int lifetime) {
        Particle p = engine.createParticle(options, x, y, z, xd, yd, zd);
        if (p == null) {
            return 0;
        }
        p.scale(scale);
        if (lifetime > 0) {
            p.setLifetime(lifetime);
        }
        return 1;
    }

    /** Random direction on the unit sphere; falls back to straight up for a degenerate roll. */
    private static double[] unitDir(RandomSource rand) {
        double x = rand.nextDouble() * 2.0 - 1.0;
        double y = rand.nextDouble() * 2.0 - 1.0;
        double z = rand.nextDouble() * 2.0 - 1.0;
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0E-5) {
            return new double[] {0.0, 1.0, 0.0};
        }
        return new double[] {x / len, y / len, z / len};
    }
}
