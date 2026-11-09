package com.poseidon.tacz_betterexplosion;

/**
 * Default values for every config option. The config specs use these as their defaults, and the
 * config getters return them while a spec is not loaded.
 */
public final class ExplosionDefaults {

    // Server
    public static final boolean ENABLED = true;
    public static final double REACH_MULTIPLIER = 2.0;
    public static final double COVER_FLOOR = 0.25;
    public static final double DAMAGE_MULTIPLIER = 1.0;
    public static final double KNOCKBACK_MULTIPLIER = 1.0;
    public static final int MAX_TARGETS = 128;

    // Client
    public static final String QUALITY = "HIGH";
    public static final int MAX_LIVE_PARTICLES = 700;
    public static final boolean ADAPTIVE_QUALITY = true;
    public static final boolean SCREEN_SHAKE = true;
    public static final double SHAKE_STRENGTH = 1.0;
    public static final boolean SCREEN_FLASH = true;

    private ExplosionDefaults() {
    }
}
