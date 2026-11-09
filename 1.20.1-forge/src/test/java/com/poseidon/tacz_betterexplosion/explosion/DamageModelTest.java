package com.poseidon.tacz_betterexplosion.explosion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DamageModelTest {

    private static final double D = 30.0;
    private static final double R = 5.0;
    private static final DamageModel.Params P = new DamageModel.Params(2.0, 0.25, 1.0, 1.0);

    private static double damageAt(double distance, double seen, DamageModel.Params p) {
        return DamageModel.damage(D, DamageModel.blastFactor(R, distance, seen, p), p);
    }

    @Test
    void damageAtCentreIsExactlyD() {
        assertEquals(30.0, DamageModel.damage(D, DamageModel.blastFactor(R, 0, 1, P), P), 1e-9);
    }

    @Test
    void matchesSpecTable() {
        assertEquals(21.6, damageAt(2, 1, P), 0.01);
        assertEquals(11.25, damageAt(5, 1, P), 0.01);
        assertEquals(3.6, damageAt(8, 1, P), 0.01);
        assertEquals(0.0, damageAt(10, 1, P), 0.0);
        assertEquals(0.0, damageAt(12, 1, P), 0.0);
    }

    @Test
    void strictlyDecreasingWithDistance() {
        double previous = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 99; i++) {
            double distance = i * 0.1;
            double current = damageAt(distance, 1, P);
            assertTrue(current < previous, "damage must fall at distance " + distance);
            previous = current;
        }
    }

    @Test
    void coverFloorKeepsDamage() {
        assertEquals(3.6, damageAt(2, 0, P), 0.01);
        DamageModel.Params noFloor = new DamageModel.Params(2.0, 0.0, 1.0, 1.0);
        assertEquals(0.0, damageAt(2, 0, noFloor), 0.0);
    }

    @Test
    void multipliersScale() {
        DamageModel.Params doubled = new DamageModel.Params(2.0, 0.25, 2.0, 1.0);
        assertEquals(2 * damageAt(2, 1, P), damageAt(2, 1, doubled), 1e-9);
        assertEquals(0.6, DamageModel.knockback(30, 5, 1.0, P), 1e-9);
    }

    @Test
    void badInputsGiveZero() {
        for (double radius : new double[] {0, -1, Double.NaN}) {
            double factor = DamageModel.blastFactor(radius, 1, 1, P);
            assertEquals(0.0, factor, 0.0, "blastFactor for radius " + radius);
            assertEquals(0.0, DamageModel.damage(D, factor, P), 0.0, "damage for radius " + radius);
            assertEquals(0.0, DamageModel.knockback(D, radius, 1.0, P), 0.0, "knockback for radius " + radius);
        }
        double goodFactor = DamageModel.blastFactor(R, 1, 1, P);
        assertTrue(goodFactor > 0.0);
        for (double damage : new double[] {0, -5, Double.NaN}) {
            double result = DamageModel.damage(damage, goodFactor, P);
            assertFalse(Double.isNaN(result));
            assertEquals(0.0, result, 0.0, "damage for D " + damage);
            assertEquals(0.0, DamageModel.knockback(damage, R, goodFactor, P), 0.0, "knockback for D " + damage);
        }
        assertEquals(0.0, DamageModel.blastFactor(R, Double.NaN, 1, P), 0.0);
        assertEquals(0.0, DamageModel.blastFactor(R, 1, Double.NaN, P), 0.0);
        assertEquals(0.0, DamageModel.damage(D, Double.NaN, P), 0.0);
    }

    @Test
    void zeroDirectionIsZeroVector() {
        assertArrayEquals(new double[] {0, 0, 0}, DamageModel.direction(0, 0, 0), 0.0);
        assertArrayEquals(new double[] {0, 0, 0}, DamageModel.direction(1e-9, 0, 0), 0.0);
        assertArrayEquals(new double[] {0.6, 0, 0.8}, DamageModel.direction(3, 0, 4), 1e-12);
    }
}
