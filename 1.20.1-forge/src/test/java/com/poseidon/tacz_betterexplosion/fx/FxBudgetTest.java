package com.poseidon.tacz_betterexplosion.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.poseidon.tacz_betterexplosion.fx.FxBudget.Layer;
import com.poseidon.tacz_betterexplosion.fx.FxBudget.VanillaParticles;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class FxBudgetTest {

    private static final double EPS = 1e-9;

    @Test
    void burstFactorTable() {
        assertEquals(1.0, FxBudget.burstFactor(0), EPS);
        assertEquals(0.7, FxBudget.burstFactor(1), EPS);
        assertEquals(0.49, FxBudget.burstFactor(2), EPS);
        assertEquals(0.25, FxBudget.burstFactor(10), EPS);
        assertEquals(1.0, FxBudget.burstFactor(-3), EPS);
    }

    @Test
    void fpsZeroDoesNotThrottle() {
        assertEquals(1.0, FxBudget.fpsFactor(0), EPS);
        assertEquals(1.0, FxBudget.fpsFactor(-1), EPS);
        assertEquals(0.25, FxBudget.fpsFactor(19), EPS);
        assertEquals(0.5, FxBudget.fpsFactor(20), EPS);
        assertEquals(0.5, FxBudget.fpsFactor(29), EPS);
        assertEquals(1.0, FxBudget.fpsFactor(30), EPS);
        assertEquals(1.0, FxBudget.fpsFactor(60), EPS);
    }

    @Test
    void budgetNeverNegativeWhenOverCap() {
        assertEquals(0, mediumHigh(900, 700));
        assertEquals(0, mediumHigh(700, 700));
        assertEquals(10, mediumHigh(690, 700));
    }

    @Test
    void budgetHighMediumIsTableValue() {
        assertEquals(55, FxBudget.budget(ExplosionTier.MEDIUM, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, 0, 700));
        assertEquals(90, FxBudget.budget(ExplosionTier.LARGE, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, 0, 700));
        assertEquals(150, FxBudget.budget(ExplosionTier.HUGE, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, 0, 700));
    }

    @Test
    void budgetScalesWithFactors() {
        assertEquals(19, FxBudget.budget(ExplosionTier.MEDIUM, Quality.LOW, VanillaParticles.ALL, 1, 1, 1, 0, 700));
        assertEquals(
                28, FxBudget.budget(ExplosionTier.MEDIUM, Quality.HIGH, VanillaParticles.DECREASED, 1, 1, 1, 0, 700));
        assertEquals(0, FxBudget.budget(ExplosionTier.MEDIUM, Quality.HIGH, VanillaParticles.ALL, 1, 1, 0, 0, 700));
        assertEquals(100, FxBudget.budget(ExplosionTier.HUGE, Quality.ULTRA, VanillaParticles.ALL, 1, 1, 1, 0, 100));
    }

    @Test
    void lodBeyondRangeIsZero() {
        assertEquals(1.0, FxBudget.lodFactor(96.0, ExplosionTier.MEDIUM, false), EPS);
        assertEquals(0.0, FxBudget.lodFactor(96.01, ExplosionTier.MEDIUM, false), EPS);
        assertEquals(0.0, FxBudget.lodFactor(Double.NaN, ExplosionTier.MEDIUM, false), EPS);
    }

    @Test
    void behindCameraFarIsPointFour() {
        assertEquals(1.0, FxBudget.lodFactor(24.0, ExplosionTier.MEDIUM, true), EPS);
        assertEquals(0.4, FxBudget.lodFactor(24.01, ExplosionTier.MEDIUM, true), EPS);
        assertEquals(1.0, FxBudget.lodFactor(50.0, ExplosionTier.MEDIUM, false), EPS);
    }

    @Test
    void layerAllowedFarOnlyFlashAndStem() {
        for (Layer layer : Layer.values()) {
            assertTrue(FxBudget.layerAllowed(layer, 64.0), layer + " at 64.0");
            boolean farAllowed = layer == Layer.FLASH || layer == Layer.STEM;
            assertEquals(farAllowed, FxBudget.layerAllowed(layer, 64.01), layer + " at 64.01");
            assertFalse(FxBudget.layerAllowed(layer, Double.NaN), layer + " at NaN");
        }
    }

    @Test
    void splitSumsToBudgetForAllBudgets() {
        for (int budget = 0; budget <= 200; budget++) {
            EnumMap<Layer, Integer> map = FxBudget.split(budget);
            assertEquals(Layer.values().length, map.size(), "keys for budget " + budget);
            int sum = 0;
            for (Layer layer : Layer.values()) {
                Integer value = map.get(layer);
                assertTrue(value != null && value >= 0, layer + " for budget " + budget);
                sum += value;
            }
            assertEquals(budget, sum, "sum for budget " + budget);
        }
    }

    @Test
    void splitFixedLayersPriority() {
        EnumMap<Layer, Integer> one = FxBudget.split(1);
        assertEquals(1, one.get(Layer.FLASH));
        assertEquals(0, one.get(Layer.RING));

        EnumMap<Layer, Integer> two = FxBudget.split(2);
        assertEquals(2, two.get(Layer.FLASH));
        assertEquals(0, two.get(Layer.RING));

        EnumMap<Layer, Integer> three = FxBudget.split(3);
        assertEquals(2, three.get(Layer.FLASH));
        assertEquals(1, three.get(Layer.RING));
        assertEquals(0, three.get(Layer.FIREBALL));
        assertEquals(0, three.get(Layer.STEM));
        assertEquals(0, three.get(Layer.DUST));
        assertEquals(0, three.get(Layer.DEBRIS));
        assertEquals(0, three.get(Layer.SPARKS));

        for (int budget = 3; budget <= 200; budget++) {
            EnumMap<Layer, Integer> map = FxBudget.split(budget);
            assertEquals(2, map.get(Layer.FLASH), "FLASH for budget " + budget);
            assertEquals(1, map.get(Layer.RING), "RING for budget " + budget);
        }
    }

    @Test
    void splitMediumShape() {
        EnumMap<Layer, Integer> map = FxBudget.split(55);
        assertEquals(2, map.get(Layer.FLASH));
        assertEquals(1, map.get(Layer.RING));
        assertEquals(16, map.get(Layer.FIREBALL));
        assertEquals(16, map.get(Layer.STEM));
        assertEquals(6, map.get(Layer.DUST));
        assertEquals(7, map.get(Layer.DEBRIS));
        assertEquals(7, map.get(Layer.SPARKS));
    }

    @Test
    void splitNegativeBudgetIsZero() {
        EnumMap<Layer, Integer> map = FxBudget.split(-5);
        assertEquals(Layer.values().length, map.size());
        for (Layer layer : Layer.values()) {
            assertEquals(0, map.get(layer), layer.toString());
        }
    }

    private static int mediumHigh(int liveNow, int maxLive) {
        return FxBudget.budget(
                ExplosionTier.MEDIUM, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, liveNow, maxLive);
    }
}
