package com.poseidon.tacz_betterexplosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.poseidon.tacz_betterexplosion.config.ClientConfig;
import com.poseidon.tacz_betterexplosion.config.ServerConfig;
import com.poseidon.tacz_betterexplosion.fx.Quality;
import org.junit.jupiter.api.Test;

class ExplosionDefaultsTest {

    @Test
    void defaultsMatchSpec() {
        assertTrue(ExplosionDefaults.ENABLED);
        assertEquals(2.0, ExplosionDefaults.REACH_MULTIPLIER);
        assertEquals(0.5, ExplosionDefaults.COVER_FLOOR);
        assertEquals(1.0, ExplosionDefaults.DAMAGE_MULTIPLIER);
        assertEquals(1.0, ExplosionDefaults.KNOCKBACK_MULTIPLIER);
        assertEquals(128, ExplosionDefaults.MAX_TARGETS);

        assertEquals("HIGH", ExplosionDefaults.QUALITY);
        assertEquals(1200, ExplosionDefaults.MAX_LIVE_PARTICLES);
        assertTrue(ExplosionDefaults.ADAPTIVE_QUALITY);
        assertTrue(ExplosionDefaults.SCREEN_SHAKE);
        assertEquals(1.0, ExplosionDefaults.SHAKE_STRENGTH);
        assertTrue(ExplosionDefaults.SCREEN_FLASH);
        assertEquals(1.0, ExplosionDefaults.BLAST_SMOKE_MULTIPLIER);
        assertEquals(1.0, ExplosionDefaults.GROUND_SMOKE_MULTIPLIER);
        assertEquals(1.0, ExplosionDefaults.DEBRIS_MULTIPLIER);
        assertTrue(ExplosionDefaults.IMPROVED_ROCKET_FLIGHT);
        assertTrue(ExplosionDefaults.ROCKET_CHUNK_LOADING);
        assertTrue(ExplosionDefaults.ROCKET_SMOKE_TRAIL);
        assertTrue(ExplosionDefaults.ROCKET_FLIGHT_SOUND);
        assertEquals(1.0, ExplosionDefaults.SOUND_VOLUME);

        assertEquals(1.0, Quality.HIGH.density);
    }

    @Test
    void qualityDensities() {
        assertEquals(0.3, Quality.LOW.density);
        assertEquals(0.65, Quality.MEDIUM.density);
        assertEquals(1.0, Quality.HIGH.density);
        assertEquals(Quality.HIGH, Quality.valueOf(ExplosionDefaults.QUALITY));
    }

    @Test
    void gettersReturnDefaultsWhileSpecNotLoaded() {
        assertFalse(ServerConfig.SPEC.isLoaded());
        assertEquals(ExplosionDefaults.ENABLED, ServerConfig.enabled());
        assertEquals(ExplosionDefaults.REACH_MULTIPLIER, ServerConfig.reachMultiplier());
        assertEquals(ExplosionDefaults.COVER_FLOOR, ServerConfig.coverFloor());
        assertEquals(ExplosionDefaults.DAMAGE_MULTIPLIER, ServerConfig.damageMultiplier());
        assertEquals(ExplosionDefaults.KNOCKBACK_MULTIPLIER, ServerConfig.knockbackMultiplier());
        assertEquals(ExplosionDefaults.MAX_TARGETS, ServerConfig.maxTargets());
        assertEquals(ExplosionDefaults.IMPROVED_ROCKET_FLIGHT, ServerConfig.improvedRocketFlight());
        assertEquals(ExplosionDefaults.ROCKET_CHUNK_LOADING, ServerConfig.rocketChunkLoading());

        assertFalse(ClientConfig.SPEC.isLoaded());
        assertEquals(Quality.HIGH, ClientConfig.quality());
        assertEquals(ExplosionDefaults.MAX_LIVE_PARTICLES, ClientConfig.maxLiveParticles());
        assertEquals(ExplosionDefaults.ADAPTIVE_QUALITY, ClientConfig.adaptiveQuality());
        assertEquals(ExplosionDefaults.SCREEN_SHAKE, ClientConfig.screenShake());
        assertEquals(ExplosionDefaults.SHAKE_STRENGTH, ClientConfig.shakeStrength());
        assertEquals(ExplosionDefaults.SCREEN_FLASH, ClientConfig.screenFlash());
        assertEquals(ExplosionDefaults.BLAST_SMOKE_MULTIPLIER, ClientConfig.blastSmokeMultiplier());
        assertEquals(ExplosionDefaults.GROUND_SMOKE_MULTIPLIER, ClientConfig.groundSmokeMultiplier());
        assertEquals(ExplosionDefaults.DEBRIS_MULTIPLIER, ClientConfig.debrisMultiplier());
        assertEquals(ExplosionDefaults.ROCKET_SMOKE_TRAIL, ClientConfig.rocketSmokeTrail());
        assertEquals(ExplosionDefaults.ROCKET_FLIGHT_SOUND, ClientConfig.rocketFlightSound());
        assertEquals(ExplosionDefaults.SOUND_VOLUME, ClientConfig.soundVolume());
    }
}
