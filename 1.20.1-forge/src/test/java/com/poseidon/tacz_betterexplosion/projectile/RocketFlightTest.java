package com.poseidon.tacz_betterexplosion.projectile;

import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import com.poseidon.tacz_betterexplosion.fx.ExplosionType;
import com.poseidon.tacz_betterexplosion.fx.FxBudget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RocketFlightTest {

    @Test
    void testRocketClassification() {
        assertEquals(ExplosionType.ROCKET, ExplosionType.classify("tacz:rpg_rocket", List.of()));
        assertEquals(ExplosionType.ROCKET, ExplosionType.classify("superbwarfare:rpg_rocket_standard", List.of()));
        assertEquals(ExplosionType.ROCKET, ExplosionType.classify("custom:pg7v", List.of()));
        assertEquals(ExplosionType.ROCKET, ExplosionType.classify("pack:stinger_missile", List.of()));
        assertEquals(ExplosionType.ROCKET, ExplosionType.classify("gunpack:tandem_warhead", List.of()));
    }

    @Test
    void testChunkTicketRefreshDeduplication() {
        // First tick always submits tickets
        assertTrue(RocketChunkManager.shouldRefreshTickets(100L, Long.MIN_VALUE, 1, -1));
        // Same chunk within refresh interval skips duplicate ticket submission
        assertFalse(RocketChunkManager.shouldRefreshTickets(100L, 100L, 5, 1));
        assertFalse(RocketChunkManager.shouldRefreshTickets(100L, 100L, 15, 1));
        // Crossing into a new chunk refreshes immediately
        assertTrue(RocketChunkManager.shouldRefreshTickets(101L, 100L, 3, 1));
        // Staying in the same chunk refreshes at TICKET_REFRESH_INTERVAL (well before 40-tick TTL)
        assertTrue(RocketChunkManager.shouldRefreshTickets(100L, 100L, 16, 1));
    }

    @Test
    void testFarLodExplosionVisibility() {
        assertEquals(1.0, FxBudget.lodFactor(128.0, ExplosionTier.SMALL, false), 1e-6);
        assertEquals(0.25, FxBudget.lodFactor(150.0, ExplosionTier.SMALL, false), 1e-6);
        assertTrue(FxBudget.layerAllowed(FxBudget.Layer.FLASH, 150.0));
        assertTrue(FxBudget.layerAllowed(FxBudget.Layer.STEM, 150.0));
        assertFalse(FxBudget.layerAllowed(FxBudget.Layer.SPARKS, 150.0));
        assertFalse(FxBudget.layerAllowed(FxBudget.Layer.DEBRIS, 150.0));

        assertEquals(0.0, FxBudget.lodFactor(192.1, ExplosionTier.SMALL, false), 1e-6);

        assertEquals(1.0, FxBudget.lodFactor(192.0, ExplosionTier.MEDIUM, false), 1e-6);
        assertEquals(0.25, FxBudget.lodFactor(250.0, ExplosionTier.MEDIUM, false), 1e-6);
        assertEquals(0.0, FxBudget.lodFactor(288.1, ExplosionTier.MEDIUM, false), 1e-6);
    }
}
