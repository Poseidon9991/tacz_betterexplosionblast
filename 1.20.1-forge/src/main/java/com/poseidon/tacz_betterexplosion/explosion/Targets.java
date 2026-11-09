package com.poseidon.tacz_betterexplosion.explosion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Pure target selection. */
public final class Targets {

    private Targets() {
    }

    /**
     * The {@code max} items closest to the centre, in ascending distance order. Items at equal
     * distance keep their input order. The distance function is called once per item.
     */
    public static <T> List<T> nearest(List<T> items, ToDoubleFunction<T> distance, int max) {
        if (max <= 0 || items.isEmpty()) {
            return new ArrayList<>();
        }
        record Entry<E>(E item, double distance) {
        }
        List<Entry<T>> entries = new ArrayList<>(items.size());
        for (T item : items) {
            entries.add(new Entry<>(item, distance.applyAsDouble(item)));
        }
        entries.sort(Comparator.comparingDouble(Entry::distance));
        List<T> result = new ArrayList<>(Math.min(max, entries.size()));
        for (int i = 0; i < entries.size() && i < max; i++) {
            result.add(entries.get(i).item());
        }
        return result;
    }
}
