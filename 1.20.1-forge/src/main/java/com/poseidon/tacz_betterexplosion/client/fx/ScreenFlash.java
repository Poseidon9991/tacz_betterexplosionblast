package com.poseidon.tacz_betterexplosion.client.fx;

import com.mojang.blaze3d.platform.Window;
import net.minecraftforge.client.event.RenderGuiEvent;

/**
 * Full-screen white flash for close explosions. {@link #flash()} starts an 8-tick overlay: full alpha 0.45
 * for the first 2 ticks, then {@code 0.45 * (1 - u)^2} with {@code u} running 0 to 1 over the remaining 6
 * ticks; ages are counted in {@link FxState#clientTicks()} like {@link ScreenShake}. Drawn on
 * {@code RenderGuiEvent.Post} so it sits above the world but under nothing else that frame.
 */
public final class ScreenFlash {

    /** Flash duration in ticks, the ticks held at full alpha, and the peak alpha. */
    private static final int DURATION = 8;
    private static final int HOLD = 2;
    private static final float PEAK_ALPHA = 0.45F;

    private static long startTick = Long.MIN_VALUE;

    private ScreenFlash() {}

    /** Starts (or restarts) the flash now. */
    public static void flash() {
        startTick = FxState.clientTicks();
    }

    /** Fills the screen with the fading white overlay while the flash is live. */
    public static void render(RenderGuiEvent.Post event) {
        float age = FxState.clientTicks() - startTick + event.getPartialTick();
        if (age < 0.0F || age >= DURATION) {
            return;
        }
        float u = Math.max(0.0F, (age - HOLD) / (DURATION - HOLD));
        float alpha = PEAK_ALPHA * (1.0F - u) * (1.0F - u);
        int argb = ((int) (alpha * 255.0F) << 24) | 0xFFFFFF;
        Window window = event.getWindow();
        event.getGuiGraphics().fill(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), argb);
    }

    /** Drops any running flash (logout, level unload). */
    public static void clear() {
        startTick = Long.MIN_VALUE;
    }
}
