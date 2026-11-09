package com.poseidon.tacz_betterexplosion.client.fx;

import com.poseidon.tacz_betterexplosion.ModParticles;
import com.poseidon.tacz_betterexplosion.client.particle.FlashParticle;
import com.poseidon.tacz_betterexplosion.client.particle.PuffParticle;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Spawns the layered explosion particles of one {@link ExplosionFx}. One {@link RandomSource} seeded with
 * {@code fx.seed()} drives every offset so all clients see the same blast.
 *
 * <p>Ground: {@link #findGround} scans down once at spawn. When it returns {@code NaN} the DUST and DEBRIS
 * layers are skipped and SHRAPNEL uses the full sphere instead of the upper hemisphere bias.
 *
 * <p>Underwater ({@link ExplosionFx#underwater()}): no fireball; the flash is smaller and dimmer, the stem a pale
 * froth plume without a cap, the dust silt, and the SPARKS and SHRAPNEL layers become vanilla bubbles.
 *
 * <p>Spawn contract: {@code ParticleEngine.createParticle} returns the particle already added to the engine
 * (never call {@code add} on it); the providers hand out unit particles ({@code quadSize == 1}), so
 * {@code scale(s)} and {@code setLifetime(n)} afterwards set the real size and life before the first tick.
 */
public final class ExplosionVfx {

    /** Deepest block scan of {@link #findGround}. */
    private static final int GROUND_SCAN_CAP = 24;
    /** Dust tint when the ground block has no usable map colour. */
    private static final float DEFAULT_DUST_R = 0.8F;
    private static final float DEFAULT_DUST_G = 0.75F;
    private static final float DEFAULT_DUST_B = 0.65F;
    /** The map colour moves {@code DUST_DESATURATE} of the way toward this grey. */
    private static final float DUST_GREY = 0.75F;
    private static final float DUST_DESATURATE = 0.4F;
    /** Silt (underwater dust): fallback tint and the stronger pull toward grey. */
    private static final float SILT_R = 0.7F;
    private static final float SILT_G = 0.7F;
    private static final float SILT_B = 0.65F;
    private static final float SILT_DESATURATE = 0.5F;
    /** Pale blue-white tint of the underwater froth plume. */
    private static final float FROTH_R = 0.80F;
    private static final float FROTH_G = 0.92F;
    private static final float FROTH_B = 1.00F;
    /** Underwater flash shrink, and cap of the bubble count. */
    private static final float UNDERWATER_FLASH_SCALE = 0.7F;
    private static final int MAX_BUBBLES = 48;

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
     * (unused when {@code groundY} is {@code NaN}). {@code underwater} selects the underwater variant.
     */
    public static int spawn(
            ExplosionFx fx,
            ClientLevel level,
            EnumMap<FxBudget.Layer, Integer> split,
            double groundY,
            BlockState groundState,
            boolean underwater) {
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
        spawned += spawnFlash(engine, split.get(FxBudget.Layer.FLASH), rand, cx, cy, cz, r, factor, underwater);
        if (!underwater) {
            // Underwater the fireball layer is dropped, not redistributed.
            spawned += spawnFireball(engine, split.get(FxBudget.Layer.FIREBALL), rand, cx, cy, cz, r, factor);
        }
        if (underwater) {
            spawned += spawnFrothStem(engine, split.get(FxBudget.Layer.STEM), rand, cx, cy, cz, r, factor);
        } else {
            spawned += spawnStem(engine, split.get(FxBudget.Layer.STEM), rand, cx, cy, cz, r, factor, tier);
        }
        if (ground) {
            spawned += spawnDust(
                    engine, split.get(FxBudget.Layer.DUST), rand, level, cx, cz, groundY, r, factor, groundState,
                    underwater);
            spawned += spawnDebris(
                    engine, split.get(FxBudget.Layer.DEBRIS), rand, cx, cz, groundY, r, groundState);
        }
        if (underwater) {
            // The sparks and shrapnel budget becomes bubbles, never more than the two layers together.
            int bubbles = Math.min(MAX_BUBBLES, split.get(FxBudget.Layer.SPARKS) + split.get(FxBudget.Layer.SHRAPNEL));
            spawned += spawnBubbles(engine, bubbles, rand, cx, cy, cz, r);
        } else {
            spawned += spawnSparks(engine, split.get(FxBudget.Layer.SPARKS), rand, cx, cy, cz);
            spawned += spawnShrapnel(engine, split.get(FxBudget.Layer.SHRAPNEL), rand, cx, cy, cz, r, ground);
        }

        return spawned;
    }

    /**
     * Bright centre flashes (jitter {@code 0.2R} per axis, no velocity). The first is the core (scale
     * {@code 1.1R}, intensity 1, default lifetime), the second the halo (scale {@code 2R}, intensity 0.5,
     * lifetime 9); any further flash repeats the core. Underwater both are scaled by 0.7 and dimmed to
     * intensity 0.6 (core) and 0.3 (halo), with the same lifetimes.
     */
    private static int spawnFlash(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, float factor, boolean underwater) {
        float sizeFactor = underwater ? UNDERWATER_FLASH_SCALE : 1.0F;
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            boolean halo = i == 1;
            Particle p = create(
                    engine, ModParticles.FLASH.get(),
                    cx + (rand.nextDouble() - 0.5) * 0.4 * r,
                    cy + (rand.nextDouble() - 0.5) * 0.4 * r,
                    cz + (rand.nextDouble() - 0.5) * 0.4 * r,
                    0.0, 0.0, 0.0,
                    (halo ? 2.0F : 1.1F) * r * factor * sizeFactor,
                    halo ? 9 : 0);
            if (p instanceof FlashParticle flash) {
                float intensity = underwater ? (halo ? 0.3F : 0.6F) : (halo ? 0.5F : 1.0F);
                flash.setIntensity(intensity);
            }
            spawned += p == null ? 0 : 1;
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
                    r * (0.55F + rand.nextFloat() * 0.8F) * factor,
                    10 + rand.nextInt(17));
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
                scale = r * (0.7F + rand.nextFloat() * 0.6F) * factor;
            }
            spawned += spawn(
                    engine, ModParticles.SMOKE_PUFF.get(), x, y, z,
                    (rand.nextDouble() - 0.5) * 0.02, 0.02 + rand.nextDouble() * 0.04,
                    (rand.nextDouble() - 0.5) * 0.02,
                    scale, 0);
        }
        return spawned;
    }

    /**
     * Underwater stem: a pale froth plume rising from the same column as the normal stem (no mushroom cap, even
     * for LARGE and HUGE), tinted blue-white, rising faster and living 35..64 ticks.
     */
    private static int spawnFrothStem(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, float factor) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double x = cx + (rand.nextDouble() - 0.5) * 0.3 * r;
            double y = cy + rand.nextDouble() * 0.3 * r;
            double z = cz + (rand.nextDouble() - 0.5) * 0.3 * r;
            double xd = (rand.nextDouble() - 0.5) * 0.03;
            double yd = 0.05 + rand.nextDouble() * 0.06;
            double zd = (rand.nextDouble() - 0.5) * 0.03;
            float scale = r * (0.55F + rand.nextFloat() * 0.5F) * factor;
            Particle p = create(
                    engine, ModParticles.SMOKE_PUFF.get(), x, y, z, xd, yd, zd, scale, 35 + rand.nextInt(30));
            if (p instanceof PuffParticle puff) {
                puff.setTint(FROTH_R, FROTH_G, FROTH_B);
            }
            spawned += p == null ? 0 : 1;
        }
        return spawned;
    }

    /**
     * Scattered low blast cloud: smoke puffs spread uniformly over a disc of radius {@code 1.1R} around the
     * centre, creeping outward just above the ground, tinted with the ground block's map colour. Underwater it
     * is silt: tint pulled 50 percent toward grey (fallback 0.7, 0.7, 0.65), half the upward speed, lifetime
     * 25..39 ticks.
     */
    private static int spawnDust(
            ParticleEngine engine,
            int n, RandomSource rand, ClientLevel level, double cx, double cz, double groundY, float r,
            float factor, BlockState groundState, boolean underwater) {
        float[] tint = dustTint(level, BlockPos.containing(cx, groundY - 0.01, cz), groundState, underwater);
        double upScale = underwater ? 0.5 : 1.0;
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double angle = rand.nextDouble() * Mth.TWO_PI;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double dist = r * Math.sqrt(rand.nextDouble()) * 1.1 * factor;
            double outward = 0.015 + rand.nextDouble() * 0.03;
            Particle p = create(
                    engine, ModParticles.SMOKE_PUFF.get(),
                    cx + dx * dist, groundY + 0.1, cz + dz * dist,
                    dx * outward, (0.02 + rand.nextDouble() * 0.03) * upScale, dz * outward,
                    r * 0.3F * (0.8F + rand.nextFloat() * 0.5F),
                    (underwater ? 25 : 20) + rand.nextInt(15));
            if (p instanceof PuffParticle puff) {
                puff.setTint(tint[0], tint[1], tint[2]);
            }
            spawned += p == null ? 0 : 1;
        }
        return spawned;
    }

    /**
     * Dust tint: the ground block's map colour moved 40 percent toward grey (0.75), or the fallback colour
     * when the block is air or has no map colour. Underwater (silt): 50 percent toward the same grey, fallback
     * (0.7, 0.7, 0.65).
     */
    private static float[] dustTint(
            ClientLevel level, BlockPos groundPos, BlockState groundState, boolean underwater) {
        if (groundState.isAir()) {
            return defaultDustTint(underwater);
        }
        MapColor mapColor = groundState.getMapColor(level, groundPos);
        if (mapColor == MapColor.NONE) {
            return defaultDustTint(underwater);
        }
        float desaturate = underwater ? SILT_DESATURATE : DUST_DESATURATE;
        int rgb = mapColor.col;
        return new float[] {
            Mth.lerp(desaturate, ((rgb >> 16) & 0xFF) / 255.0F, DUST_GREY),
            Mth.lerp(desaturate, ((rgb >> 8) & 0xFF) / 255.0F, DUST_GREY),
            Mth.lerp(desaturate, (rgb & 0xFF) / 255.0F, DUST_GREY)
        };
    }

    private static float[] defaultDustTint(boolean underwater) {
        return underwater
                ? new float[] {SILT_R, SILT_G, SILT_B}
                : new float[] {DEFAULT_DUST_R, DEFAULT_DUST_G, DEFAULT_DUST_B};
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

    /** Slow falling embers biased upward from the centre. */
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
                    0.8F + rand.nextFloat() * 0.9F,
                    0);
        }
        return spawned;
    }

    /**
     * Fast shrapnel streaks flying out of the centre (jitter {@code 0.1R} per axis). With ground the direction
     * is biased to the upper hemisphere ({@code y = |y| * 0.8 + 0.15}, renormalised); an air burst uses the
     * full sphere. Speed {@code (0.9 + rand * 1.3) * (0.7 + 0.06R)} blocks per tick.
     */
    private static int spawnShrapnel(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, boolean ground) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double[] dir = unitDir(rand);
            if (ground) {
                dir[1] = Math.abs(dir[1]) * 0.8 + 0.15;
                double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
                dir[0] /= len;
                dir[1] /= len;
                dir[2] /= len;
            }
            double speed = (0.9 + rand.nextDouble() * 1.3) * (0.7 + 0.06 * r);
            spawned += spawn(
                    engine, ModParticles.SHRAPNEL.get(),
                    cx + (rand.nextDouble() - 0.5) * 0.2 * r,
                    cy + (rand.nextDouble() - 0.5) * 0.2 * r,
                    cz + (rand.nextDouble() - 0.5) * 0.2 * r,
                    dir[0] * speed, dir[1] * speed, dir[2] * speed,
                    0.8F + rand.nextFloat() * 0.6F,
                    0);
        }
        return spawned;
    }

    /**
     * Underwater replacement of the SPARKS and SHRAPNEL layers: vanilla bubbles (not {@code FxParticle}s, so
     * {@link FxState} does not track them) around the centre (jitter {@code 0.3R} per axis), flying off in a
     * random direction at {@code 0.08 + rand * 0.25} blocks per tick plus 0.05 up. No scale or lifetime
     * override: vanilla sizes them and removes them when they leave the water.
     */
    private static int spawnBubbles(
            ParticleEngine engine, int n, RandomSource rand, double cx, double cy, double cz, float r) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double[] dir = unitDir(rand);
            double speed = 0.08 + rand.nextDouble() * 0.25;
            Particle p = engine.createParticle(
                    ParticleTypes.BUBBLE,
                    cx + (rand.nextDouble() - 0.5) * 0.6 * r,
                    cy + (rand.nextDouble() - 0.5) * 0.6 * r,
                    cz + (rand.nextDouble() - 0.5) * 0.6 * r,
                    dir[0] * speed, dir[1] * speed + 0.05, dir[2] * speed);
            spawned += p == null ? 0 : 1;
        }
        return spawned;
    }

    /** {@link #create} counted as 1 when the particle exists, else 0 (e.g. air debris). */
    private static int spawn(
            ParticleEngine engine,
            ParticleOptions options,
            double x, double y, double z,
            double xd, double yd, double zd,
            float scale,
            int lifetime) {
        return create(engine, options, x, y, z, xd, yd, zd, scale, lifetime) == null ? 0 : 1;
    }

    /**
     * Creates one particle through the engine ({@code createParticle} already adds it), applies the scale and
     * an optional lifetime override ({@code > 0}), and returns it, or {@code null} when it does not exist.
     */
    private static Particle create(
            ParticleEngine engine,
            ParticleOptions options,
            double x, double y, double z,
            double xd, double yd, double zd,
            float scale,
            int lifetime) {
        Particle p = engine.createParticle(options, x, y, z, xd, yd, zd);
        if (p == null) {
            return null;
        }
        p.scale(scale);
        if (lifetime > 0) {
            p.setLifetime(lifetime);
        }
        return p;
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
