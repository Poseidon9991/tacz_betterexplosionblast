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
        assertEquals(28.8, damageAt(2, 1, P), 0.01);
        assertEquals(22.5, damageAt(5, 1, P), 0.01);
        assertEquals(10.8, damageAt(8, 1, P), 0.01);
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
        assertEquals(10.8, damageAt(2, 0, P), 0.01);
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
    void negativeOrNaNMultipliersGiveZero() {
        double factor = DamageModel.blastFactor(R, 1, 1, P);
        assertTrue(factor > 0.0);
        for (double bad : new double[] {-1.0, -0.5, Double.NaN, Double.NEGATIVE_INFINITY}) {
            DamageModel.Params badDamage = new DamageModel.Params(2.0, 0.25, bad, 1.0);
            double damage = DamageModel.damage(D, factor, badDamage);
            assertEquals(0.0, damage, 0.0, "damage for damageMultiplier " + bad);
            assertTrue(damage >= 0.0, "damage must not be negative for damageMultiplier " + bad);

            DamageModel.Params badKnockback = new DamageModel.Params(2.0, 0.25, 1.0, bad);
            double knockback = DamageModel.knockback(D, R, factor, badKnockback);
            assertEquals(0.0, knockback, 0.0, "knockback for knockbackMultiplier " + bad);
            assertTrue(knockback >= 0.0, "knockback must not be negative for knockbackMultiplier " + bad);
        }
        // A bad multiplier on one axis leaves the other untouched.
        DamageModel.Params badDamageOnly = new DamageModel.Params(2.0, 0.25, -1.0, 1.0);
        assertEquals(0.6, DamageModel.knockback(30, 5, 1.0, badDamageOnly), 1e-9);
        DamageModel.Params badKnockbackOnly = new DamageModel.Params(2.0, 0.25, 1.0, Double.NaN);
        assertEquals(30.0, DamageModel.damage(D, 1.0, badKnockbackOnly), 1e-9);
    }

    @Test
    void zeroDirectionIsZeroVector() {
        assertArrayEquals(new double[] {0, 0, 0}, DamageModel.direction(0, 0, 0), 0.0);
        assertArrayEquals(new double[] {0, 0, 0}, DamageModel.direction(1e-9, 0, 0), 0.0);
        assertArrayEquals(new double[] {0.6, 0, 0.8}, DamageModel.direction(3, 0, 4), 1e-12);
    }
}
