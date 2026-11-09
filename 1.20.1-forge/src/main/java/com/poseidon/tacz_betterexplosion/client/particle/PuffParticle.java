package com.poseidon.tacz_betterexplosion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Fireball and smoke puffs ({@code t = age / lifetime}, size is a half-extent, see {@link FxParticle}).
 *
 * <p>FIRE: additive, full-bright, lifetime 8..20. Colour cream (1.0, 0.95, 0.80) at t=0, orange
 * (1.0, 0.55, 0.12) at t=0.35, dark red (0.35, 0.05, 0.02) at t=1. Alpha 1 until t=0.45, then linear to 0.
 * Size {@code quadSize * (0.55 + 0.45 * easeOutCubic(t))}. Velocity {@code * 0.88} per tick, {@code yd += 0.004}.
 *
 * <p>SMOKE: translucent sheet, lifetime 40..100, light read once at spawn (the only world access). Colour
 * (0.38, 0.36, 0.35) to (0.10, 0.10, 0.10). Alpha ramps 0 to 0.75 over t in [0, 0.08], holds, then falls
 * linearly to 0 over t in [0.6, 1]. Size {@code quadSize * (0.6 + 0.4 * easeOutQuad(t))}. Velocity
 * {@code * 0.96} per tick, {@code yd >= 0.012} until t=0.8, horizontal drift of 0.004 * sin/cos(0.35 * age +
 * phase) added to {@code xd}/{@code zd} with a random per-particle phase.
 */
public final class PuffParticle extends FxParticle {

    /** Which puff this is. */
    public enum Variant {
        FIRE,
        SMOKE
    }

    private static final int FIRE_MIN_LIFETIME = 8;
    private static final int FIRE_LIFETIME_SPREAD = 13;
    private static final int SMOKE_MIN_LIFETIME = 40;
    private static final int SMOKE_LIFETIME_SPREAD = 61;

    private static final float FIRE_ORANGE_AT = 0.35F;
    private static final float FIRE_ALPHA_HOLD = 0.45F;
    private static final float FIRE_DAMPING = 0.88F;
    private static final float FIRE_BUOYANCY = 0.004F;

    private static final float SMOKE_FADE_IN_END = 0.08F;
    private static final float SMOKE_FADE_OUT_START = 0.6F;
    private static final float SMOKE_MAX_ALPHA = 0.75F;
    private static final float SMOKE_DAMPING = 0.96F;
    private static final float SMOKE_MIN_RISE = 0.012F;
    private static final float SMOKE_RISE_UNTIL = 0.8F;
    private static final float SMOKE_DRIFT = 0.004F;
    private static final float SMOKE_DRIFT_FREQUENCY = 0.35F;

    private final Variant variant;
    private final int light;
    private final float phase;

    PuffParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            Variant variant,
            SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        this.variant = variant;
        if (variant == Variant.FIRE) {
            this.lifetime = FIRE_MIN_LIFETIME + this.random.nextInt(FIRE_LIFETIME_SPREAD);
            this.light = FULL_BRIGHT;
            this.phase = 0.0F;
        } else {
            this.lifetime = SMOKE_MIN_LIFETIME + this.random.nextInt(SMOKE_LIFETIME_SPREAD);
            this.light = lightAt(level, x, y, z);
            this.phase = this.random.nextFloat() * Mth.TWO_PI;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.variant == Variant.FIRE ? FxRenderTypes.ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return this.light;
    }

    @Override
    protected float sizeAt(float t) {
        if (this.variant == Variant.FIRE) {
            return this.quadSize * (0.55F + 0.45F * easeOutCubic(t));
        }
        return this.quadSize * (0.6F + 0.4F * easeOutQuad(t));
    }

    @Override
    protected void applyLook(float t) {
        if (this.variant == Variant.FIRE) {
            if (t < FIRE_ORANGE_AT) {
                blendColor(t / FIRE_ORANGE_AT, 1.0F, 0.95F, 0.80F, 1.0F, 0.55F, 0.12F);
            } else {
                blendColor((t - FIRE_ORANGE_AT) / (1.0F - FIRE_ORANGE_AT), 1.0F, 0.55F, 0.12F, 0.35F, 0.05F, 0.02F);
            }
            this.alpha = t <= FIRE_ALPHA_HOLD ? 1.0F : 1.0F - (t - FIRE_ALPHA_HOLD) / (1.0F - FIRE_ALPHA_HOLD);
        } else {
            blendColor(t, 0.38F, 0.36F, 0.35F, 0.10F, 0.10F, 0.10F);
            if (t < SMOKE_FADE_IN_END) {
                this.alpha = SMOKE_MAX_ALPHA * t / SMOKE_FADE_IN_END;
            } else if (t < SMOKE_FADE_OUT_START) {
                this.alpha = SMOKE_MAX_ALPHA;
            } else {
                this.alpha = SMOKE_MAX_ALPHA * (1.0F - (t - SMOKE_FADE_OUT_START) / (1.0F - SMOKE_FADE_OUT_START));
            }
        }
    }

    @Override
    protected void stepVelocity() {
        if (this.variant == Variant.FIRE) {
            this.xd *= FIRE_DAMPING;
            this.yd = this.yd * FIRE_DAMPING + FIRE_BUOYANCY;
            this.zd *= FIRE_DAMPING;
            return;
        }
        float angle = SMOKE_DRIFT_FREQUENCY * this.age + this.phase;
        this.xd = this.xd * SMOKE_DAMPING + SMOKE_DRIFT * Mth.sin(angle);
        this.yd *= SMOKE_DAMPING;
        this.zd = this.zd * SMOKE_DAMPING + SMOKE_DRIFT * Mth.cos(angle);
        if (this.age < SMOKE_RISE_UNTIL * this.lifetime) {
            this.yd = Math.max(this.yd, SMOKE_MIN_RISE);
        }
    }

    /** Provider for both variants. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final Variant variant;
        private final SpriteSet sprites;

        public Provider(Variant variant, SpriteSet sprites) {
            this.variant = variant;
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
            return new PuffParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.variant, this.sprites);
        }
    }
}
