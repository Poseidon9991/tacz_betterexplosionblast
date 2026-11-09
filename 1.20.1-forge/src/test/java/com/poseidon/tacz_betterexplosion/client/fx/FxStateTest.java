package com.poseidon.tacz_betterexplosion.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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

    @Test
    void recentExplosionsCountsLastTenTicksAndRecordsCall() {
        assertEquals(0, FxState.recentExplosions(100));
        assertEquals(1, FxState.recentExplosions(100));
        // t=100 entries are more than 10 ticks old at t=111 and get pruned.
        assertEquals(0, FxState.recentExplosions(111));
        assertEquals(1, FxState.recentExplosions(112));
    }

    @Test
    void recentExplosionsKeepsEntriesInsideWindow() {
        FxState.recentExplosions(100);
        FxState.recentExplosions(105);
        // t=100 stays at t=110 (exactly 10 ticks), t=105 too: 2 recorded before this call.
        assertEquals(2, FxState.recentExplosions(110));
    }

    @Test
    void scheduledRunsAfterTheGivenDelayInOrder() {
        List<String> ran = new ArrayList<>();
        FxState.schedule(2, () -> ran.add("a"));
        FxState.schedule(2, () -> ran.add("b"));

        FxState.tick();
        assertTrue(ran.isEmpty());

        FxState.tick();
        assertEquals(List.of("a", "b"), ran);

        FxState.tick();
        assertEquals(2, ran.size());
    }

    @Test
    void scheduledZeroDelayRunsOnNextTick() {
        List<String> ran = new ArrayList<>();
        FxState.schedule(0, () -> ran.add("x"));

        FxState.tick();

        assertEquals(List.of("x"), ran);
    }

    @Test
    void runnableScheduledFromARunnableWaitsForNextTick() {
        List<String> ran = new ArrayList<>();
        FxState.schedule(0, () -> {
            ran.add("outer");
            FxState.schedule(0, () -> ran.add("inner"));
        });

        FxState.tick();
        assertEquals(List.of("outer"), ran);

        FxState.tick();
        assertEquals(List.of("outer", "inner"), ran);
    }

    @Test
    void resetClearsScheduleDequeLiveAndPeak() {
        List<String> ran = new ArrayList<>();
        FxState.schedule(0, () -> ran.add("x"));
        FxState.recentExplosions(50);
        FxState.particleAdded();
        FxState.particleAdded();

        FxState.reset();
        FxState.tick();

        assertTrue(ran.isEmpty());
        assertEquals(0, FxState.recentExplosions(50));
        assertEquals(0, FxState.liveParticles());
        assertEquals(0, FxState.peakLive());
        assertEquals(0, FxState.spawnedTotal());
    }

    @Test
    void addSpawnedAccumulatesAndResetStatsZeroesTotalAndPeak() {
        FxState.addSpawned(5);
        FxState.addSpawned(3);
        FxState.particleAdded();
        FxState.particleAdded();
        FxState.particleAdded();
        FxState.particleRemoved();

        assertEquals(8, FxState.spawnedTotal());

        FxState.resetStats();

        assertEquals(0, FxState.spawnedTotal());
        assertEquals(2, FxState.peakLive());
        assertEquals(2, FxState.liveParticles());
    }
}
