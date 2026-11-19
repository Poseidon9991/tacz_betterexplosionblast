package com.poseidon.tacz_betterexplosion.client.fx;

import com.poseidon.tacz_betterexplosion.ModSounds;
import com.poseidon.tacz_betterexplosion.client.sound.EarMuffle;
import com.poseidon.tacz_betterexplosion.config.ClientConfig;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import com.poseidon.tacz_betterexplosion.fx.ExplosionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Distance-banded 3D explosion audio with indoor reverb tail and near-miss shrapnel whiz.
 * Uses {@link SoundInstance.Attenuation#NONE} with world-space coordinates (matching TaCZ's {@code GunSoundInstance})
 * so OpenAL preserves full 3D stereo panning while distance falloff is driven by {@link #computeGain} all the way
 * out to {@link #audibleRange} instead of OpenAL's 16-block linear cutoff.
 */
public final class ExplosionSounds {

    static final double CLOSE_BAND = 0.35;
    static final double BLEND_BAND = 0.52;
    static final double MID_BAND = 0.75;
    private static final double TICKS_PER_BLOCK = 1.0 / 17.0;
    private static final int MAX_DELAY = 100;
    private static final double CENTERED_DISTANCE = 1.5;
    private static final float UNDERWATER_PITCH = 0.75F;
    private static final float UNDERWATER_VOLUME = 0.85F;
    private static final float MIN_PITCH = 0.55F;
    private static final float MAX_PITCH = 1.80F;
    private static final float HORIZON_FLOOR = 0.10F;

    private static final int[][] HORIZ_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private ExplosionSounds() {}

    public static void play(ExplosionFx fx, ClientLevel level, double distance, boolean underwater) {
        double configVol = ClientConfig.soundVolume();
        if (configVol <= 0.0) {
            return;
        }
        ExplosionTier tier = ExplosionTier.fromRadius(fx.radius());
        ExplosionType type = fx.type();
        double maxRange = audibleRange(tier, type);
        if (distance > maxRange * 1.15) {
            return;
        }

        RandomSource rand = RandomSource.create(fx.seed());
        int ordinal = tier.ordinal();
        float jitter = (rand.nextFloat() - 0.5F) * 0.08F;
        float pitchScale = (float) type.look.pitch() * (underwater ? UNDERWATER_PITCH : 1.0F);
        float baseGain = computeGain(distance, tier, type) * (float) configVol * (underwater ? UNDERWATER_VOLUME : 1.0F);
        if (baseGain <= 0.005F) {
            return;
        }

        double pRange = tier.particleRange();
        int delay = travelDelayTicks(distance);
        boolean relative = distance < CENTERED_DISTANCE;

        if (distance < CLOSE_BAND * pRange) {
            float pitch = clampPitch((1.08F - 0.04F * ordinal + jitter) * pitchScale);
            scheduleAt(fx.x(), fx.y(), fx.z(), ModSounds.EXPLOSION_CLOSE.get(), baseGain, pitch, delay, relative, rand.nextLong());
            if (!underwater && ordinal >= 2) {
                float tailPitch = clampPitch((0.92F - 0.02F * ordinal + jitter) * pitchScale);
                scheduleAt(fx.x(), fx.y(), fx.z(), ModSounds.EXPLOSION_FAR.get(), baseGain * 0.42F, tailPitch, delay + 3, relative, rand.nextLong());
            }
        } else if (distance < MID_BAND * pRange) {
            float pitch = clampPitch((0.98F - 0.03F * ordinal + jitter) * pitchScale);
            scheduleAt(fx.x(), fx.y(), fx.z(), ModSounds.EXPLOSION_MID.get(), baseGain, pitch, delay, relative, rand.nextLong());
            if (distance < BLEND_BAND * pRange) {
                float crackPitch = clampPitch((1.02F - 0.04F * ordinal + jitter) * pitchScale);
                scheduleAt(fx.x(), fx.y(), fx.z(), ModSounds.EXPLOSION_CLOSE.get(), baseGain * 0.40F, crackPitch, delay, relative, rand.nextLong());
            }
        } else {
            float pitch = clampPitch((0.94F - 0.02F * ordinal + jitter) * pitchScale);
            scheduleAt(fx.x(), fx.y(), fx.z(), ModSounds.EXPLOSION_FAR.get(), baseGain, pitch, delay, relative, rand.nextLong());
        }

        if (!underwater) {
            if (distance < 0.80 * maxRange) {
                float enclosure = measureEnclosure(level, fx.x(), fx.y(), fx.z());
                if (enclosure >= 0.30F) {
                    float earlyPitch = clampPitch((0.86F - 0.02F * ordinal + jitter) * pitchScale);
                    float latePitch = clampPitch((0.76F - 0.02F * ordinal + jitter) * pitchScale);
                    scheduleAt(fx.x(), fx.y(), fx.z(), ModSounds.EXPLOSION_MID.get(),
                            baseGain * 0.42F * enclosure, earlyPitch, delay + 4, relative, rand.nextLong());
                    scheduleAt(fx.x(), fx.y(), fx.z(), ModSounds.EXPLOSION_FAR.get(),
                            baseGain * 0.34F * enclosure, latePitch, delay + 9, relative, rand.nextLong());
                }
            }

            float whizGain = shrapnelWhizGain(distance, fx.radius(), type) * (float) configVol;
            if (whizGain >= 0.04F) {
                maybePlayShrapnelWhiz(fx, level, whizGain, rand);
            }
        }

        if (ClientConfig.earMuffle() && distance <= fx.radius() * 1.5 + 2.0) {
            int muffleTicks = 35 + ordinal * 15;
            EarMuffle.start(muffleTicks, (float) ClientConfig.earMuffleDuck());
            float ringPitch = clampPitch(0.95F + rand.nextFloat() * 0.1F);
            scheduleAt(0.0, 0.0, 0.0, ModSounds.EAR_RING.get(),
                    0.9F * (float) configVol, ringPitch, 0, true, rand.nextLong());
        }
    }

    static double audibleRange(ExplosionTier tier, ExplosionType type) {
        double minByType = (type == ExplosionType.ROCKET || type == ExplosionType.HEAVY) ? 384.0 : 192.0;
        return Math.max(tier.packetRange(), minByType);
    }

    static float computeGain(double distance, ExplosionTier tier, ExplosionType type) {
        double maxRange = audibleRange(tier, type);
        if (distance > maxRange * 1.15) {
            return 0.0F;
        }
        double fullRange = 18.0 + 6.0 * tier.ordinal();
        if (distance <= fullRange) {
            return 1.0F;
        }
        if (distance >= maxRange) {
            double tail = 1.0 - (distance - maxRange) / (maxRange * 0.15);
            return (float) Math.max(0.0, HORIZON_FLOOR * tail);
        }
        double t = (distance - fullRange) / (maxRange - fullRange);
        double curve = Math.pow(1.0 - t, 1.35);
        return (float) (HORIZON_FLOOR + (1.0 - HORIZON_FLOOR) * curve);
    }

    static float computeEnclosure(boolean hasRoof, int walledRays, int totalRays) {
        if (totalRays <= 0) {
            return 0.0F;
        }
        double wallFrac = Math.max(0.0, Math.min(1.0, (double) walledRays / totalRays));
        if (hasRoof) {
            return walledRays >= 2 ? (float) Math.min(1.0, 0.35 + 0.75 * wallFrac) : 0.0F;
        }
        return walledRays >= 5 ? (float) (0.45 * wallFrac) : 0.0F;
    }

    static float shrapnelWhizGain(double distance, float radius, ExplosionType type) {
        if (radius <= 0.0F || distance < 2.5) {
            return 0.0F;
        }
        double reach = Math.min(48.0, Math.max(8.0, radius * (3.5 + 2.2 * type.look.shrapnel())));
        if (distance > reach) {
            return 0.0F;
        }
        double t = (distance - 2.5) / (reach - 2.5);
        return (float) (0.75 * Math.pow(1.0 - t, 1.15));
    }

    static float clampPitch(float pitch) {
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }

    static int travelDelayTicks(double distance) {
        if (distance <= 0.0) {
            return 0;
        }
        return Math.min(MAX_DELAY, Math.round((float) (distance * TICKS_PER_BLOCK)));
    }

    private static float measureEnclosure(ClientLevel level, double x, double y, double z) {
        int bx = Mth.floor(x);
        int by = Mth.floor(y + 0.5);
        int bz = Mth.floor(z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        boolean hasRoof = false;
        for (int dy = 1; dy <= 10; dy++) {
            pos.set(bx, by + dy, bz);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                hasRoof = true;
                break;
            }
        }

        int walled = 0;
        for (int[] dir : HORIZ_DIRS) {
            for (int step = 1; step <= 9; step++) {
                pos.set(bx + dir[0] * step, by, bz + dir[1] * step);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                    walled++;
                    break;
                }
            }
        }
        return computeEnclosure(hasRoof, walled, HORIZ_DIRS.length);
    }

    private static void maybePlayShrapnelWhiz(ExplosionFx fx, ClientLevel level, float whizGain, RandomSource rand) {
        Minecraft mc = Minecraft.getInstance();
        Entity viewer = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        if (viewer == null || mc.gameRenderer == null) {
            return;
        }
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 blastOrigin = new Vec3(fx.x(), fx.y() + 0.35, fx.z());
        HitResult hit = level.clip(new ClipContext(
                blastOrigin, camPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return;
        }

        // Offset ~1.4 blocks past the player's left or right ear perpendicular to the incoming blast vector.
        Vec3 toCam = camPos.subtract(blastOrigin);
        double horizLen = Math.hypot(toCam.x, toCam.z);
        double perpX = horizLen > 1.0e-3 ? -toCam.z / horizLen : 1.0;
        double perpZ = horizLen > 1.0e-3 ? toCam.x / horizLen : 0.0;
        double side = rand.nextBoolean() ? 1.35 : -1.35;
        double wx = camPos.x + perpX * side;
        double wy = camPos.y + 0.15 + rand.nextDouble() * 0.45;
        double wz = camPos.z + perpZ * side;

        float whizPitch = clampPitch(0.92F + rand.nextFloat() * 0.24F);
        int whizDelay = 1 + rand.nextInt(2);
        scheduleAt(wx, wy, wz, ModSounds.SHRAPNEL_WHIZ.get(), whizGain, whizPitch, whizDelay, false, rand.nextLong());
    }

    private static void scheduleAt(double x, double y, double z, SoundEvent event, float gain, float pitch,
                                    int delayTicks, boolean relative, long seed) {
        float clampedGain = Math.max(0.0F, Math.min(2.0F, gain));
        Runnable task = () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            double sx = relative ? 0.0 : x;
            double sy = relative ? 0.0 : y;
            double sz = relative ? 0.0 : z;
            SimpleSoundInstance instance = new SimpleSoundInstance(
                    event.getLocation(),
                    SoundSource.PLAYERS,
                    clampedGain,
                    pitch,
                    RandomSource.create(seed),
                    false,
                    0,
                    SoundInstance.Attenuation.NONE,
                    sx, sy, sz,
                    relative);
            mc.getSoundManager().play(instance);
        };
        if (delayTicks <= 0) {
            task.run();
        } else {
            FxState.schedule(delayTicks, task);
        }
    }
}
