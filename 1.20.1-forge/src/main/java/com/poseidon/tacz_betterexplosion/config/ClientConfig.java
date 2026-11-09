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
                    "MEDIUM: density 0.65, adds shrapnel; still no debris or sparks.",
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
}
