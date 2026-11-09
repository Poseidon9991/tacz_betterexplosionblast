package com.poseidon.tacz_betterexplosion.fx;

import java.util.Set;

/**
 * Client particle quality setting. The density scales a tier's particle budget; {@link #blocked}
 * layers are skipped entirely and their budget share is not redistributed. LOW keeps flash,
 * fireball, stem and dust; MEDIUM adds shrapnel; HIGH spawns everything.
 */
public enum Quality {
    LOW(0.3, Set.of(FxBudget.Layer.DEBRIS, FxBudget.Layer.SPARKS, FxBudget.Layer.SHRAPNEL)),
    MEDIUM(0.65, Set.of(FxBudget.Layer.DEBRIS, FxBudget.Layer.SPARKS)),
    HIGH(1.0, Set.of());

    /** Multiplier applied to the tier's particle budget. */
    public final double density;
    /** Layers this quality never spawns. */
    public final Set<FxBudget.Layer> blocked;

    Quality(double density, Set<FxBudget.Layer> blocked) {
        this.density = density;
        this.blocked = blocked;
    }

    /** Whether this quality spawns {@code layer}. */
    public boolean allows(FxBudget.Layer layer) {
        return !blocked.contains(layer);
    }
}
