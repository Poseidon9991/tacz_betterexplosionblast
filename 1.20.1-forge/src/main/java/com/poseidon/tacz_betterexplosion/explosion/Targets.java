package com.poseidon.tacz_betterexplosion.explosion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Pure target selection. */
public final class Targets {

    private Targets() {
    }

    /** An item together with the distance it was ranked by. */
    public record Ranked<T>(T item, double distance) {
    }

    /**
     * The {@code max} items closest to the centre, in ascending distance order; see
     * {@link #rankedNearest} for the rules.
     */
    public static <T> List<T> nearest(List<T> items, ToDoubleFunction<T> distance, int max) {
        List<Ranked<T>> ranked = rankedNearest(items, distance, max);
        List<T> result = new ArrayList<>(ranked.size());
        for (Ranked<T> entry : ranked) {
            result.add(entry.item());
        }
        return result;
    }

    /**
     * Like {@link #nearest} but keeps each item's distance, so callers need not recompute it.
     * The distance function is called once per item. Items whose distance is NaN are excluded
     * (they have no defined rank and must not fill free slots). A {@code max} of 0 or less
     * gives an empty list. Items at equal distance (0.0 and -0.0 count as equal) keep their
     * input order.
     */
    public static <T> List<Ranked<T>> rankedNearest(List<T> items, ToDoubleFunction<T> distance, int max) {
        if (max <= 0 || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<Ranked<T>> entries = new ArrayList<>(items.size());
        for (T item : items) {
            double d = distance.applyAsDouble(item);
            if (!Double.isNaN(d)) {
                entries.add(new Ranked<>(item, d));
            }
        }
        // Adding 0.0 turns -0.0 into 0.0 so the two tie; List.sort is stable.
        entries.sort(Comparator.comparingDouble(entry -> entry.distance() + 0.0));
        return entries.size() > max ? new ArrayList<>(entries.subList(0, max)) : entries;
    }
}
