package com.poseidon.tacz_betterexplosion.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import com.poseidon.tacz_betterexplosion.fx.ExplosionType;
import org.junit.jupiter.api.Test;

class ExplosionSoundsTest {

    @Test
    void gainStaysAudibleAcrossCloseMidAndFarBands() {
        for (ExplosionTier tier : ExplosionTier.values()) {
            double pRange = tier.particleRange();
            float close = ExplosionSounds.computeGain(10.0, tier, ExplosionType.STANDARD);
            float mid = ExplosionSounds.computeGain(0.55 * pRange, tier, ExplosionType.STANDARD);
            float far = ExplosionSounds.computeGain(0.90 * pRange, tier, ExplosionType.STANDARD);

            assertEquals(1.0F, close, 1.0e-5F);
            assertTrue(mid > 0.35F, tier + " mid gain too low: " + mid);
            assertTrue(far >= 0.10F, tier + " far gain culled: " + far);
            assertTrue(close > mid && mid > far);
        }
    }

    @Test
    void rocketAndHeavyStayAudibleOutTo384Blocks() {
        float at200 = ExplosionSounds.computeGain(200.0, ExplosionTier.MEDIUM, ExplosionType.ROCKET);
        float at350 = ExplosionSounds.computeGain(350.0, ExplosionTier.MEDIUM, ExplosionType.ROCKET);
        float beyond = ExplosionSounds.computeGain(500.0, ExplosionTier.MEDIUM, ExplosionType.ROCKET);

        assertTrue(at200 > 0.30F, "expected clear boom at 200m, got " + at200);
        assertTrue(at350 >= 0.10F, "expected horizon rumble at 350m, got " + at350);
        assertEquals(0.0F, beyond, 1.0e-5F);
    }

    @Test
    void enclosureDetectsRoomsAndCanyonsWhileSkippingOpenField() {
        assertEquals(0.0F, ExplosionSounds.computeEnclosure(false, 0, 8), 1.0e-5F);
        assertEquals(0.0F, ExplosionSounds.computeEnclosure(false, 3, 8), 1.0e-5F);
        assertEquals(0.0F, ExplosionSounds.computeEnclosure(true, 1, 8), 1.0e-5F);

        float room = ExplosionSounds.computeEnclosure(true, 6, 8);
        float cave = ExplosionSounds.computeEnclosure(true, 8, 8);
        float canyon = ExplosionSounds.computeEnclosure(false, 6, 8);

        assertTrue(room >= 0.85F, "expected strong room reverb, got " + room);
        assertEquals(1.0F, cave, 1.0e-5F);
        assertTrue(canyon >= 0.30F && canyon < room, "expected moderate canyon reverb, got " + canyon);
    }

    @Test
    void shrapnelWhizAppliesInNearMissBandAndScalesByArchetype() {
        // Point-blank (<2.5m) skips pass-by whiz
        assertEquals(0.0F, ExplosionSounds.shrapnelWhizGain(1.0, 4.0F, ExplosionType.GRENADE), 1.0e-5F);

        // Grenade (2.4x shrapnel) reaches further than Rocket (0.35x shrapnel) for the same radius
        float grenadeAt25 = ExplosionSounds.shrapnelWhizGain(25.0, 4.0F, ExplosionType.GRENADE);
        float rocketAt25 = ExplosionSounds.shrapnelWhizGain(25.0, 4.0F, ExplosionType.ROCKET);

        assertTrue(grenadeAt25 > 0.15F, "expected audible grenade whiz at 25m, got " + grenadeAt25);
        assertEquals(0.0F, rocketAt25, 1.0e-5F);
    }

    @Test
    void pitchClampsAboveSubBassCutoff() {
        assertEquals(0.55F, ExplosionSounds.clampPitch(0.25F), 1.0e-5F);
        assertEquals(1.0F, ExplosionSounds.clampPitch(1.0F), 1.0e-5F);
        assertEquals(1.80F, ExplosionSounds.clampPitch(2.5F), 1.0e-5F);
    }

    @Test
    void travelDelayScalesWithSpeedOfSoundAndCaps() {
        assertEquals(0, ExplosionSounds.travelDelayTicks(0.0));
        assertEquals(0, ExplosionSounds.travelDelayTicks(5.0));
        assertEquals(6, ExplosionSounds.travelDelayTicks(100.0));
        assertEquals(100, ExplosionSounds.travelDelayTicks(5000.0));
    }
}
