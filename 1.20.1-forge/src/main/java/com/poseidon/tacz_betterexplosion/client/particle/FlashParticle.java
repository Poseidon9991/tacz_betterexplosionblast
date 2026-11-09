package com.poseidon.tacz_betterexplosion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Short bright flash: additive, full-bright camera-facing billboard, lifetime 2 ticks, colour
 * (1.0, 0.96, 0.85), alpha {@code 1 - t}, size {@code quadSize * (1 + 0.5 * t)} with {@code t = age / lifetime}.
 * The velocity is kept constant (no damping).
 */
public final class FlashParticle extends FxParticle {

    private static final int LIFETIME = 2;

    FlashParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        this.lifetime = LIFETIME;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return FxRenderTypes.ADDITIVE;
    }

    @Override
    protected float sizeAt(float t) {
        return this.quadSize * (1.0F + 0.5F * t);
    }

    @Override
    protected void applyLook(float t) {
        setColor(1.0F, 0.96F, 0.85F);
        this.alpha = 1.0F - t;
    }

    @Override
    protected void stepVelocity() {}

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
            return new FlashParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
