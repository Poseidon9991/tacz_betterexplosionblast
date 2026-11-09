package com.poseidon.tacz_betterexplosion.client.fx;

/**
 * Client-side counters for the mod's live explosion particles. Plain statics, touched only from the client
 * main thread. No Minecraft types, so it is unit-testable.
 */
public final class FxState {

    private static int live;
    private static int peakLive;

    private FxState() {}

    /** Number of mod particles currently alive. */
    public static int liveParticles() {
        return live;
    }

    /** Counts one new particle and raises the peak if needed. */
    public static void particleAdded() {
        live++;
        if (live > peakLive) {
            peakLive = live;
        }
    }

    /** Counts one removed particle; never goes below zero. */
    public static void particleRemoved() {
        if (live > 0) {
            live--;
        }
    }

    /** Highest live count since the last {@link #resetPeak()} or {@link #reset()}. */
    public static int peakLive() {
        return peakLive;
    }

    /** Sets the peak to the current live count. */
    public static void resetPeak() {
        peakLive = live;
    }

    /** Zeroes the live count and the peak. */
    public static void reset() {
        live = 0;
        peakLive = 0;
    }
}
