package com.poseidon.tacz_betterexplosion.explosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TargetsTest {

    private static final List<Double> ITEMS = List.of(4.0, 1.0, 5.0, 2.0, 3.0);

    @Test
    void nearestKeepsClosestSorted() {
        assertEquals(List.of(1.0, 2.0, 3.0), Targets.nearest(ITEMS, Double::doubleValue, 3));
        assertTrue(Targets.nearest(ITEMS, Double::doubleValue, 0).isEmpty());
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0), Targets.nearest(ITEMS, Double::doubleValue, 99));
    }

    @Test
    void nearestIsStableForEqualDistances() {
        List<String> items = List.of("bb", "a", "cc", "d", "ee");
        assertEquals(List.of("a", "d", "bb"), Targets.nearest(items, s -> s.length(), 3));
    }
}
