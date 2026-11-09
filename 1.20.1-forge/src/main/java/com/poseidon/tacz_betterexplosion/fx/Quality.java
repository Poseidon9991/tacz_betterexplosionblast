package com.poseidon.tacz_betterexplosion.fx;

/** Client particle quality setting. The density scales a tier's particle budget. */
public enum Quality {
    LOW(0.35),
    MEDIUM(0.7),
    HIGH(1.0),
    ULTRA(1.5);

    /** Multiplier applied to the tier's particle budget. */
    public final double density;

    Quality(double density) {
        this.density = density;
    }
}
