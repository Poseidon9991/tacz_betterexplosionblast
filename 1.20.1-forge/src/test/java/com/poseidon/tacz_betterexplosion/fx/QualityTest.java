package com.poseidon.tacz_betterexplosion.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.poseidon.tacz_betterexplosion.fx.FxBudget.Layer;
import org.junit.jupiter.api.Test;

class QualityTest {

    private static final double EPS = 1e-9;

    @Test
    void exactlyThreeLevelsLowMediumHigh() {
        assertEquals(3, Quality.values().length);
        assertEquals(Quality.LOW, Quality.values()[0]);
        assertEquals(Quality.MEDIUM, Quality.values()[1]);
        assertEquals(Quality.HIGH, Quality.values()[2]);
    }

    @Test
    void densitiesArePointThreePointSixFiveOne() {
        assertEquals(0.3, Quality.LOW.density, EPS);
        assertEquals(0.65, Quality.MEDIUM.density, EPS);
        assertEquals(1.0, Quality.HIGH.density, EPS);
    }

    @Test
    void lowKeepsFlashFireballStemDust() {
        assertTrue(Quality.LOW.allows(Layer.FLASH));
        assertTrue(Quality.LOW.allows(Layer.FIREBALL));
        assertTrue(Quality.LOW.allows(Layer.STEM));
        assertTrue(Quality.LOW.allows(Layer.DUST));
        assertFalse(Quality.LOW.allows(Layer.DEBRIS));
        assertFalse(Quality.LOW.allows(Layer.SPARKS));
        assertFalse(Quality.LOW.allows(Layer.SHRAPNEL));
    }

    @Test
    void mediumAddsDebrisAndShrapnel() {
        assertTrue(Quality.MEDIUM.allows(Layer.FLASH));
        assertTrue(Quality.MEDIUM.allows(Layer.FIREBALL));
        assertTrue(Quality.MEDIUM.allows(Layer.STEM));
        assertTrue(Quality.MEDIUM.allows(Layer.DUST));
        assertTrue(Quality.MEDIUM.allows(Layer.DEBRIS));
        assertFalse(Quality.MEDIUM.allows(Layer.SPARKS));
        assertTrue(Quality.MEDIUM.allows(Layer.SHRAPNEL));
    }

    @Test
    void highAllowsEveryLayer() {
        for (Layer layer : Layer.values()) {
            assertTrue(Quality.HIGH.allows(layer), layer.toString());
        }
    }

    @Test
    void blockedSetIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> Quality.HIGH.blocked.add(Layer.FLASH));
        assertThrows(UnsupportedOperationException.class, () -> Quality.LOW.blocked.remove(Layer.DEBRIS));
    }
}
