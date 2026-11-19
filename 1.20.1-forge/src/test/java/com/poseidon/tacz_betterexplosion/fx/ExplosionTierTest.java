package com.poseidon.tacz_betterexplosion.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExplosionTierTest {

    @Test
    void boundariesFollowTable() {
        assertEquals(ExplosionTier.MINI, ExplosionTier.fromRadius(1.99f));
        assertEquals(ExplosionTier.SMALL, ExplosionTier.fromRadius(2.0f));
        assertEquals(ExplosionTier.SMALL, ExplosionTier.fromRadius(3.49f));
        assertEquals(ExplosionTier.MEDIUM, ExplosionTier.fromRadius(3.5f));
        assertEquals(ExplosionTier.MEDIUM, ExplosionTier.fromRadius(5.99f));
        assertEquals(ExplosionTier.LARGE, ExplosionTier.fromRadius(6.0f));
        assertEquals(ExplosionTier.LARGE, ExplosionTier.fromRadius(9.99f));
        assertEquals(ExplosionTier.HUGE, ExplosionTier.fromRadius(10.0f));
    }

    @Test
    void badRadiusIsSafe() {
        assertEquals(ExplosionTier.MINI, ExplosionTier.fromRadius(Float.NaN));
        assertEquals(ExplosionTier.MINI, ExplosionTier.fromRadius(-5f));
        assertEquals(ExplosionTier.HUGE, ExplosionTier.fromRadius(1e9f));
    }

    @Test
    void tableValues() {
        assertTier(ExplosionTier.MINI, 14, 96, 64);
        assertTier(ExplosionTier.SMALL, 34, 192, 128);
        assertTier(ExplosionTier.MEDIUM, 66, 288, 192);
        assertTier(ExplosionTier.LARGE, 108, 384, 256);
        assertTier(ExplosionTier.HUGE, 180, 512, 384);
    }

    private static void assertTier(ExplosionTier tier, int budget, int packetRange, int particleRange) {
        assertEquals(budget, tier.budget(), tier + " budget");
        assertEquals(packetRange, tier.packetRange(), tier + " packetRange");
        assertEquals(particleRange, tier.particleRange(), tier + " particleRange");
    }
}
