package com.poseidon.tacz_betterexplosion.client.fx;

import com.poseidon.tacz_betterexplosion.ModSounds;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Distance-banded explosion sound: close / mid / far against the tier's particle range, played after
 * {@code distance / 17} ticks of sound travel (capped at 100). Seeded from {@code fx.seed()} so the pitch
 * jitter is the same for every client.
 */
public final class ExplosionSounds {

    /** Fraction of the particle range where the close band ends and the mid band ends. */
    private static final double CLOSE_BAND = 0.35;
    private static final double MID_BAND = 0.75;
    /** Ticks of travel delay per block of distance, and its cap. */
    private static final double TICKS_PER_BLOCK = 1.0 / 17.0;
    private static final int MAX_DELAY = 100;

    private ExplosionSounds() {}

    /** Picks the band for {@code distance} and plays it now or through {@link FxState#schedule}. */
    public static void play(ExplosionFx fx, ClientLevel level, double distance) {
        RandomSource rand = RandomSource.create(fx.seed());
        ExplosionTier tier = ExplosionTier.fromRadius(fx.radius());
        double range = tier.particleRange();
        int ordinal = tier.ordinal();
        float jitter = (rand.nextFloat() - 0.5F) * 0.1F;

        SoundEvent sound;
        float volume;
        float pitch;
        if (distance < CLOSE_BAND * range) {
            sound = ModSounds.EXPLOSION_CLOSE.get();
            volume = 2.0F + ordinal;
            pitch = 1.15F - 0.1F * ordinal + jitter;
        } else if (distance < MID_BAND * range) {
            sound = ModSounds.EXPLOSION_MID.get();
            volume = 3.0F;
            pitch = 0.8F + jitter;
        } else {
            sound = ModSounds.EXPLOSION_FAR.get();
            volume = 6.0F;
            pitch = 0.5F + jitter;
        }

        int delay = Math.min(MAX_DELAY, Math.round((float) (distance * TICKS_PER_BLOCK)));
        Runnable play = () -> level.playLocalSound(
                fx.x(), fx.y(), fx.z(), sound, SoundSource.BLOCKS, volume, pitch, false);
        if (delay <= 0) {
            play.run();
        } else {
            FxState.schedule(delay, play);
        }
    }
}
