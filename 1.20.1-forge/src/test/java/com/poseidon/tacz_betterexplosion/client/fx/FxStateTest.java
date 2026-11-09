package com.poseidon.tacz_betterexplosion.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FxStateTest {

    @BeforeEach
    @AfterEach
    void clean() {
        FxState.reset();
    }

    @Test
    void addedAndRemovedUpdateLiveAndPeak() {
        FxState.particleAdded();
        FxState.particleAdded();
        FxState.particleAdded();
        assertEquals(3, FxState.liveParticles());
        assertEquals(3, FxState.peakLive());

        FxState.particleRemoved();
        assertEquals(2, FxState.liveParticles());
        assertEquals(3, FxState.peakLive());

        FxState.particleAdded();
        assertEquals(3, FxState.liveParticles());
        assertEquals(3, FxState.peakLive());

        FxState.particleAdded();
        assertEquals(4, FxState.liveParticles());
        assertEquals(4, FxState.peakLive());
    }

    @Test
    void removedNeverGoesBelowZero() {
        FxState.particleRemoved();
        assertEquals(0, FxState.liveParticles());
        FxState.particleAdded();
        FxState.particleRemoved();
        FxState.particleRemoved();
        FxState.particleRemoved();
        assertEquals(0, FxState.liveParticles());
        assertEquals(1, FxState.peakLive());
    }

    @Test
    void resetPeakSetsPeakToCurrentLive() {
        for (int i = 0; i < 5; i++) {
            FxState.particleAdded();
        }
        FxState.particleRemoved();
        FxState.particleRemoved();
        assertEquals(5, FxState.peakLive());

        FxState.resetPeak();

        assertEquals(3, FxState.peakLive());
        assertEquals(3, FxState.liveParticles());
    }

    @Test
    void resetZeroesBoth() {
        FxState.particleAdded();
        FxState.particleAdded();

        FxState.reset();

        assertEquals(0, FxState.liveParticles());
        assertEquals(0, FxState.peakLive());
    }
}
