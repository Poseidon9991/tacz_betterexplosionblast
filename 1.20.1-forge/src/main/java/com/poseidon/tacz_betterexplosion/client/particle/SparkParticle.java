package com.poseidon.tacz_betterexplosion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Slow falling ember streak (streak rendering and physics in {@link StreakParticle}): lifetime 10..25. Gravity
 * {@code yd -= 0.05} then drag {@code * 0.98} per tick. Half-length {@code clamp(speed * 2.5, 0.08, 1.2) *
 * quadSize}, half-width {@code 0.08 * quadSize}. Colour yellow-white (1.0, 0.9, 0.5) at t=0, orange
 * (1.0, 0.45, 0.1) at t=0.4, red (0.6, 0.1, 0.05) at t=1. Alpha 1 until t=0.5, then linear to 0.
 */
public final class SparkParticle extends StreakParticle {

    private static final Shape SHAPE = new Shape(0.05F, 0.98F, 2.5F, 0.08F, 1.2F, 0.08F);
    private static final int MIN_LIFETIME = 10;
    private static final int LIFETIME_SPREAD = 16;
    private static final float ORANGE_AT = 0.4F;
    private static final float ALPHA_HOLD = 0.5F;

    SparkParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites, SHAPE);
        this.lifetime = MIN_LIFETIME + this.random.nextInt(LIFETIME_SPREAD);
    }

    @Override
    protected void applyLook(float t) {
        if (t < ORANGE_AT) {
            blendColor(t / ORANGE_AT, 1.0F, 0.9F, 0.5F, 1.0F, 0.45F, 0.1F);
        } else {
            blendColor((t - ORANGE_AT) / (1.0F - ORANGE_AT), 1.0F, 0.45F, 0.1F, 0.6F, 0.1F, 0.05F);
        }
        this.alpha = t <= ALPHA_HOLD ? 1.0F : 1.0F - (t - ALPHA_HOLD) / (1.0F - ALPHA_HOLD);
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
            return new SparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
