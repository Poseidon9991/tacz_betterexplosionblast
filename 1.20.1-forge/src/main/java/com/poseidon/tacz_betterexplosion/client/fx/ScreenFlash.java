package com.poseidon.tacz_betterexplosion.client.fx;

import com.mojang.blaze3d.platform.Window;
import net.minecraftforge.client.event.RenderGuiEvent;

/**
 * Full-screen white flash for close explosions. {@link #flash()} starts a 4-tick overlay whose alpha ramps
 * 0.35 to 0; ages are counted in {@link FxState#clientTicks()} like {@link ScreenShake}. Drawn on
 * {@code RenderGuiEvent.Post} so it sits above the world but under nothing else that frame.
 */
public final class ScreenFlash {

    /** Flash duration in ticks and its starting alpha. */
    private static final int DURATION = 4;
    private static final float START_ALPHA = 0.35F;

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
        float alpha = START_ALPHA * (1.0F - age / DURATION);
        int argb = ((int) (alpha * 255.0F) << 24) | 0xFFFFFF;
        Window window = event.getWindow();
        event.getGuiGraphics().fill(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), argb);
    }

    /** Drops any running flash (logout, level unload). */
    public static void clear() {
        startTick = Long.MIN_VALUE;
    }
}
