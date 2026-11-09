package com.poseidon.tacz_betterexplosion.client.fx;

import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Camera shake. {@link #kick(float)} starts (or strengthens) a decaying 15-tick shake; ages are counted in
 * {@link FxState#clientTicks()}. On {@code ComputeCameraAngles} the decayed strength {@code e} is added as
 * {@code e * sin(age * 1.3 + phase)} to roll and at 0.6 weight with cosine/sine to pitch and yaw, so the
 * camera wobbles on all three axes. Concurrent kicks keep the max strength instead of summing.
 */
public final class ScreenShake {

    /** Shake duration in ticks. */
    private static final int DURATION = 15;

    private static float strength;
    private static long startTick = Long.MIN_VALUE;
    private static float phaseRoll;
    private static float phasePitch;
    private static float phaseYaw;

    private ScreenShake() {}

    /**
     * Starts a shake of {@code strengthDeg} degrees, or raises the running one. A kick while a shake is live
     * keeps the running age and phases and only takes the max strength (kicks never sum); once the shake
     * expired a kick restarts the decay with new phases. Non-positive kicks do nothing.
     */
    public static void kick(float strengthDeg) {
        if (!(strengthDeg > 0.0F)) {
            return;
        }
        long now = FxState.clientTicks();
        if (startTick == Long.MIN_VALUE || now - startTick >= DURATION) {
            startTick = now;
            strength = strengthDeg;
            RandomSource rand = RandomSource.create();
            phaseRoll = rand.nextFloat() * (float) (Math.PI * 2.0);
            phasePitch = rand.nextFloat() * (float) (Math.PI * 2.0);
            phaseYaw = rand.nextFloat() * (float) (Math.PI * 2.0);
        } else {
            strength = Math.max(strength, strengthDeg);
        }
    }

    /** Adds the shake offsets for the current age to the event's camera angles. */
    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        long age = FxState.clientTicks() - startTick;
        if (age < 0 || age >= DURATION) {
            return;
        }
        double decay = 1.0 - (double) age / DURATION;
        float e = (float) (strength * decay * decay);
        event.setRoll(event.getRoll() + (float) (e * Math.sin(age * 1.3 + phaseRoll)));
        event.setPitch(event.getPitch() + (float) (e * 0.6 * Math.sin(age * 1.7 + phasePitch)));
        event.setYaw(event.getYaw() + (float) (e * 0.6 * Math.cos(age * 1.5 + phaseYaw)));
    }

    /** Drops any running shake (logout, level unload). */
    public static void clear() {
        strength = 0.0F;
        startTick = Long.MIN_VALUE;
    }
}
