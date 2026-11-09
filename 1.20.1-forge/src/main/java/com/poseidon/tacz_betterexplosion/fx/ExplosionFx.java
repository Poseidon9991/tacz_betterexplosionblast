package com.poseidon.tacz_betterexplosion.fx;

import io.netty.buffer.ByteBuf;

/** Server-to-client explosion effect payload and its byte codec. */
public record ExplosionFx(double x, double y, double z, float radius, byte flags, int seed) {

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
