package com.poseidon.tacz_betterexplosion.explosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
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

    @Test
    void nearestIsStableForSignedZeroDistances() {
        List<String> items = List.of("pos", "neg", "pos2", "neg2");
        // -0.0 and 0.0 are the same distance: input order must win.
        assertEquals(List.of("pos", "neg", "pos2", "neg2"),
                Targets.nearest(items, s -> s.startsWith("neg") ? -0.0 : 0.0, 4));
        assertEquals(List.of("pos", "neg"),
                Targets.nearest(items, s -> s.startsWith("neg") ? -0.0 : 0.0, 2));
    }

    @Test
    void nearestExcludesNaNDistances() {
        List<String> items = List.of("nan1", "far", "nan2", "near", "nan3");
        ToDoubleFunction<String> distance = s -> s.startsWith("nan") ? Double.NaN : s.equals("near") ? 1.0 : 9.0;
        // NaN items must not fill free slots, even when max exceeds the valid count.
        assertEquals(List.of("near", "far"), Targets.nearest(items, distance, 5));
        assertEquals(List.of("near"), Targets.nearest(items, distance, 1));
        assertTrue(Targets.nearest(List.of(Double.NaN, Double.NaN), Double::doubleValue, 3).isEmpty());
    }

    @Test
    void nearestKeepsInfiniteDistancesButSortsThemLast() {
        List<Double> items = List.of(Double.POSITIVE_INFINITY, 2.0, 1.0);
        assertEquals(List.of(1.0, 2.0, Double.POSITIVE_INFINITY), Targets.nearest(items, Double::doubleValue, 3));
    }

    @Test
    void rankedCarriesTheDistanceAndComputesItOncePerItem() {
        List<String> items = List.of("c", "nan", "a", "b");
        Map<String, Integer> calls = new HashMap<>();
        ToDoubleFunction<String> distance = s -> {
            calls.merge(s, 1, Integer::sum);
            return switch (s) {
                case "a" -> 1.5;
                case "b" -> 2.5;
                case "c" -> 3.5;
                default -> Double.NaN;
            };
        };
        List<Targets.Ranked<String>> ranked = Targets.rankedNearest(items, distance, 2);
        assertEquals(List.of(new Targets.Ranked<>("a", 1.5), new Targets.Ranked<>("b", 2.5)), ranked);
        assertEquals(Map.of("a", 1, "b", 1, "c", 1, "nan", 1), calls);
    }

    @Test
    void rankedAppliesTheSameRulesAsNearest() {
        assertTrue(Targets.rankedNearest(ITEMS, Double::doubleValue, 0).isEmpty());
        assertTrue(Targets.rankedNearest(ITEMS, Double::doubleValue, -3).isEmpty());
        assertTrue(Targets.rankedNearest(List.of(Double.NaN), Double::doubleValue, 3).isEmpty());
        assertEquals(List.of("a", "d", "bb"),
                Targets.rankedNearest(List.of("bb", "a", "cc", "d", "ee"), s -> s.length(), 3).stream()
                        .map(Targets.Ranked::item).toList());
    }

    @Test
    void nearestWithNonPositiveMaxIsEmpty() {
        assertTrue(Targets.nearest(ITEMS, Double::doubleValue, 0).isEmpty());
        assertTrue(Targets.nearest(ITEMS, Double::doubleValue, -1).isEmpty());
        assertTrue(Targets.nearest(ITEMS, Double::doubleValue, Integer.MIN_VALUE).isEmpty());
        assertTrue(Targets.nearest(List.<Double>of(), Double::doubleValue, 3).isEmpty());
    }
}
