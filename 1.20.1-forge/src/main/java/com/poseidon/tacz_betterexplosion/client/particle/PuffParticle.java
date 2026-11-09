package com.poseidon.tacz_betterexplosion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Fireball and smoke puffs ({@code t = age / lifetime}, size is a half-extent, see {@link FxParticle}). Both
 * variants blend the final colour 60 percent toward the {@link #setTint tint} when one is set (default: no
 * tint, colour unchanged).
 *
 * <p>FIRE: additive, full-bright, lifetime 10..26. Colour cream (1.0, 0.95, 0.80) at t=0, orange
 * (1.0, 0.55, 0.12) at t=0.35, dark red (0.35, 0.05, 0.02) at t=1. Alpha 1 until t=0.45, then linear to 0.
 * Size {@code quadSize * (0.55 + 0.45 * easeOutCubic(t))}. Velocity {@code * 0.88} per tick, {@code yd += 0.004}.
 *
 * <p>SMOKE: translucent sheet, lifetime 70..140, light read once at spawn (the only world access). Colour
 * piecewise linear from warm ember-brown (0.55, 0.38, 0.25) at t=0 to grey (0.36, 0.35, 0.34) at t=0.2,
 * (0.16, 0.16, 0.16) at t=0.65 and (0.09, 0.09, 0.09) at t=1, times a per-particle brightness in
 * [0.85, 1.15]. Alpha ramps 0 to 0.95 over t in [0, 0.05], holds until t=0.65, then falls linearly to 0
 * over t in [0.65, 1]. Size {@code quadSize * (0.6 + 0.8 * easeOutQuad(t))} (billowing growth). The quad spins slowly
 * ({@code roll} changes by up to 0.04 rad per tick, random sign and magnitude, from a random start angle).
 * Velocity {@code * 0.965} per tick, {@code yd >= 0.010} until t=0.85, plus turbulence
 * {@code 0.006 * sin(0.30 * age + phase) + 0.003 * sin(0.77 * age + phase2)} on {@code xd} and the cosine of
 * the same angles on {@code zd}.
 *
 * <p>{@link #setGroundDust} switches a SMOKE puff into ground-dust mode (the shock-ring puffs): horizontal
 * damping 0.93 per tick, constant {@code yd = 0.004} (no rise floor, no turbulence), size
 * {@code quadSize * (0.7 + 0.8 * easeOutQuad(t))} (low, flat, wide growth) and alpha ramping 0 to 0.8 over
 * t in [0, 0.08], holding until t=0.5, then linear to 0. Colour and spin are unchanged.
 */
public final class PuffParticle extends FxParticle {

    /** Which puff this is. */
    public enum Variant {
        FIRE,
        SMOKE
    }

    private static final int FIRE_MIN_LIFETIME = 10;
    private static final int FIRE_LIFETIME_SPREAD = 17;
    private static final int SMOKE_MIN_LIFETIME = 70;
    private static final int SMOKE_LIFETIME_SPREAD = 71;

    private static final float FIRE_ORANGE_AT = 0.35F;
    private static final float FIRE_ALPHA_HOLD = 0.45F;
    private static final float FIRE_DAMPING = 0.88F;
    private static final float FIRE_BUOYANCY = 0.004F;

    private static final float SMOKE_GREY_AT = 0.2F;
    private static final float SMOKE_DARK_AT = 0.65F;
    private static final float SMOKE_FADE_IN_END = 0.05F;
    private static final float SMOKE_FADE_OUT_START = 0.65F;
    private static final float SMOKE_MAX_ALPHA = 0.95F;
    private static final float SMOKE_DAMPING = 0.965F;
    private static final float GROUND_DUST_FADE_IN_END = 0.08F;
    private static final float GROUND_DUST_FADE_OUT_START = 0.5F;
    private static final float GROUND_DUST_MAX_ALPHA = 0.8F;
    private static final float GROUND_DUST_DAMPING = 0.93F;
    private static final float GROUND_DUST_RISE = 0.004F;
    private static final float SMOKE_MIN_RISE = 0.010F;
    private static final float SMOKE_RISE_UNTIL = 0.85F;
    private static final float SMOKE_BRIGHTNESS_MIN = 0.85F;
    private static final float SMOKE_BRIGHTNESS_SPREAD = 0.30F;
    private static final float SMOKE_MAX_SPIN = 0.04F;
    private static final float SMOKE_SWIRL = 0.006F;
    private static final float SMOKE_SWIRL_FREQUENCY = 0.30F;
    private static final float SMOKE_FLUTTER = 0.003F;
    private static final float SMOKE_FLUTTER_FREQUENCY = 0.77F;

    /** Share of the {@link #setTint tint} in the final colour of a tinted puff. */
    private static final float TINT_BLEND = 0.6F;

    private final Variant variant;
    private final int light;
    private final float phase;
    private final float phase2;
    private final float brightness;
    private final float spin;
    private boolean tinted;
    private float tintR;
    private float tintG;
    private float tintB;
    private boolean groundDust;

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
            this.phase2 = 0.0F;
            this.brightness = 1.0F;
            this.spin = 0.0F;
        } else {
            this.lifetime = SMOKE_MIN_LIFETIME + this.random.nextInt(SMOKE_LIFETIME_SPREAD);
            this.light = lightAt(level, x, y, z);
            this.phase = this.random.nextFloat() * Mth.TWO_PI;
            this.phase2 = this.random.nextFloat() * Mth.TWO_PI;
            this.brightness = SMOKE_BRIGHTNESS_MIN + this.random.nextFloat() * SMOKE_BRIGHTNESS_SPREAD;
            this.spin = (this.random.nextFloat() * 2.0F - 1.0F) * SMOKE_MAX_SPIN;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
        }
    }

    /**
     * Blends the colour toward {@code (r, g, b)}: {@code colour = lerp(gradient * brightness, tint, 0.6)}, so
     * a tinted puff (the ground dust) is lighter than the dark stem smoke instead of darker. Without a tint
     * (the default) the gradient colour is used unchanged.
     */
    public void setTint(float r, float g, float b) {
        this.tinted = true;
        this.tintR = r;
        this.tintG = g;
        this.tintB = b;
    }

    /** Ground-dust mode for the shock-ring puffs; must be set before the first tick. */
    public void setGroundDust() {
        this.groundDust = true;
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
        if (this.groundDust) {
            return this.quadSize * (0.7F + 0.8F * easeOutQuad(t));
        }
        return this.quadSize * (0.6F + 0.8F * easeOutQuad(t));
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
            if (t < SMOKE_GREY_AT) {
                blendColor(t / SMOKE_GREY_AT, 0.55F, 0.38F, 0.25F, 0.36F, 0.35F, 0.34F);
            } else if (t < SMOKE_DARK_AT) {
                blendColor(
                        (t - SMOKE_GREY_AT) / (SMOKE_DARK_AT - SMOKE_GREY_AT),
                        0.36F, 0.35F, 0.34F, 0.16F, 0.16F, 0.16F);
            } else {
                blendColor((t - SMOKE_DARK_AT) / (1.0F - SMOKE_DARK_AT), 0.16F, 0.16F, 0.16F, 0.09F, 0.09F, 0.09F);
            }
            this.rCol *= this.brightness;
            this.gCol *= this.brightness;
            this.bCol *= this.brightness;
            if (this.groundDust) {
                if (t < GROUND_DUST_FADE_IN_END) {
                    this.alpha = GROUND_DUST_MAX_ALPHA * t / GROUND_DUST_FADE_IN_END;
                } else if (t < GROUND_DUST_FADE_OUT_START) {
                    this.alpha = GROUND_DUST_MAX_ALPHA;
                } else {
                    this.alpha = GROUND_DUST_MAX_ALPHA
                            * (1.0F - (t - GROUND_DUST_FADE_OUT_START) / (1.0F - GROUND_DUST_FADE_OUT_START));
                }
            } else if (t < SMOKE_FADE_IN_END) {
                this.alpha = SMOKE_MAX_ALPHA * t / SMOKE_FADE_IN_END;
            } else if (t < SMOKE_FADE_OUT_START) {
                this.alpha = SMOKE_MAX_ALPHA;
            } else {
                this.alpha = SMOKE_MAX_ALPHA * (1.0F - (t - SMOKE_FADE_OUT_START) / (1.0F - SMOKE_FADE_OUT_START));
            }
        }
        if (this.tinted) {
            this.rCol = Mth.lerp(TINT_BLEND, this.rCol, this.tintR);
            this.gCol = Mth.lerp(TINT_BLEND, this.gCol, this.tintG);
            this.bCol = Mth.lerp(TINT_BLEND, this.bCol, this.tintB);
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
        if (this.groundDust) {
            this.xd *= GROUND_DUST_DAMPING;
            this.yd = GROUND_DUST_RISE;
            this.zd *= GROUND_DUST_DAMPING;
        } else {
            float swirl = SMOKE_SWIRL_FREQUENCY * this.age + this.phase;
            float flutter = SMOKE_FLUTTER_FREQUENCY * this.age + this.phase2;
            this.xd = this.xd * SMOKE_DAMPING + SMOKE_SWIRL * Mth.sin(swirl) + SMOKE_FLUTTER * Mth.sin(flutter);
            this.yd *= SMOKE_DAMPING;
            this.zd = this.zd * SMOKE_DAMPING + SMOKE_SWIRL * Mth.cos(swirl) + SMOKE_FLUTTER * Mth.cos(flutter);
            if (this.age < SMOKE_RISE_UNTIL * this.lifetime) {
                this.yd = Math.max(this.yd, SMOKE_MIN_RISE);
            }
        }
        this.oRoll = this.roll;
        this.roll += this.spin;
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
