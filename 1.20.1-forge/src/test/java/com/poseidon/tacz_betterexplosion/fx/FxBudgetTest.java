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
        assertEquals(66, FxBudget.budget(ExplosionTier.MEDIUM, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, 0, 700));
        assertEquals(108, FxBudget.budget(ExplosionTier.LARGE, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, 0, 700));
        assertEquals(180, FxBudget.budget(ExplosionTier.HUGE, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, 0, 700));
    }

    @Test
    void budgetScalesWithFactors() {
        assertEquals(20, FxBudget.budget(ExplosionTier.MEDIUM, Quality.LOW, VanillaParticles.ALL, 1, 1, 1, 0, 700));
        assertEquals(
                33, FxBudget.budget(ExplosionTier.MEDIUM, Quality.HIGH, VanillaParticles.DECREASED, 1, 1, 1, 0, 700));
        assertEquals(0, FxBudget.budget(ExplosionTier.MEDIUM, Quality.HIGH, VanillaParticles.ALL, 1, 1, 0, 0, 700));
        assertEquals(100, FxBudget.budget(ExplosionTier.HUGE, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, 0, 100));
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
    void splitSmallBudgetsClampFlashFirst() {
        assertShape(FxBudget.split(1), 1, 0, 0, 0, 0, 0, 0);
        assertShape(FxBudget.split(2), 2, 0, 0, 0, 0, 0, 0);
        // remainder 1: every floor is 0, the one leftover unit goes to STEM
        assertShape(FxBudget.split(3), 2, 0, 1, 0, 0, 0, 0);
    }

    @Test
    void splitFlashIsTwoOnceBudgetAllowsIt() {
        for (int budget = 2; budget <= 200; budget++) {
            assertEquals(2, FxBudget.split(budget).get(Layer.FLASH), "FLASH for budget " + budget);
        }
    }

    @Test
    void splitMediumShape() {
        // remainder 64: floors 10/23/10/3/3/12 (sum 61), leftover 3 to STEM, FIREBALL, SHRAPNEL
        assertShape(FxBudget.split(66), 2, 11, 24, 10, 3, 3, 13);
    }

    @Test
    void splitLeftoverOrderIsStemFireballShrapnelDebrisSparksDust() {
        // remainder 7: floors FIREBALL 1, STEM 2, DUST 1, SHRAPNEL 1 (sum 5), leftover 2 to STEM, FIREBALL
        assertShape(FxBudget.split(9), 2, 2, 3, 1, 0, 0, 1);
        // remainder 5 -> floors STEM 1, SHRAPNEL 1 (sum 2), leftover 3 to STEM, FIREBALL, SHRAPNEL
        assertShape(FxBudget.split(7), 2, 1, 2, 0, 0, 0, 2);
        // remainder 18 (budget 20): floors 2/6/2/1/1/3 (sum 15), leftover 3 to STEM, FIREBALL, SHRAPNEL
        assertShape(FxBudget.split(20), 2, 3, 7, 2, 1, 1, 4);
        // remainder 49 (budget 51): floors 7/17/7/2/2/9 (sum 44), leftover 5 to STEM, FIREBALL, SHRAPNEL,
        // DEBRIS, SPARKS (DUST gets none)
        assertShape(FxBudget.split(51), 2, 8, 18, 7, 3, 3, 10);
    }

    @Test
    void layerEnumHasSevenLayersEndingInShrapnel() {
        for (Layer layer : Layer.values()) {
            assertFalse(layer.name().equals("RING"), "no ring layer");
        }
        assertEquals(7, Layer.values().length);
        assertEquals(Layer.SHRAPNEL, Layer.values()[Layer.values().length - 1]);
    }

    @Test
    void splitNegativeBudgetIsZero() {
        EnumMap<Layer, Integer> map = FxBudget.split(-5);
        assertEquals(Layer.values().length, map.size());
        for (Layer layer : Layer.values()) {
            assertEquals(0, map.get(layer), layer.toString());
        }
    }

    private static void assertShape(
            EnumMap<Layer, Integer> map,
            int flash, int fireball, int stem, int dust, int debris, int sparks, int shrapnel) {
        assertEquals(7, map.size());
        assertEquals(flash, map.get(Layer.FLASH), "FLASH");
        assertEquals(fireball, map.get(Layer.FIREBALL), "FIREBALL");
        assertEquals(stem, map.get(Layer.STEM), "STEM");
        assertEquals(dust, map.get(Layer.DUST), "DUST");
        assertEquals(debris, map.get(Layer.DEBRIS), "DEBRIS");
        assertEquals(sparks, map.get(Layer.SPARKS), "SPARKS");
        assertEquals(shrapnel, map.get(Layer.SHRAPNEL), "SHRAPNEL");
    }

    private static int mediumHigh(int liveNow, int maxLive) {
        return FxBudget.budget(
                ExplosionTier.MEDIUM, Quality.HIGH, VanillaParticles.ALL, 1, 1, 1, liveNow, maxLive);
    }
}
