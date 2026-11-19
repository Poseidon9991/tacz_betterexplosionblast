package com.poseidon.tacz_betterexplosion.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EarMuffleTest {

    @Test
    void duckEnvelopeAttacksFastAndReleasesToFull() {
        int total = 60;
        float duck = 0.12F;

        // Attack reaches full duck depth within ATTACK_TICKS
        assertTrue(EarMuffle.duckFactor(0, total, duck) > duck);
        assertEquals(duck, EarMuffle.duckFactor(EarMuffle.ATTACK_TICKS - 1, total, duck), 1.0e-5F);

        // Release rises monotonically back to full volume
        float prev = EarMuffle.duckFactor(EarMuffle.ATTACK_TICKS, total, duck);
        for (int t = EarMuffle.ATTACK_TICKS + 1; t <= total; t++) {
            float f = EarMuffle.duckFactor(t, total, duck);
            assertTrue(f >= prev, "duck factor regressed at t=" + t);
            prev = f;
        }
        assertEquals(1.0F, EarMuffle.duckFactor(total, total, duck), 1.0e-5F);
        assertEquals(1.0F, EarMuffle.duckFactor(0, 0, duck), 1.0e-5F);
    }
}
