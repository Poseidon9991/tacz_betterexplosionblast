package com.poseidon.tacz_betterexplosion.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class ExplosionFxTest {

    @Test
    void roundTrip() {
        ExplosionFx original = new ExplosionFx(1.5, -64.25, 300.0, 4.0f, (byte) 1, 123456);
        ByteBuf buf = Unpooled.buffer();
        original.encode(buf);
        ExplosionFx decoded = ExplosionFx.decode(buf);
        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes());
    }
}
