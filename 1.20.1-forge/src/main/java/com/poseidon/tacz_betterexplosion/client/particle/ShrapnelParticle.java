package com.poseidon.tacz_betterexplosion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Fast, clearly visible glowing shrapnel streak (streak rendering and physics in {@link StreakParticle}):
 * lifetime 14..30. Gravity {@code yd -= 0.025} then drag {@code * 0.985} per tick. Half-length
 * {@code clamp(speed * 2.6, 0.5, 3.5) * quadSize}, half-width {@code 0.14 * quadSize}. Colour white-yellow
 * (1.0, 0.95, 0.7) at t=0, orange (1.0, 0.5, 0.12) at t=0.35, dark red (0.5, 0.08, 0.04) at t=0.85 (held
 * after). Alpha 1 until t=0.6, then linear to 0. {@code shrapnel.png} is brighter and wider than the spark.
 */
public final class ShrapnelParticle extends StreakParticle {

    private static final Shape SHAPE = new Shape(0.025F, 0.985F, 2.6F, 0.5F, 3.5F, 0.14F);
    private static final int MIN_LIFETIME = 14;
    private static final int LIFETIME_SPREAD = 17;
    private static final float ORANGE_AT = 0.35F;
    private static final float RED_AT = 0.85F;
    private static final float ALPHA_HOLD = 0.6F;

    ShrapnelParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites, SHAPE);
        this.lifetime = MIN_LIFETIME + this.random.nextInt(LIFETIME_SPREAD);
    }

    @Override
    protected void applyLook(float t) {
        if (t < ORANGE_AT) {
            blendColor(t / ORANGE_AT, 1.0F, 0.95F, 0.7F, 1.0F, 0.5F, 0.12F);
        } else {
            blendColor(
                    Math.min(1.0F, (t - ORANGE_AT) / (RED_AT - ORANGE_AT)), 1.0F, 0.5F, 0.12F, 0.5F, 0.08F, 0.04F);
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
            return new ShrapnelParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
