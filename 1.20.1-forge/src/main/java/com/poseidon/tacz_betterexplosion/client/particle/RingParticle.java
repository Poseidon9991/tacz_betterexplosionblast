package com.poseidon.tacz_betterexplosion.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Expanding shock ring: additive, full-bright, lifetime 10 ticks, one flat horizontal quad centred on the
 * particle position (lifted by 0.03 blocks against z-fighting). Half-extent {@code quadSize * easeOutCubic(t)}
 * (so the quad reaches {@code quadSize} blocks; the ring in the texture sits at 0.9 of the half-extent),
 * alpha {@code 0.9 * (1 - t)^1.5}, colour (1.0, 0.9, 0.75), {@code t = age / lifetime}. Ignores the velocity.
 * Both windings are written so the ring is visible from above and below; GL cull state is not touched
 * (with culling on only the side facing the camera is drawn, so the ring is not drawn twice).
 */
public final class RingParticle extends FxParticle {

    private static final int LIFETIME = 10;
    private static final float LIFT = 0.03F;
    private static final float MAX_ALPHA = 0.9F;

    RingParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        this.lifetime = LIFETIME;
        setParticleSpeed(0.0, 0.0, 0.0);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return FxRenderTypes.ADDITIVE;
    }

    @Override
    protected float sizeAt(float t) {
        return this.quadSize * easeOutCubic(t);
    }

    @Override
    protected void applyLook(float t) {
        setColor(1.0F, 0.9F, 0.75F);
        float remaining = 1.0F - t;
        this.alpha = MAX_ALPHA * remaining * Mth.sqrt(remaining);
    }

    @Override
    protected void stepVelocity() {}

    @Override
    protected void emit(VertexConsumer buffer, Camera camera, float partialTicks, float cx, float cy, float cz) {
        float h = this.renderSize;
        float y = cy + LIFT;
        float x0 = cx - h;
        float x1 = cx + h;
        float z0 = cz - h;
        float z1 = cz + h;
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTicks);
        // Counter-clockwise seen from above (+Y), then the reverse order for the underside.
        vertex(buffer, x0, y, z0, u0, v0, light);
        vertex(buffer, x0, y, z1, u0, v1, light);
        vertex(buffer, x1, y, z1, u1, v1, light);
        vertex(buffer, x1, y, z0, u1, v0, light);
        vertex(buffer, x1, y, z0, u1, v0, light);
        vertex(buffer, x1, y, z1, u1, v1, light);
        vertex(buffer, x0, y, z1, u0, v1, light);
        vertex(buffer, x0, y, z0, u0, v0, light);
    }

    /** Provider. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new RingParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
