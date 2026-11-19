package com.poseidon.tacz_betterexplosion;

/**
 * Default values for every config option. The config specs use these as their defaults, and the
 * config getters return them while a spec is not loaded.
 */
public final class ExplosionDefaults {

    // Server
    public static final boolean ENABLED = true;
    public static final double REACH_MULTIPLIER = 2.0;
    public static final double COVER_FLOOR = 0.5;
    public static final double DAMAGE_MULTIPLIER = 1.0;
    public static final double KNOCKBACK_MULTIPLIER = 1.0;
    public static final int MAX_TARGETS = 128;
    public static final boolean IMPROVED_ROCKET_FLIGHT = true;
    public static final boolean ROCKET_CHUNK_LOADING = true;

    // Client
    public static final String QUALITY = "HIGH";
    public static final int MAX_LIVE_PARTICLES = 1200;
    public static final boolean ADAPTIVE_QUALITY = true;
    public static final boolean SCREEN_SHAKE = true;
    public static final double SHAKE_STRENGTH = 1.0;
    public static final boolean SCREEN_FLASH = true;
    public static final double BLAST_SMOKE_MULTIPLIER = 1.0;
    public static final double GROUND_SMOKE_MULTIPLIER = 1.0;
    public static final double DEBRIS_MULTIPLIER = 1.0;
    public static final boolean ROCKET_SMOKE_TRAIL = true;
    public static final boolean ROCKET_FLIGHT_SOUND = true;
    public static final double SOUND_VOLUME = 1.0;

    private ExplosionDefaults() {
    }
}
