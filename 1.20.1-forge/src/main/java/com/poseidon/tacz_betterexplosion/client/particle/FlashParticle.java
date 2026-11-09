package com.poseidon.tacz_betterexplosion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Bright flash: additive, full-bright camera-facing billboard, default lifetime 6 ticks, colour
 * (1.0, 0.96, 0.85), {@code t = age / lifetime}. Alpha is {@link #setIntensity intensity} (default 1) for
 * {@code t < 0.25}, then {@code intensity * (1 - (t - 0.25) / 0.75)^1.5}. Size
 * {@code quadSize * (0.8 + 0.7 * easeOutCubic(t))}. The velocity is kept constant (no damping).
 */
public final class FlashParticle extends FxParticle {

    private static final int LIFETIME = 6;
    private static final float HOLD_UNTIL = 0.25F;
    private static final float DECAY_POWER = 1.5F;

    private float intensity = 1.0F;

    FlashParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        this.lifetime = LIFETIME;
    }

    /** Multiplier of the alpha curve (1 = core flash, less for the halo). */
    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return FxRenderTypes.ADDITIVE;
    }

    @Override
    protected float sizeAt(float t) {
        return this.quadSize * (0.8F + 0.7F * easeOutCubic(t));
    }

    @Override
    protected void applyLook(float t) {
        setColor(1.0F, 0.96F, 0.85F);
        this.alpha = t < HOLD_UNTIL
                ? this.intensity
                : this.intensity * (float) Math.pow(1.0F - (t - HOLD_UNTIL) / (1.0F - HOLD_UNTIL), DECAY_POWER);
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
