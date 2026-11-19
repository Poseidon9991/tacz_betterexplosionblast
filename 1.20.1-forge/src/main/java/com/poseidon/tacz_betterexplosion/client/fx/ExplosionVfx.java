package com.poseidon.tacz_betterexplosion.client.fx;

import com.poseidon.tacz_betterexplosion.ModParticles;
import com.poseidon.tacz_betterexplosion.client.particle.FlashParticle;
import com.poseidon.tacz_betterexplosion.client.particle.PuffParticle;
import com.poseidon.tacz_betterexplosion.client.particle.RingParticle;
import com.poseidon.tacz_betterexplosion.config.ClientConfig;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import com.poseidon.tacz_betterexplosion.fx.ExplosionType;
import com.poseidon.tacz_betterexplosion.fx.FxBudget;
import com.poseidon.tacz_betterexplosion.fx.Quality;
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
 * <p>Ground: {@link #findGround} scans below the blast at spawn and below each dust-ring puff at spawn.
 * When the centre scan returns {@code NaN}, DUST and DEBRIS are skipped and SHRAPNEL uses the full sphere
 * instead of the upper hemisphere bias. Particles never query the world while ticking.
 *
 * <p>Underwater ({@link ExplosionFx#underwater()}): no fireball; the flash is smaller and dimmer, the STEM
 * layer a pale froth plume, the dust silt, and the SPARKS and SHRAPNEL layers become vanilla bubbles.
 *
 * <p>Spawn contract: {@code ParticleEngine.createParticle} returns the particle already added to the engine
 * (never call {@code add} on it); the providers hand out unit particles ({@code quadSize == 1}), so
 * {@code scale(s)} and {@code setLifetime(n)} afterwards set the real size and life before the first tick.
 */
public final class ExplosionVfx {

    /** Deepest block scan of {@link #findGround}. */
    private static final int GROUND_SCAN_CAP = 24;
    /** Dust tint when the ground block has no usable map colour (earthy tan/sand dust). */
    private static final float DEFAULT_DUST_R = 0.78F;
    private static final float DEFAULT_DUST_G = 0.70F;
    private static final float DEFAULT_DUST_B = 0.56F;
    /** The ground block's map colour moves {@code DUST_DESATURATE} of the way toward warm earthy dust. */
    private static final float DUST_TAN_R = 0.76F;
    private static final float DUST_TAN_G = 0.68F;
    private static final float DUST_TAN_B = 0.55F;
    private static final float DUST_DESATURATE = 0.52F;
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
     *
     * <p>The blast is shaped by two things the server puts in {@code fx.flags}: the {@link ExplosionType} (counts of
     * each layer, smoke size and life, tint) and the hit surface. The surface gives the blast its axis {@code a}:
     * a floor hit rises up, a wall hit blooms out of the wall (smoke jets along the wall normal and a vertical dust
     * ring and a splash of dust spreads along the wall), a ceiling hit rolls flat along the ceiling and rains
     * debris, an air burst is a plain sphere. Ground dust, ring and debris only appear when the ground is the
     * surface or is close below the blast. {@code groundY} is {@link #findGround}'s result and {@code groundState}
     * the block under it; {@code surfaceState} is the block that was hit (air when it is gone or none).
     * {@code underwater} selects the underwater variant, which ignores the surface. Layers {@code quality} blocks
     * are dropped to zero, not redistributed.
     */
    public static int spawn(
            ExplosionFx fx,
            ClientLevel level,
            EnumMap<FxBudget.Layer, Integer> split,
            double groundY,
            BlockState groundState,
            BlockState surfaceState,
            boolean underwater,
            Quality quality) {
        RandomSource rand = RandomSource.create(fx.seed());
        float factor = 0.8F + rand.nextFloat() * 0.4F;
        ExplosionTier tier = ExplosionTier.fromRadius(fx.radius());
        ExplosionType.Look look = fx.type().look;
        float r = fx.radius();
        double cx = fx.x();
        double cy = fx.y();
        double cz = fx.z();
        boolean ground = !Double.isNaN(groundY);
        ParticleEngine engine = Minecraft.getInstance().particleEngine;

        Direction normal = surfaceDirection(fx.surface());
        boolean wall = normal != null && normal.getAxis().isHorizontal();
        boolean ceiling = normal == Direction.DOWN;
        boolean floor = normal == Direction.UP;
        Surface kind = floor ? Surface.FLOOR : wall ? Surface.WALL : ceiling ? Surface.CEILING : Surface.AIR;
        double[][] frame = frame(normal);
        double[] a = frame[0];

        // Ground effects (dust ring and cloud, ground debris): when the ground is the surface or close below.
        boolean groundFx = ground && !ceiling
                && (floor || cy - groundY <= (wall ? 0.6 : 0.9) * r);
        double groundMul = wall ? 0.5 : 1.0;

        // Quality-blocked layers drop to zero so their spawn loops are skipped; the budget is not
        // redistributed. The type multiplies each remaining layer.
        EnumMap<FxBudget.Layer, Integer> counts = new EnumMap<>(split);
        for (FxBudget.Layer layer : FxBudget.Layer.values()) {
            if (!quality.allows(layer)) {
                counts.put(layer, 0);
            } else {
                counts.put(layer, (int) Math.round(counts.get(layer) * look.layer(layer)));
            }
        }

        // The flash, fireball and smoke start slightly out of the surface so they do not sit inside the block.
        double lift = switch (kind) {
            case FLOOR -> 0.15 * r;
            case WALL, CEILING -> 0.25 * r;
            case AIR -> 0.0;
        };
        double ox = cx + a[0] * lift;
        double oy = cy + a[1] * lift;
        double oz = cz + a[2] * lift;

        int spawned = 0;
        spawned += spawnFlash(engine, counts.get(FxBudget.Layer.FLASH), rand, ox, oy, oz, r, factor, underwater);
        if (!underwater) {
            // Underwater the fireball layer is dropped, not redistributed.
            spawned += spawnFireball(engine, counts.get(FxBudget.Layer.FIREBALL), rand, ox, oy, oz, r, factor,
                    kind == Surface.AIR ? null : a, frame);
        }
        if (underwater) {
            spawned += spawnFrothStem(engine, counts.get(FxBudget.Layer.STEM), rand, cx, cy, cz, r, factor);
        } else {
            spawned += spawnStem(engine, counts.get(FxBudget.Layer.STEM), rand, ox, oy, oz, r, factor, tier, kind,
                    frame, look);
        }

        if (underwater) {
            if (ground) {
                spawned += spawnDust(engine, counts.get(FxBudget.Layer.DUST), rand, level, cx, cz, groundY, r, factor,
                        groundState, true, 1.0);
                spawned += spawnDebris(engine, counts.get(FxBudget.Layer.DEBRIS), rand, level, UP_FRAME,
                        cx, groundY, cz, 0.25, r, factor, groundState);
            }
        } else {
            if (groundFx) {
                int dust = counts.get(FxBudget.Layer.DUST);
                // Air blasts split the dust budget into a ground shock ring and the scattered cloud.
                int ringCount = FxBudget.ringDustCount(dust);
                int cloudShare = Math.max(dust / 2 + 2, dust - ringCount);
                spawned += spawnDust(engine, cloudShare, rand, level, cx, cz, groundY, r, factor, groundState,
                        false, groundMul);
                if (quality.allows(FxBudget.Layer.DUST)) {
                    // Flat expanding dust shock ring + inner glowing ember scorch ring on the ground
                    float[] tint = dustTint(level, BlockPos.containing(cx, groundY - 0.01, cz), groundState, false);
                    spawned += spawnRing(engine, cx, groundY, cz, Direction.UP, 1.5F * r * factor, tint);
                    spawned += spawnScorch(engine, cx, groundY + 0.01, cz, Direction.UP, 0.85F * r * factor);
                }
                if (ringCount > 0) {
                    spawned += spawnDustRing(engine, ringCount, rand, level, cx, cz, groundY, r, factor, groundState,
                            groundMul, 1.0);
                }
                spawned += spawnDebris(engine, (int) Math.round(counts.get(FxBudget.Layer.DEBRIS) * groundMul), rand,
                        level, UP_FRAME, cx, groundY, cz, 0.25, r, factor, groundState);
            }
            if (wall || ceiling) {
                // Splash on the hit surface itself: dust ring, drifting dust and debris along the normal.
                BlockPos hit = BlockPos.containing(cx - a[0] * 0.1, cy - a[1] * 0.1, cz - a[2] * 0.1);
                BlockState state = surfaceState.isAir() ? groundState : surfaceState;
                float[] tint = dustTint(level, hit, state, false);
                if (quality.allows(FxBudget.Layer.DUST) && counts.get(FxBudget.Layer.DUST) > 0) {
                    spawned += spawnRing(engine, cx, cy, cz, normal, 1.3F * r * factor, tint);
                    spawned += spawnScorch(engine, cx, cy, cz, normal, 0.8F * r * factor);
                    spawned += spawnSurfaceDust(engine, counts.get(FxBudget.Layer.DUST), rand, cx, cy, cz, frame, r,
                            factor, tint, ceiling);
                }
                spawned += spawnDebris(engine, counts.get(FxBudget.Layer.DEBRIS), rand, level, frame, cx, cy, cz,
                        ceiling ? 0.1 : 0.15, r, factor, state);
            }
        }
        if (underwater) {
            // The sparks and shrapnel budget becomes bubbles, never more than the two layers together.
            int bubbles = Math.min(MAX_BUBBLES, counts.get(FxBudget.Layer.SPARKS) + counts.get(FxBudget.Layer.SHRAPNEL));
            spawned += spawnBubbles(engine, bubbles, rand, cx, cy, cz, r);
        } else {
            // Sparks and shrapnel fly out of the surface (hemisphere around the normal); an air burst with no
            // ground nearby is a full sphere, with ground near it is biased up.
            double[] burstAxis = kind == Surface.AIR ? (groundFx ? UP_AXIS : null) : a;
            spawned += spawnSparks(engine, counts.get(FxBudget.Layer.SPARKS), rand, ox, oy, oz,
                    burstAxis == null ? UP_AXIS : burstAxis);
            spawned += spawnShrapnel(engine, counts.get(FxBudget.Layer.SHRAPNEL), rand, ox, oy, oz, r, burstAxis);
        }

        return spawned;
    }

    /** Where the blast hit; picks the shape of the smoke and which ground effects play. */
    private enum Surface {
        FLOOR,
        WALL,
        CEILING,
        AIR
    }

    private static final double[] UP_AXIS = {0.0, 1.0, 0.0};
    private static final double[][] UP_FRAME = frame(Direction.UP);

    /** The face normal of {@code ExplosionFx#surface()} (0 is air, so null). */
    public static Direction surfaceDirection(int surface) {
        return surface <= 0 || surface > 6 ? null : Direction.from3DDataValue(surface - 1);
    }

    /**
     * Orthonormal frame {axis, tangent1, tangent2} of a surface normal; {@code null} (air) gives straight up.
     * For a floor or ceiling both tangents are horizontal; for a wall tangent1 is up and tangent2 horizontal.
     */
    private static double[][] frame(Direction normal) {
        double[] a = normal == null ? UP_AXIS : new double[] {normal.getStepX(), normal.getStepY(), normal.getStepZ()};
        double[] ref = Math.abs(a[1]) < 0.9 ? new double[] {0.0, 1.0, 0.0} : new double[] {1.0, 0.0, 0.0};
        double[] t1 = normalize(cross(a, ref));
        double[] t2 = cross(a, t1);
        return new double[][] {a, t1, t2};
    }

    private static double[] cross(double[] u, double[] v) {
        return new double[] {
            u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
    }

    private static double[] normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return len < 1.0E-9 ? new double[] {0.0, 1.0, 0.0} : new double[] {v[0] / len, v[1] / len, v[2] / len};
    }

    /** {@code dir} with its component against the axis mirrored, so it points into the axis hemisphere. */
    private static double[] reflectInto(double[] dir, double[] axis) {
        double dot = dir[0] * axis[0] + dir[1] * axis[1] + dir[2] * axis[2];
        if (dot >= 0.0) {
            return dir;
        }
        return new double[] {
            dir[0] - 2.0 * dot * axis[0], dir[1] - 2.0 * dot * axis[1], dir[2] - 2.0 * dot * axis[2]};
    }

    /** Random unit vector perpendicular to the frame axis. */
    private static double[] tangentDir(RandomSource rand, double[][] frame) {
        double angle = rand.nextDouble() * Mth.TWO_PI;
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[] {
            frame[1][0] * c + frame[2][0] * s, frame[1][1] * c + frame[2][1] * s, frame[1][2] * c + frame[2][2] * s};
    }

    /** Flat expanding dust ring lying on a surface with this outward normal. */
    private static int spawnRing(
            ParticleEngine engine, double x, double y, double z, Direction normal, float size, float[] tint) {
        Particle ringP = create(engine, ModParticles.RING.get(), x, y, z, 0.0, 0.0, 0.0, size, 0);
        if (ringP instanceof RingParticle ringParticle) {
            ringParticle.setNormal(normal);
            ringParticle.setTint(tint[0], tint[1], tint[2]);
        }
        return ringP == null ? 0 : 1;
    }

    /** Glowing ember scorch ring right under the blast on a surface. */
    private static int spawnScorch(
            ParticleEngine engine, double x, double y, double z, Direction normal, float size) {
        Particle scorchP = create(engine, ModParticles.RING.get(), x, y, z, 0.0, 0.0, 0.0, size, 0);
        if (scorchP instanceof RingParticle scorchRing) {
            scorchRing.setNormal(normal);
            scorchRing.setEmberScorch();
        }
        return scorchP == null ? 0 : 1;
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

    /**
     * Fireball puffs drifting outward from a shell around the centre; against a surface the directions are
     * mirrored into the hemisphere in front of it, so the fireball blooms out of the wall, floor or ceiling.
     */
    private static int spawnFireball(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, float factor,
            double[] axis, double[][] frame) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double[] dir = unitDir(rand);
            if (axis != null) {
                dir = reflectInto(dir, axis);
            }
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
     * Blast smoke as a violent burst, a column and a rolling cap, all in the frame of the hit surface (see
     * {@link #spawn}): off a floor the column and cap rise (mushroom); off a wall they jet out along the wall
     * normal and roll sideways while the buoyancy lifts them; under a ceiling they are pressed down and spread
     * flat along it with no buoyancy; an air burst is a mostly spherical puff. {@code look} scales size and
     * life and can tint the smoke (e.g. the light grey of a grenade). Velocities scale with the radius;
     * {@code PuffParticle} brakes the burst hard, then lets buoyancy and a slow swirl churn the cloud.
     */
    private static int spawnStem(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, float factor,
            ExplosionTier tier, Surface kind, double[][] frame, ExplosionType.Look look) {
        if (n <= 0) {
            return 0;
        }
        int baseTotal = tier == ExplosionTier.MINI ? n : n + Math.max(6, (int) Math.round(n * 0.65));
        int total = (int) Math.round(baseTotal * Math.max(0.0, ClientConfig.blastSmokeMultiplier()));
        if (total <= 0) {
            return 0;
        }
        double[] a = frame[0];
        float vScale = 0.5F + 0.2F * r;
        float sizeF = (float) (factor * look.smokeSize());
        boolean air = kind == Surface.AIR;
        boolean cap = tier != ExplosionTier.MINI && total >= 6;
        double burstShare = air ? 0.65 : 0.40;
        double columnShare = air ? 0.85 : 0.75;
        int burstEnd = Math.max(1, (int) Math.round(total * burstShare));
        int columnEnd = cap ? Math.max(burstEnd, (int) Math.round(total * columnShare)) : total;
        double columnPush = switch (kind) {
            case FLOOR -> 1.0;
            case WALL -> 0.8;
            case CEILING -> 0.4;
            case AIR -> 0.5;
        };
        double capSpread = switch (kind) {
            case FLOOR -> 1.0;
            case WALL -> 1.3;
            case CEILING -> 2.2;
            case AIR -> 1.0;
        };
        float buoyancy = switch (kind) {
            case FLOOR, AIR -> 1.0F;
            case WALL -> 0.4F;
            case CEILING -> 0.0F;
        };
        int spawned = 0;
        for (int i = 0; i < total; i++) {
            double[] t = tangentDir(rand, frame);
            double x, y, z, xd, yd, zd;
            float scale;
            if (i < burstEnd) {
                double[] dir = unitDir(rand);
                if (!air) {
                    dir = reflectInto(dir, a);
                }
                double dist = r * 0.18 * rand.nextDouble();
                double speed = (0.07 + rand.nextDouble() * 0.10) * vScale;
                double along = air ? 0.0 : 0.1 * r;
                x = cx + dir[0] * dist + a[0] * along;
                y = cy + dir[1] * dist + a[1] * along;
                z = cz + dir[2] * dist + a[2] * along;
                double push = air ? 0.0 : 0.03 * vScale;
                xd = dir[0] * speed + a[0] * push;
                yd = dir[1] * speed * (air ? 1.0 : 0.8) + a[1] * push + (air ? 0.02 * vScale : 0.0);
                zd = dir[2] * speed + a[2] * push;
                scale = (float) (r * (0.40 + rand.nextDouble() * 0.28) * sizeF);
            } else if (i < columnEnd) {
                double dist = r * 0.12 * Math.sqrt(rand.nextDouble());
                double along = r * 0.10 * rand.nextDouble();
                x = cx + t[0] * dist + a[0] * along;
                y = cy + t[1] * dist + a[1] * along;
                z = cz + t[2] * dist + a[2] * along;
                double push = (0.07 + rand.nextDouble() * 0.08) * vScale * columnPush;
                double side = 0.012 * vScale;
                xd = t[0] * side + a[0] * push;
                yd = t[1] * side + a[1] * push;
                zd = t[2] * side + a[2] * push;
                scale = (float) (r * (0.36 + rand.nextDouble() * 0.26) * sizeF);
            } else {
                double dist = r * 0.10 * Math.sqrt(rand.nextDouble());
                double along = r * 0.15 * rand.nextDouble();
                x = cx + t[0] * dist + a[0] * along;
                y = cy + t[1] * dist + a[1] * along;
                z = cz + t[2] * dist + a[2] * along;
                double out = (0.035 + rand.nextDouble() * 0.040) * vScale * capSpread;
                double push = (0.13 + rand.nextDouble() * 0.07) * vScale * columnPush;
                xd = t[0] * out + a[0] * push;
                yd = t[1] * out + a[1] * push;
                zd = t[2] * out + a[2] * push;
                scale = (float) (r * (0.40 + rand.nextDouble() * 0.28) * sizeF);
            }
            int life = (int) Math.round((42 + rand.nextInt(32)) * look.smokeLife());
            Particle p = create(engine, ModParticles.SMOKE_PUFF.get(), x, y, z, xd, yd, zd, scale, life);
            if (p instanceof PuffParticle puff) {
                puff.setBuoyancyScale(buoyancy);
                if (look.tint() != null) {
                    float[] tint = look.tint();
                    puff.setTint(tint[0], tint[1], tint[2]);
                }
            }
            spawned += p == null ? 0 : 1;
        }
        return spawned;
    }

    /**
     * Underwater stem: a pale froth plume rising in a tight column for every tier, tinted blue-white,
     * rising faster and living 35..64 ticks.
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
     * Thick flat ground dust blanket: 2-tier carpet of small & medium dusty puffs spread over a disc of
     * radius {@code 1.85R} around the centre, hugging the floor at local ground height and lingering much
     * longer (lifetime 80..125 ticks) than the rising blast smoke. Tinted with a warm dusty blend of the
     * ground block's map colour. Underwater it is silt: tint pulled 50 percent toward grey (fallback 0.7,
     * 0.7, 0.65), half the upward speed, lifetime 35..54 ticks.
     */
    private static int spawnDust(
            ParticleEngine engine,
            int n, RandomSource rand, ClientLevel level, double cx, double cz, double groundY, float r,
            float factor, BlockState groundState, boolean underwater, double mul) {
        if (n <= 0) {
            return 0;
        }
        float[] tint = dustTint(level, BlockPos.containing(cx, groundY - 0.01, cz), groundState, underwater);
        int baseCount = underwater ? n : Math.max(20, n * 4 + 12);
        int count = underwater
                ? baseCount
                : (int) Math.round(baseCount * Math.max(0.0, ClientConfig.groundSmokeMultiplier()) * mul);
        if (count <= 0) {
            return 0;
        }
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = rand.nextDouble() * Mth.TWO_PI;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            boolean outer = !underwater && i >= count * 0.45;
            double dist = outer
                    ? r * (0.45 + Math.sqrt(rand.nextDouble()) * 1.40) * factor
                    : r * Math.sqrt(rand.nextDouble()) * 0.75 * factor;
            double px = cx + dx * dist;
            double pz = cz + dz * dist;
            double py = groundY;
            if (!underwater) {
                double localGround = findGround(level, px, groundY + 1.0, pz, 2.0F);
                if (Double.isNaN(localGround)) {
                    continue;
                }
                py = localGround;
            }
            double outward = (outer ? (0.025 + rand.nextDouble() * 0.045) : (0.010 + rand.nextDouble() * 0.022))
                    * (0.65 + 0.12 * r);
            float size = underwater
                    ? r * 0.25F * (0.8F + rand.nextFloat() * 0.45F) * factor
                    : (outer
                            ? r * (0.13F + rand.nextFloat() * 0.09F) * factor
                            : r * (0.17F + rand.nextFloat() * 0.11F) * factor);
            Particle p = create(
                    engine, ModParticles.SMOKE_PUFF.get(),
                    px, py + 0.04 + rand.nextDouble() * 0.06, pz,
                    dx * outward, underwater ? (0.01 + rand.nextDouble() * 0.015) : 0.002, dz * outward,
                    size,
                    (underwater ? 35 : 80) + rand.nextInt(underwater ? 20 : 46));
            if (p instanceof PuffParticle puff) {
                puff.setTint(tint[0], tint[1], tint[2]);
                if (!underwater) {
                    puff.setGroundDust();
                }
            }
            spawned += p == null ? 0 : 1;
        }
        return spawned;
    }

    /**
     * Two-wave ground dust shock ring (air blasts only): a fast outer shockwave ring plus a denser inner
     * rolling dust wave racing outward along the floor and lingering (lifetime 68..105 ticks). Each puff sits
     * on the local ground surface found by a short scan at its own position — where the ground falls away the
     * puff is skipped rather than left floating — and runs in {@code PuffParticle}'s ground-dust mode.
     */
    private static int spawnDustRing(
            ParticleEngine engine,
            int n, RandomSource rand, ClientLevel level, double cx, double cz, double groundY, float r,
            float factor, BlockState groundState, double mul, double speedScale) {
        float[] tint = dustTint(level, BlockPos.containing(cx, groundY - 0.01, cz), groundState, false);
        int baseCount = Math.max(12, (int) Math.round(n * 2.2) + 8);
        int count = (int) Math.round(baseCount * Math.max(0.0, ClientConfig.groundSmokeMultiplier()) * mul);
        if (count <= 0) {
            return 0;
        }
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            boolean fastWave = (i & 1) == 0;
            double angle = Mth.TWO_PI * (i + (rand.nextDouble() - 0.5) * 0.6) / count;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double startRadius = (fastWave ? 0.34 : 0.20) * r;
            double px = cx + cos * startRadius;
            double pz = cz + sin * startRadius;
            double localGround = findGround(level, px, groundY + 1.0, pz, 2.0F);
            if (Double.isNaN(localGround)) {
                continue;
            }
            double speed = (fastWave ? 0.16 : 0.095) * r * (0.85 + rand.nextDouble() * 0.30) * speedScale;
            float scale = (fastWave
                    ? r * (0.17F + rand.nextFloat() * 0.11F)
                    : r * (0.19F + rand.nextFloat() * 0.12F)) * factor;
            Particle p = create(
                    engine, ModParticles.SMOKE_PUFF.get(),
                    px, localGround + 0.04 + rand.nextDouble() * 0.06, pz,
                    cos * speed, 0.002 + rand.nextDouble() * 0.0025, sin * speed,
                    scale,
                    68 + rand.nextInt(38));
            if (p instanceof PuffParticle puff) {
                puff.setTint(tint[0], tint[1], tint[2]);
                puff.setGroundDust();
            }
            spawned += p == null ? 0 : 1;
        }
        return spawned;
    }

    /**
     * Dust tint: the ground block's map colour moved 52 percent toward warm earthy tan-dust
     * (0.76, 0.68, 0.55), or the fallback dusty colour when the block is air or has no map colour.
     * Underwater (silt): 50 percent toward neutral silt (0.7, 0.7, 0.65).
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
        float targetR = underwater ? SILT_R : DUST_TAN_R;
        float targetG = underwater ? SILT_G : DUST_TAN_G;
        float targetB = underwater ? SILT_B : DUST_TAN_B;
        return new float[] {
            Mth.lerp(desaturate, ((rgb >> 16) & 0xFF) / 255.0F, targetR),
            Mth.lerp(desaturate, ((rgb >> 8) & 0xFF) / 255.0F, targetG),
            Mth.lerp(desaturate, (rgb & 0xFF) / 255.0F, targetB)
        };
    }

    private static float[] defaultDustTint(boolean underwater) {
        return underwater
                ? new float[] {SILT_R, SILT_G, SILT_B}
                : new float[] {DEFAULT_DUST_R, DEFAULT_DUST_G, DEFAULT_DUST_B};
    }

    /**
     * Dust splashed off a wall or ceiling: dusty puffs on the plane of the surface, flying outward along it and
     * drifting a little away from it, tinted by the hit block. Ceiling dust is released a bit lower and sinks.
     */
    private static int spawnSurfaceDust(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, double[][] frame, float r, float factor,
            float[] tint, boolean ceiling) {
        double[] a = frame[0];
        int base = Math.max(12, n * 3 + 8);
        int count = (int) Math.round(base * Math.max(0.0, ClientConfig.groundSmokeMultiplier()));
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double[] t = tangentDir(rand, frame);
            boolean outer = i >= count * 0.4;
            double dist = outer ? r * (0.35 + Math.sqrt(rand.nextDouble()) * 0.95) * factor
                    : r * Math.sqrt(rand.nextDouble()) * 0.5 * factor;
            double off = 0.08 + rand.nextDouble() * 0.12;
            double outward = (outer ? 0.030 + rand.nextDouble() * 0.040 : 0.012 + rand.nextDouble() * 0.020)
                    * (0.65 + 0.12 * r);
            double away = 0.008 + rand.nextDouble() * 0.020;
            float size = r * (outer ? 0.14F + rand.nextFloat() * 0.10F : 0.18F + rand.nextFloat() * 0.12F) * factor;
            Particle p = create(
                    engine, ModParticles.SMOKE_PUFF.get(),
                    cx + t[0] * dist + a[0] * off, cy + t[1] * dist + a[1] * off, cz + t[2] * dist + a[2] * off,
                    t[0] * outward + a[0] * away, t[1] * outward + a[1] * away + (ceiling ? -0.004 : 0.0),
                    t[2] * outward + a[2] * away,
                    size,
                    55 + rand.nextInt(40));
            if (p instanceof PuffParticle puff) {
                puff.setTint(tint[0], tint[1], tint[2]);
                puff.setFreeDustDrift();
            }
            spawned += p == null ? 0 : 1;
        }
        return spawned;
    }

    /**
     * Block chunks and kick-up dust spray thrown out of a surface: tangent speed along the surface and
     * {@code along} speed out of it (a floor throws up in high arcs and skimming fragments; a wall sprays out
     * and falls; a ceiling just drops chunks). Skipped when there are none to spawn or the state is air.
     * {@code base} is the point on the surface and {@code lift} how far off it the chunks start.
     */
    private static int spawnDebris(
            ParticleEngine engine,
            int n, RandomSource rand, ClientLevel level, double[][] frame,
            double bx, double by, double bz, double lift, float r,
            float factor, BlockState state) {
        if (n <= 0 || state.isAir()) {
            return 0;
        }
        double[] a = frame[0];
        boolean down = a[1] < -0.5;
        boolean upward = a[1] > 0.5;
        DebrisOption option = new DebrisOption(state);
        float[] tint = dustTint(level, BlockPos.containing(bx - a[0] * 0.1, by - a[1] * 0.1 - 0.01, bz - a[2] * 0.1),
                state, false);
        int baseChunks = Math.max(16, n * 6 + 12);
        int chunks = (int) Math.round(baseChunks * Math.max(0.0, ClientConfig.debrisMultiplier()));
        if (chunks <= 0) {
            return 0;
        }
        double outScale = down ? 0.25 : upward ? 1.0 : 0.8;
        int spawned = 0;
        for (int i = 0; i < chunks; i++) {
            double[] t = tangentDir(rand, frame);
            double dist = rand.nextDouble() * 0.45 * r;
            boolean highArc = (i & 1) == 0;
            double hSpeed = (highArc ? (0.18 + rand.nextDouble() * 0.35) : (0.35 + rand.nextDouble() * 0.50))
                    * (0.75 + 0.08 * r);
            double vSpeed = (highArc ? (0.38 + rand.nextDouble() * 0.45) : (0.18 + rand.nextDouble() * 0.28))
                    * (0.80 + 0.06 * r) * outScale;
            double along = lift + rand.nextDouble() * 0.25;
            double px = bx + t[0] * dist + a[0] * along;
            double py = by + t[1] * dist + a[1] * along;
            double pz = bz + t[2] * dist + a[2] * along;
            spawned += spawn(
                    engine, option,
                    px, py, pz,
                    t[0] * hSpeed + a[0] * vSpeed, t[1] * hSpeed + a[1] * vSpeed, t[2] * hSpeed + a[2] * vSpeed,
                    1.0F + rand.nextFloat() * 1.1F,
                    0);
            // Pair every third chunk with a dusty plume puff kicked up along its trajectory
            if (i % 3 == 0) {
                Particle dustP = create(
                        engine, ModParticles.SMOKE_PUFF.get(),
                        px, py, pz,
                        (t[0] * hSpeed + a[0] * vSpeed) * 0.4, (t[1] * hSpeed + a[1] * vSpeed) * 0.4,
                        (t[2] * hSpeed + a[2] * vSpeed) * 0.4,
                        r * (0.10F + rand.nextFloat() * 0.07F) * factor,
                        28 + rand.nextInt(18));
                if (dustP instanceof PuffParticle puff) {
                    puff.setTint(tint[0], tint[1], tint[2]);
                    puff.setFreeDustDrift();
                }
                spawned += dustP == null ? 0 : 1;
            }
        }
        return spawned;
    }

    /** Slow falling embers biased along {@code axis} (up for a floor or air, out of the wall for a wall). */
    private static int spawnSparks(
            ParticleEngine engine, int n, RandomSource rand, double cx, double cy, double cz, double[] axis) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double[] dir = unitDir(rand);
            dir[0] += axis[0] * 0.4;
            dir[1] += axis[1] * 0.4;
            dir[2] += axis[2] * 0.4;
            double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            if (len < 1.0E-5) {
                continue;
            }
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
     * Fast shrapnel streaks flying out of the centre (jitter {@code 0.1R} per axis). Against a surface the
     * direction is biased into the hemisphere of {@code axis} (component along it
     * {@code |c| * 0.8 + 0.15}, renormalised); with a {@code null} axis (open air) it is the full sphere. Speed
     * {@code (1.0 + rand * 1.5) * (0.7 + 0.06R)} blocks per tick.
     */
    private static int spawnShrapnel(
            ParticleEngine engine,
            int n, RandomSource rand, double cx, double cy, double cz, float r, double[] axis) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double[] dir = unitDir(rand);
            if (axis != null) {
                double dot = dir[0] * axis[0] + dir[1] * axis[1] + dir[2] * axis[2];
                double along = Math.abs(dot) * 0.8 + 0.15;
                dir[0] += axis[0] * (along - dot);
                dir[1] += axis[1] * (along - dot);
                dir[2] += axis[2] * (along - dot);
                double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
                dir[0] /= len;
                dir[1] /= len;
                dir[2] /= len;
            }
            double speed = (1.0 + rand.nextDouble() * 1.5) * (0.7 + 0.06 * r);
            spawned += spawn(
                    engine, ModParticles.SHRAPNEL.get(),
                    cx + (rand.nextDouble() - 0.5) * 0.2 * r,
                    cy + (rand.nextDouble() - 0.5) * 0.2 * r,
                    cz + (rand.nextDouble() - 0.5) * 0.2 * r,
                    dir[0] * speed, dir[1] * speed, dir[2] * speed,
                    1.0F + rand.nextFloat() * 0.9F,
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
