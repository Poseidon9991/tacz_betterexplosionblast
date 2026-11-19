package com.poseidon.tacz_betterexplosion.config;

import com.poseidon.tacz_betterexplosion.ExplosionDefaults;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side config: explosion damage and knockback tuning. */
public final class ServerConfig {
    private ServerConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Master switch. When false, TaCZ explosions use TaCZ's own damage and knockback.")
            .define("enabled", ExplosionDefaults.ENABLED);

    public static final ForgeConfigSpec.DoubleValue REACH_MULTIPLIER = BUILDER
            .comment("Damage reach as a multiple of the explosion radius.")
            .defineInRange("reachMultiplier", ExplosionDefaults.REACH_MULTIPLIER, 1.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue COVER_FLOOR = BUILDER
            .comment("Minimum fraction of damage that gets through cover (0 = full shielding, 1 = no shielding).")
            .defineInRange("coverFloor", ExplosionDefaults.COVER_FLOOR, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER = BUILDER
            .comment("Scales explosion damage.")
            .defineInRange("damageMultiplier", ExplosionDefaults.DAMAGE_MULTIPLIER, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue KNOCKBACK_MULTIPLIER = BUILDER
            .comment("Scales explosion knockback.")
            .defineInRange("knockbackMultiplier", ExplosionDefaults.KNOCKBACK_MULTIPLIER, 0.0, 10.0);

    public static final ForgeConfigSpec.IntValue MAX_TARGETS = BUILDER
            .comment("Maximum number of entities one explosion affects. The nearest are kept.")
            .defineInRange("maxTargets", ExplosionDefaults.MAX_TARGETS, 1, 512);

    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> AMMO_TYPES = BUILDER
            .comment("Explosion type per ammo id, as \"namespace:ammo=TYPE\" with TYPE one of STANDARD, GRENADE,",
                    "ROCKET, HEAVY. Ammo not listed is detected from its id (40mm/grenade, rpg/rocket/missile,",
                    "mortar/artillery/bomb), anything else is STANDARD. Example: [\"mypack:big_shell=HEAVY\"]")
            .defineListAllowEmpty("ammoTypes", java.util.List.of(), o -> o instanceof String s && s.contains("="));

    public static final ForgeConfigSpec.BooleanValue IMPROVED_ROCKET_FLIGHT = BUILDER
            .comment("Improve RPG and rocket flight dynamics: motor ignition acceleration, extended range, and realistic flight curve.")
            .define("improvedRocketFlight", ExplosionDefaults.IMPROVED_ROCKET_FLIGHT);

    public static final ForgeConfigSpec.BooleanValue ROCKET_CHUNK_LOADING = BUILDER
            .comment("Force-load chunks ahead of fast rockets so they fly smoothly without freezing or disappearing in unloaded chunks.")
            .define("rocketChunkLoading", ExplosionDefaults.ROCKET_CHUNK_LOADING);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enabled() {
        return !SPEC.isLoaded() ? ExplosionDefaults.ENABLED : ENABLED.get();
    }

    public static double reachMultiplier() {
        return !SPEC.isLoaded() ? ExplosionDefaults.REACH_MULTIPLIER : REACH_MULTIPLIER.get();
    }

    public static double coverFloor() {
        return !SPEC.isLoaded() ? ExplosionDefaults.COVER_FLOOR : COVER_FLOOR.get();
    }

    public static double damageMultiplier() {
        return !SPEC.isLoaded() ? ExplosionDefaults.DAMAGE_MULTIPLIER : DAMAGE_MULTIPLIER.get();
    }

    public static double knockbackMultiplier() {
        return !SPEC.isLoaded() ? ExplosionDefaults.KNOCKBACK_MULTIPLIER : KNOCKBACK_MULTIPLIER.get();
    }

    public static int maxTargets() {
        return !SPEC.isLoaded() ? ExplosionDefaults.MAX_TARGETS : MAX_TARGETS.get();
    }

    public static java.util.List<? extends String> ammoTypes() {
        return !SPEC.isLoaded() ? java.util.List.of() : AMMO_TYPES.get();
    }

    public static boolean improvedRocketFlight() {
        return !SPEC.isLoaded() ? ExplosionDefaults.IMPROVED_ROCKET_FLIGHT : IMPROVED_ROCKET_FLIGHT.get();
    }

    public static boolean rocketChunkLoading() {
        return !SPEC.isLoaded() ? ExplosionDefaults.ROCKET_CHUNK_LOADING : ROCKET_CHUNK_LOADING.get();
    }
}