package com.poseidon.tacz_betterexplosion.fx;

import io.netty.buffer.ByteBuf;

/** Server-to-client explosion effect payload and its byte codec. */
public record ExplosionFx(double x, double y, double z, float radius, byte flags, int seed) {

    /** {@code flags} bit 0: the blast centre is in water (underwater effect variant). */
    public static final byte FLAG_UNDERWATER = 1;

    /** Whether the {@link #FLAG_UNDERWATER} bit is set; other bits are ignored. */
    public boolean underwater() {
        return (flags & FLAG_UNDERWATER) != 0;
    }

    /** Bits 1-3 of {@code flags}: the {@link ExplosionType} id. */
    public ExplosionType type() {
        return ExplosionType.byId((flags >> 1) & 7);
    }

    /**
     * Bits 4-6 of {@code flags}: 0 when the blast is in the air, else 1 + the 3D data value of the hit face's
     * outward normal (1 down, 2 up, 3 north, 4 south, 5 west, 6 east). Anything above 6 reads as air.
     */
    public int surface() {
        int s = (flags >> 4) & 7;
        return s > 6 ? 0 : s;
    }

    /** Builds the {@code flags} byte. */
    public static byte pack(boolean underwater, ExplosionType type, int surface) {
        int bits = underwater ? FLAG_UNDERWATER : 0;
        bits |= (type.id & 7) << 1;
        bits |= (surface >= 0 && surface <= 6 ? surface : 0) << 4;
        return (byte) bits;
    }

    /** The same blast with another radius (used for the per-type visual size). */
    public ExplosionFx withRadius(float newRadius) {
        return new ExplosionFx(x, y, z, newRadius, flags, seed);
    }

    /** Writes x, y, z, radius, flags, seed in that order. */
    public void encode(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(radius);
        buf.writeByte(flags);
        buf.writeInt(seed);
    }

    /** Reads the fields in the order {@link #encode} wrote them. */
    public static ExplosionFx decode(ByteBuf buf) {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float radius = buf.readFloat();
        byte flags = buf.readByte();
        int seed = buf.readInt();
        return new ExplosionFx(x, y, z, radius, flags, seed);
    }
}
