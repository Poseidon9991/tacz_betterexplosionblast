package com.poseidon.tacz_betterexplosion.fx;

import java.util.List;
import java.util.Locale;

/**
 * Explosion archetype picked on the server from the ammo that exploded and sent to clients in bits 1-3 of the
 * packet's {@code flags} byte. It changes both the gameplay profile ({@link Gameplay}) and the look and sound
 * ({@link Look}). Pure data, no Minecraft types.
 *
 * <ul>
 *   <li>{@link #STANDARD}: the unmodified behaviour (anything that is not recognised).</li>
 *   <li>{@link #GRENADE} (40mm, grenades): anti-personnel fragmentation: wide splash, lots of shrapnel and a
 *       fast dust ring, small snappy fireball, light grey short-lived smoke.</li>
 *   <li>{@link #ROCKET} (RPG, rockets, missiles): concentrated heavy blast: strong fireball and sparks, thick
 *       dark smoke, hard knockback and concussion that gets through cover, little shrapnel.</li>
 *   <li>{@link #HEAVY} (mortar, artillery, bombs): everything bigger, dirt fountain, huge long smoke.</li>
 * </ul>
 */
public enum ExplosionType {
    //            id  gameplay(reach, damage, knockback, coverFloorDelta)
    //                look(visualRadius, shake, smokeSize, smokeLife, pitch, tint,
    //                     fireball, stem, dust, debris, sparks, shrapnel)
    STANDARD(0,
            new Gameplay(1.0, 1.0, 1.0, 0.0),
            new Look(1.0, 1.0, 1.0, 1.0, 1.0, null, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)),
    GRENADE(1,
            new Gameplay(1.35, 1.0, 0.8, -0.15),
            new Look(0.65, 0.8, 0.9, 0.75, 1.12, new float[] {0.66F, 0.64F, 0.60F},
                    0.7, 0.8, 1.2, 1.3, 1.3, 2.4)),
    ROCKET(2,
            new Gameplay(0.9, 1.25, 1.4, 0.1),
            new Look(1.3, 1.3, 1.15, 1.2, 0.9, null, 1.6, 1.3, 1.0, 1.0, 1.8, 0.35)),
    HEAVY(3,
            new Gameplay(1.2, 1.2, 1.6, 0.1),
            new Look(1.0, 1.8, 1.3, 1.4, 0.8, null, 1.4, 1.7, 1.4, 2.2, 1.0, 0.6));

    /**
     * Gameplay multipliers applied on top of the server config.
     *
     * @param reachScale        multiplies {@code reachMultiplier}
     * @param damageScale       multiplies the damage multiplier
     * @param knockbackScale    multiplies the knockback multiplier
     * @param coverFloorDelta   added to {@code coverFloor} (result clamped to [0, 1])
     */
    public record Gameplay(double reachScale, double damageScale, double knockbackScale, double coverFloorDelta) {
    }

    /**
     * Client look: sizes and per-layer particle count multipliers.
     *
     * @param radiusScale multiplies the packet radius for everything visual (tier, sizes, sound)
     * @param shake       multiplies the screen shake strength
     * @param smokeSize   multiplies the size of blast smoke puffs
     * @param smokeLife   multiplies the lifetime of blast smoke puffs
     * @param pitch       multiplies the pitch of the blast sound
     * @param tint        optional RGB the blast smoke is pulled toward, or null for the default dark smoke
     */
    public record Look(double radiusScale, double shake, double smokeSize, double smokeLife, double pitch,
                       float[] tint, double fireball, double stem, double dust, double debris, double sparks,
                       double shrapnel) {

        /** Count multiplier of one layer. */
        public double layer(FxBudget.Layer layer) {
            return switch (layer) {
                case FLASH -> 1.0;
                case FIREBALL -> fireball;
                case STEM -> stem;
                case DUST -> dust;
                case DEBRIS -> debris;
                case SPARKS -> sparks;
                case SHRAPNEL -> shrapnel;
            };
        }
    }

    private static final ExplosionType[] BY_ID = values();
    private static final List<String> HEAVY_KEYS = List.of("mortar", "artillery", "howitzer", "bomb", "heavy_shell");
    private static final List<String> ROCKET_KEYS =
            List.of("rpg", "rocket", "missile", "pg7", "pg-7", "javelin", "stinger", "tandem");
    private static final List<String> GRENADE_KEYS = List.of("40mm", "grenade", "m203", "m320", "hedp", "40x46");

    public final int id;
    public final Gameplay gameplay;
    public final Look look;

    ExplosionType(int id, Gameplay gameplay, Look look) {
        this.id = id;
        this.gameplay = gameplay;
        this.look = look;
    }

    /** The type with this id; an unknown id is {@link #STANDARD}. */
    public static ExplosionType byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : STANDARD;
    }

    /**
     * Picks the type of an ammo id such as {@code tacz:40mm}: first an exact {@code "ammoId=TYPE"} entry of
     * {@code overrides}, then keywords in the path (heavy, then rocket, then grenade). Null or unknown ammo
     * is {@link #STANDARD}.
     */
    public static ExplosionType classify(String ammoId, List<? extends String> overrides) {
        if (ammoId == null || ammoId.isEmpty()) {
            return STANDARD;
        }
        String id = ammoId.toLowerCase(Locale.ROOT);
        if (overrides != null) {
            for (String entry : overrides) {
                int eq = entry.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                if (entry.substring(0, eq).trim().toLowerCase(Locale.ROOT).equals(id)) {
                    try {
                        return valueOf(entry.substring(eq + 1).trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        // Bad type name: fall through to the keyword rules.
                    }
                }
            }
        }
        if (containsAny(id, HEAVY_KEYS)) {
            return HEAVY;
        }
        if (containsAny(id, ROCKET_KEYS)) {
            return ROCKET;
        }
        if (containsAny(id, GRENADE_KEYS)) {
            return GRENADE;
        }
        return STANDARD;
    }

    private static boolean containsAny(String id, List<String> keys) {
        for (String key : keys) {
            if (id.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
