package com.poseidon.tacz_betterexplosion.fx;

/** Explosion size class: effect budget and visibility ranges, chosen by radius. */
public enum ExplosionTier {
    MINI(2.0f, 12, 64, 48),
    SMALL(3.5f, 28, 96, 64),
    MEDIUM(6.0f, 55, 160, 96),
    LARGE(10.0f, 90, 224, 144),
    HUGE(Float.POSITIVE_INFINITY, 150, 256, 192);

    private final float radiusLimit;
    private final int budget;
    private final int packetRange;
    private final int particleRange;

    ExplosionTier(float radiusLimit, int budget, int packetRange, int particleRange) {
        this.radiusLimit = radiusLimit;
        this.budget = budget;
        this.packetRange = packetRange;
        this.particleRange = particleRange;
    }

    /** Particle budget for one explosion of this tier at quality HIGH. */
    public int budget() {
        return budget;
    }

    /** Blocks within which players receive the fx packet. */
    public int packetRange() {
        return packetRange;
    }

    /** Blocks within which a client spawns particles. */
    public int particleRange() {
        return particleRange;
    }

    /** The tier whose exclusive upper radius bound is above {@code radius}. NaN and negatives give MINI. */
    public static ExplosionTier fromRadius(float radius) {
        if (Float.isNaN(radius)) {
            return MINI;
        }
        for (ExplosionTier tier : values()) {
            if (radius < tier.radiusLimit) {
                return tier;
            }
        }
        return HUGE;
    }
}
