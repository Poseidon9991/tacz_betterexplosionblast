package com.poseidon.tacz_betterexplosion.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExplosionTypeTest {

    @Test
    void classifiesDefaultTaczAmmo() {
        assertEquals(ExplosionType.GRENADE, ExplosionType.classify("tacz:40mm", List.of()));
        assertEquals(ExplosionType.ROCKET, ExplosionType.classify("tacz:rpg_rocket", List.of()));
        assertEquals(ExplosionType.STANDARD, ExplosionType.classify("tacz:556x45", List.of()));
        assertEquals(ExplosionType.STANDARD, ExplosionType.classify(null, List.of()));
        assertEquals(ExplosionType.HEAVY, ExplosionType.classify("pack:Mortar_Shell", List.of()));
    }

    @Test
    void overridesWinOverKeywords() {
        List<String> overrides = List.of("tacz:40mm=HEAVY", "bad entry", "pack:x=NOPE");
        assertEquals(ExplosionType.HEAVY, ExplosionType.classify("tacz:40mm", overrides));
        assertEquals(ExplosionType.STANDARD, ExplosionType.classify("pack:x", overrides));
    }

    @Test
    void flagsRoundTripTypeSurfaceAndUnderwater() {
        for (ExplosionType type : ExplosionType.values()) {
            for (int surface = 0; surface <= 6; surface++) {
                for (boolean water : new boolean[] {false, true}) {
                    ExplosionFx fx = new ExplosionFx(0, 0, 0, 3f, ExplosionFx.pack(water, type, surface), 1);
                    assertEquals(type, fx.type());
                    assertEquals(surface, fx.surface());
                    assertEquals(water, fx.underwater());
                }
            }
        }
        // Reserved surface value 7 reads as air.
        assertEquals(0, new ExplosionFx(0, 0, 0, 3f, (byte) (7 << 4), 1).surface());
    }

    @Test
    void typesAreDistinct() {
        assertTrue(ExplosionType.GRENADE.look.shrapnel() > ExplosionType.ROCKET.look.shrapnel());
        assertTrue(ExplosionType.ROCKET.look.fireball() > ExplosionType.GRENADE.look.fireball());
        assertTrue(ExplosionType.GRENADE.gameplay.reachScale() > ExplosionType.ROCKET.gameplay.reachScale());
        assertTrue(ExplosionType.ROCKET.gameplay.knockbackScale() > ExplosionType.GRENADE.gameplay.knockbackScale());
        assertEquals(ExplosionType.STANDARD, ExplosionType.byId(99));
    }
}
