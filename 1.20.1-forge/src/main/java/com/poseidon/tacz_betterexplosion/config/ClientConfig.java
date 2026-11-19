package com.poseidon.tacz_betterexplosion.config;

import com.poseidon.tacz_betterexplosion.ExplosionDefaults;
import com.poseidon.tacz_betterexplosion.fx.Quality;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client-side config: explosion visual effects. */
public final class ClientConfig {
    private ClientConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.EnumValue<Quality> QUALITY = BUILDER
            .comment("Explosion particle quality. Scales the particle budget of each explosion.",
                    "LOW: density 0.3, no debris, sparks or shrapnel (flash, fireball, stem, dust only).",
                    "MEDIUM: density 0.65, adds debris and shrapnel; no sparks.",
                    "HIGH: density 1.0, every layer.")
            .defineEnum("quality", Quality.valueOf(ExplosionDefaults.QUALITY));

    public static final ForgeConfigSpec.IntValue MAX_LIVE_PARTICLES = BUILDER
            .comment("Cap on explosion particles alive at once.")
            .defineInRange("maxLiveParticles", ExplosionDefaults.MAX_LIVE_PARTICLES, 50, 5000);

    public static final ForgeConfigSpec.BooleanValue ADAPTIVE_QUALITY = BUILDER
            .comment("Lower the particle count automatically when the frame rate drops.")
            .define("adaptiveQuality", ExplosionDefaults.ADAPTIVE_QUALITY);

    public static final ForgeConfigSpec.BooleanValue SCREEN_SHAKE = BUILDER
            .comment("Shake the screen when an explosion goes off nearby.")
            .define("screenShake", ExplosionDefaults.SCREEN_SHAKE);

    public static final ForgeConfigSpec.DoubleValue SHAKE_STRENGTH = BUILDER
            .comment("Scales screen shake strength.")
            .defineInRange("shakeStrength", ExplosionDefaults.SHAKE_STRENGTH, 0.0, 2.0);

    public static final ForgeConfigSpec.BooleanValue SCREEN_FLASH = BUILDER
            .comment("Flash the screen when an explosion goes off close by.")
            .define("screenFlash", ExplosionDefaults.SCREEN_FLASH);

    public static final ForgeConfigSpec.DoubleValue BLAST_SMOKE_MULTIPLIER = BUILDER
            .comment("Scales the amount of rising black/orange blast smoke (0 = off, 1 = default, up to 3).")
            .defineInRange("blastSmokeMultiplier", ExplosionDefaults.BLAST_SMOKE_MULTIPLIER, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue GROUND_SMOKE_MULTIPLIER = BUILDER
            .comment("Scales the amount of flat dusty ground smoke and dust shock ring (0 = off, 1 = default, up to 3).")
            .defineInRange("groundSmokeMultiplier", ExplosionDefaults.GROUND_SMOKE_MULTIPLIER, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue DEBRIS_MULTIPLIER = BUILDER
            .comment("Scales the amount of ground block debris and dust kick-up (0 = off, 1 = default, up to 3).")
            .defineInRange("debrisMultiplier", ExplosionDefaults.DEBRIS_MULTIPLIER, 0.0, 3.0);

    public static final ForgeConfigSpec.BooleanValue ROCKET_SMOKE_TRAIL = BUILDER
            .comment("Render realistic billowing smoke trail and rocket motor flare behind in-flight RPG and rockets.")
            .define("rocketSmokeTrail", ExplosionDefaults.ROCKET_SMOKE_TRAIL);

    public static final ForgeConfigSpec.BooleanValue ROCKET_FLIGHT_SOUND = BUILDER
            .comment("Play continuous whooshing flight sound with Doppler effect for flying rockets.")
            .define("rocketFlightSound", ExplosionDefaults.ROCKET_FLIGHT_SOUND);

    public static final ForgeConfigSpec.DoubleValue SOUND_VOLUME = BUILDER
            .comment("Master volume multiplier for this mod's explosion and rocket flight sounds (0 = mute, 1 = default, up to 2).")
            .defineInRange("soundVolume", ExplosionDefaults.SOUND_VOLUME, 0.0, 2.0);

    public static final ForgeConfigSpec.BooleanValue EAR_MUFFLE = BUILDER
            .comment("Temporarily deafen (duck all other sounds and play an ear ring) when a blast detonates close by.")
            .define("earMuffle", ExplosionDefaults.EAR_MUFFLE);

    public static final ForgeConfigSpec.DoubleValue EAR_MUFFLE_DUCK = BUILDER
            .comment("How deep the ear muffle ducks other sounds (0 = silence, 1 = no ducking).")
            .defineInRange("earMuffleDuck", ExplosionDefaults.EAR_MUFFLE_DUCK, 0.0, 1.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static Quality quality() {
        return !SPEC.isLoaded() ? Quality.valueOf(ExplosionDefaults.QUALITY) : QUALITY.get();
    }

    public static int maxLiveParticles() {
        return !SPEC.isLoaded() ? ExplosionDefaults.MAX_LIVE_PARTICLES : MAX_LIVE_PARTICLES.get();
    }

    public static boolean adaptiveQuality() {
        return !SPEC.isLoaded() ? ExplosionDefaults.ADAPTIVE_QUALITY : ADAPTIVE_QUALITY.get();
    }

    public static boolean screenShake() {
        return !SPEC.isLoaded() ? ExplosionDefaults.SCREEN_SHAKE : SCREEN_SHAKE.get();
    }

    public static double shakeStrength() {
        return !SPEC.isLoaded() ? ExplosionDefaults.SHAKE_STRENGTH : SHAKE_STRENGTH.get();
    }

    public static boolean screenFlash() {
        return !SPEC.isLoaded() ? ExplosionDefaults.SCREEN_FLASH : SCREEN_FLASH.get();
    }

    public static double blastSmokeMultiplier() {
        return !SPEC.isLoaded() ? ExplosionDefaults.BLAST_SMOKE_MULTIPLIER : BLAST_SMOKE_MULTIPLIER.get();
    }

    public static double groundSmokeMultiplier() {
        return !SPEC.isLoaded() ? ExplosionDefaults.GROUND_SMOKE_MULTIPLIER : GROUND_SMOKE_MULTIPLIER.get();
    }

    public static double debrisMultiplier() {
        return !SPEC.isLoaded() ? ExplosionDefaults.DEBRIS_MULTIPLIER : DEBRIS_MULTIPLIER.get();
    }

    public static boolean rocketSmokeTrail() {
        return !SPEC.isLoaded() ? ExplosionDefaults.ROCKET_SMOKE_TRAIL : ROCKET_SMOKE_TRAIL.get();
    }

    public static boolean rocketFlightSound() {
        return !SPEC.isLoaded() ? ExplosionDefaults.ROCKET_FLIGHT_SOUND : ROCKET_FLIGHT_SOUND.get();
    }

    public static double soundVolume() {
        return !SPEC.isLoaded() ? ExplosionDefaults.SOUND_VOLUME : SOUND_VOLUME.get();
    }

    public static boolean earMuffle() {
        return !SPEC.isLoaded() ? ExplosionDefaults.EAR_MUFFLE : EAR_MUFFLE.get();
    }

    public static double earMuffleDuck() {
        return !SPEC.isLoaded() ? ExplosionDefaults.EAR_MUFFLE_DUCK : EAR_MUFFLE_DUCK.get();
    }
}
