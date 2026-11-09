package com.poseidon.tacz_betterexplosion.fx;

import java.util.EnumMap;

/** Pure particle budget maths: scaling factors, distance culling and the per-layer split. */
public final class FxBudget {

    /** Vanilla "particles" video setting, as a budget multiplier. */
    public enum VanillaParticles {
        ALL(1.0),
        DECREASED(0.5),
        MINIMAL(0.15);

        /** Multiplier applied to the budget. */
        public final double factor;

        VanillaParticles(double factor) {
            this.factor = factor;
        }
    }

    /** Visual layers of one explosion. */
    public enum Layer {
        FLASH,
        FIREBALL,
        RING,
        STEM,
        DUST,
        DEBRIS,
        SPARKS
    }

    private static final int FLASH_FIXED = 2;
    private static final int RING_FIXED = 1;
    private static final double FAR_LAYER_DISTANCE = 64.0;
    private static final double BEHIND_CAMERA_DISTANCE = 24.0;

    private FxBudget() {}

    /** Budget factor after {@code recentExplosions} recent bursts: {@code max(0.25, 0.7^n)}; n <= 0 gives 1. */
    public static double burstFactor(int recentExplosions) {
        if (recentExplosions <= 0) {
            return 1.0;
        }
        return Math.max(0.25, Math.pow(0.7, recentExplosions));
    }

    /** Budget factor for the current frame rate. A non-positive fps (unknown) does not throttle. */
    public static double fpsFactor(int fps) {
        if (fps <= 0) {
            return 1.0;
        }
        if (fps < 20) {
            return 0.25;
        }
        if (fps < 30) {
            return 0.5;
        }
        return 1.0;
    }

    /** Distance factor: 0 beyond the tier's particle range (or NaN), 0.4 far behind the camera, else 1. */
    public static double lodFactor(double distance, ExplosionTier tier, boolean behindCamera) {
        if (Double.isNaN(distance) || distance > tier.particleRange()) {
            return 0.0;
        }
        if (behindCamera && distance > BEHIND_CAMERA_DISTANCE) {
            return 0.4;
        }
        return 1.0;
    }

    /** Whether a layer is drawn at this distance. Beyond 64 blocks only FLASH and STEM; NaN allows nothing. */
    public static boolean layerAllowed(Layer layer, double distance) {
        if (Double.isNaN(distance)) {
            return false;
        }
        if (distance > FAR_LAYER_DISTANCE) {
            return layer == Layer.FLASH || layer == Layer.STEM;
        }
        return true;
    }

    /**
     * Particles to spawn for one explosion, never negative and never above the free live-particle room.
     *
     * @param fps the already computed {@link #fpsFactor(int)} value, not a raw frame rate
     */
    public static int budget(
            ExplosionTier tier,
            Quality q,
            VanillaParticles v,
            double burst,
            double fps,
            double lod,
            int liveNow,
            int maxLive) {
        double scaled = tier.budget() * q.density * v.factor * burst * fps * lod;
        long rounded = Double.isNaN(scaled) ? 0L : Math.round(scaled);
        long room = (long) maxLive - liveNow;
        long result = Math.max(0L, Math.min(rounded, room));
        return (int) Math.min(result, Integer.MAX_VALUE);
    }

    /**
     * Splits a budget over all layers. FLASH (2) and RING (1) are fixed, clamped to the budget in that priority;
     * the remainder is shared 30/30/12/14/14 percent over FIREBALL, STEM, DUST, DEBRIS, SPARKS with the
     * rounding leftover handed out round-robin in the order FIREBALL, STEM, DEBRIS, SPARKS, DUST.
     * The layers always sum to {@code budget} (all zero when it is not positive) and the map holds every layer.
     */
    public static EnumMap<Layer, Integer> split(int budget) {
        EnumMap<Layer, Integer> out = new EnumMap<>(Layer.class);
        for (Layer layer : Layer.values()) {
            out.put(layer, 0);
        }
        if (budget <= 0) {
            return out;
        }
        int flash = Math.min(FLASH_FIXED, budget);
        int ring = Math.min(RING_FIXED, budget - flash);
        int remainder = budget - flash - ring;
        out.put(Layer.FLASH, flash);
        out.put(Layer.RING, ring);

        int fireball = remainder * 30 / 100;
        int stem = remainder * 30 / 100;
        int dust = remainder * 12 / 100;
        int debris = remainder * 14 / 100;
        int sparks = remainder * 14 / 100;
        int leftover = remainder - (fireball + stem + dust + debris + sparks);

        Layer[] order = {Layer.FIREBALL, Layer.STEM, Layer.DEBRIS, Layer.SPARKS, Layer.DUST};
        out.put(Layer.FIREBALL, fireball);
        out.put(Layer.STEM, stem);
        out.put(Layer.DUST, dust);
        out.put(Layer.DEBRIS, debris);
        out.put(Layer.SPARKS, sparks);
        for (int i = 0; i < leftover; i++) {
            Layer layer = order[i % order.length];
            out.put(layer, out.get(layer) + 1);
        }
        return out;
    }
}
