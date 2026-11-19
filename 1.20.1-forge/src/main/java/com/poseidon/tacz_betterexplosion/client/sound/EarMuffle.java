package com.poseidon.tacz_betterexplosion.client.sound;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Temporary deafening after a close-range blast: ducks every sound category except
 * {@link SoundSource#PLAYERS} (which carries this mod's own ring and whiz sounds) and
 * {@link SoundSource#MASTER} for a short window, then eases back to the captured volumes.
 *
 * <p>Driven by {@link #tick()} once per client tick. Slider values are only changed in memory;
 * {@link Options#save} is never called, so a crash can never corrupt the user's audio settings.
 */
public final class EarMuffle {

    /** Attack ticks to reach full duck depth. */
    static final int ATTACK_TICKS = 3;

    private static int ticksLeft;
    private static int totalTicks;
    private static float duckDepth = 0.12F;
    private static Map<SoundSource, Double> captured;

    private EarMuffle() {}

    /** Starts (or extends) a muffle window; overlapping blasts take the longer remaining time. */
    public static void start(int ticks, float duck) {
        if (captured == null) {
            captured = new EnumMap<>(SoundSource.class);
            Minecraft mc = Minecraft.getInstance();
            for (SoundSource source : SoundSource.values()) {
                captured.put(source, mc.options.getSoundSourceOptionInstance(source).get());
            }
        }
        totalTicks = Math.max(ticksLeft, ticks);
        ticksLeft = totalTicks;
        duckDepth = Math.max(0.0F, Math.min(1.0F, duck));
    }

    /** Applies the current duck factor to every ducked category; restores when the window ends. */
    public static void tick() {
        if (captured == null || ticksLeft <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float factor = duckFactor(totalTicks - ticksLeft, totalTicks, duckDepth);
        for (Map.Entry<SoundSource, Double> entry : captured.entrySet()) {
            SoundSource source = entry.getKey();
            if (source == SoundSource.PLAYERS || source == SoundSource.MASTER) {
                continue;
            }
            option(mc, source).set(entry.getValue() * factor);
        }
        if (--ticksLeft <= 0) {
            restore();
        }
    }

    /** Immediately restores captured volumes (world unload / logout). */
    public static void reset() {
        if (captured != null) {
            restore();
        }
    }

    /**
     * Pure duck envelope: instant ramp to {@code duck} over {@link #ATTACK_TICKS}, then a smooth
     * release back to 1.0 by {@code total} ticks.
     */
    static float duckFactor(int ticksInto, int total, float duck) {
        if (total <= 0) {
            return 1.0F;
        }
        if (ticksInto < ATTACK_TICKS) {
            return 1.0F + (duck - 1.0F) * ((float) (ticksInto + 1) / ATTACK_TICKS);
        }
        double t = Math.min(1.0, (double) (ticksInto - ATTACK_TICKS) / Math.max(1, total - ATTACK_TICKS));
        double release = t * t * (3.0 - 2.0 * t); // smoothstep
        return (float) (duck + (1.0 - duck) * release);
    }

    /** Whether a muffle window is currently active. */
    public static boolean isActive() {
        return captured != null && ticksLeft > 0;
    }

    private static void restore() {
        Minecraft mc = Minecraft.getInstance();
        for (Map.Entry<SoundSource, Double> entry : captured.entrySet()) {
            option(mc, entry.getKey()).set(entry.getValue());
        }
        captured = null;
        ticksLeft = 0;
        totalTicks = 0;
    }

    private static OptionInstance<Double> option(Minecraft mc, SoundSource source) {
        return mc.options.getSoundSourceOptionInstance(source);
    }
}
