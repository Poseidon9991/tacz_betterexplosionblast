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

    @Test
    void wireLayoutIsPinned() {
        ByteBuf buf = Unpooled.buffer();
        new ExplosionFx(1.5, -64.25, 300.0, 4.0f, (byte) 1, 123456).encode(buf);
        // 3 doubles (24) + float (4) + flags byte (1) + seed int (4) = 33, in that order, big endian.
        assertEquals(33, buf.writerIndex());
        assertEquals(1.5, buf.getDouble(0));
        assertEquals(-64.25, buf.getDouble(8));
        assertEquals(300.0, buf.getDouble(16));
        assertEquals(4.0f, buf.getFloat(24));
        assertEquals((byte) 1, buf.getByte(28));
        assertEquals(123456, buf.getInt(29));
    }

    @Test
    void nonFiniteValuesKeepTheirBitPatterns() {
        double[] doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        float[] floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i < doubles.length; i++) {
            ExplosionFx original = new ExplosionFx(doubles[i], doubles[(i + 1) % 3], doubles[(i + 2) % 3],
                    floats[i], (byte) 0, 7);
            ByteBuf buf = Unpooled.buffer();
            original.encode(buf);
            assertEquals(33, buf.writerIndex());
            ExplosionFx decoded = ExplosionFx.decode(buf);
            assertEquals(0, buf.readableBytes());
            assertEquals(Double.doubleToRawLongBits(original.x()), Double.doubleToRawLongBits(decoded.x()));
            assertEquals(Double.doubleToRawLongBits(original.y()), Double.doubleToRawLongBits(decoded.y()));
            assertEquals(Double.doubleToRawLongBits(original.z()), Double.doubleToRawLongBits(decoded.z()));
            assertEquals(Float.floatToRawIntBits(original.radius()), Float.floatToRawIntBits(decoded.radius()));
        }
    }

    @Test
    void negativeFlagsByteSurvives() {
        ExplosionFx original = new ExplosionFx(0.0, 0.0, 0.0, 1.0f, (byte) -1, -123456);
        ByteBuf buf = Unpooled.buffer();
        original.encode(buf);
        assertEquals((byte) -1, buf.getByte(28));
        ExplosionFx decoded = ExplosionFx.decode(buf);
        assertEquals((byte) -1, decoded.flags());
        assertEquals(-123456, decoded.seed());
        assertEquals(original, decoded);
    }
}
