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
 * <p>SMOKE: translucent sheet, lifetime 42..72, light read once at spawn (the only world access). Colour
 * piecewise linear blast smoke: starts with warm orange-amber firelight (1.00, 0.62, 0.28) at t=0, cools
 * into warm soot grey (0.52, 0.47, 0.43) at t=0.24, dark charcoal grey (0.28, 0.27, 0.26) at t=0.65 and
 * deep soot (0.18, 0.18, 0.18) at t=1, times a per-particle brightness in [0.82, 1.16]. Alpha ramps 0 to
 * 0.94 over t in [0, 0.05], holds until t=0.65,
 * then falls linearly to 0 over t in [0.65, 1]. Size {@code quadSize * (0.65 + 0.85 * easeOutQuad(t))}
 * (billowing growth). The quad keeps its spawn sprite and rotates smoothly ({@code oRoll..roll} interpolated
 * by {@code partialTicks}, spin decaying with velocity damping) instead of discrete sprite ticking. Velocity
 * {@code * 0.965} per tick, {@code yd >= 0.014} until t=0.85.
 *
 * <p>{@link #setGroundDust} switches a SMOKE puff into ground-dust mode (the shock-ring puffs): horizontal
 * damping 0.94 per tick, constant {@code yd = 0.004} (no rise floor, no turbulence), size
 * {@code quadSize * (0.7 + 0.8 * easeOutQuad(t))} (low, flat, wide growth) and alpha ramping 0 to 0.95 over
 * t in [0, 0.03], holding until t=0.55, then linear to 0. A tinted ground-dust puff blends 85 percent toward
 * the tint (other tinted puffs 60 percent). Ground-dust puffs also override {@link #fadeSize()} so the
 * camera fade does not cull a large flat puff that is merely near the camera.
 */
public final class PuffParticle extends FxParticle {

    /** Which puff this is. */
    public enum Variant {
        FIRE,
        SMOKE
    }

    private static final int FIRE_MIN_LIFETIME = 10;
    private static final int FIRE_LIFETIME_SPREAD = 17;
    private static final int SMOKE_MIN_LIFETIME = 26;
    private static final int SMOKE_LIFETIME_SPREAD = 20;

    private static final float FIRE_ORANGE_AT = 0.35F;
    private static final float FIRE_ALPHA_HOLD = 0.45F;
    private static final float FIRE_DAMPING = 0.88F;
    private static final float FIRE_BUOYANCY = 0.004F;

    private static final float SMOKE_GREY_AT = 0.24F;
    private static final float SMOKE_DARK_AT = 0.65F;
    private static final float SMOKE_FADE_IN_END = 0.05F;
    private static final float SMOKE_FADE_OUT_START = 0.45F;
    private static final float SMOKE_MAX_ALPHA = 0.94F;
    private static final float SMOKE_DAMPING = 0.965F;
    private static final float GROUND_DUST_FADE_IN_END = 0.03F;
    private static final float GROUND_DUST_FADE_OUT_START = 0.78F;
    private static final float GROUND_DUST_MAX_ALPHA = 0.96F;
    private static final float GROUND_DUST_DAMPING = 0.945F;
    private static final float GROUND_DUST_RISE = 0.002F;
    private static final float SMOKE_MIN_RISE = 0.014F;
    private static final float SMOKE_RISE_UNTIL = 0.85F;
    private static final float SMOKE_BRIGHTNESS_MIN = 0.85F;
    private static final float SMOKE_BRIGHTNESS_SPREAD = 0.30F;
    private static final float SWIRL_AMP = 0.0016F;
    private static final float SMOKE_MAX_SPIN = 0.018F;
    private static final float SMOKE_SPIN_DAMPING = 0.96F;

    /** Share of the {@link #setTint tint} in the final colour of a tinted puff. */
    private static final float TINT_BLEND = 0.6F;
    /** Full dusty colour override for ground-dust puffs so flat ground smoke reads purely dusty. */
    private static final float GROUND_DUST_TINT_BLEND = 0.96F;

    private final Variant variant;
    private final SpriteSet sprites;
    private final int light;
    private final float brightness;
    private float spin;
    private boolean tinted;
    private float tintR;
    private float tintG;
    private float tintB;
    private boolean groundDust;
    private boolean freeDrift;
    private float buoyancyScale = 1.0F;
    private final float swirlPhase = this.random.nextFloat() * Mth.TWO_PI;

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
        this.sprites = sprites;
        if (variant == Variant.FIRE) {
            this.lifetime = FIRE_MIN_LIFETIME + this.random.nextInt(FIRE_LIFETIME_SPREAD);
            this.light = FULL_BRIGHT;
            this.brightness = 1.0F;
            this.spin = 0.0F;
        } else {
            this.lifetime = SMOKE_MIN_LIFETIME + this.random.nextInt(SMOKE_LIFETIME_SPREAD);
            this.light = lightAt(level, x, y, z);
            this.brightness = SMOKE_BRIGHTNESS_MIN + this.random.nextFloat() * SMOKE_BRIGHTNESS_SPREAD;
            float sign = this.random.nextBoolean() ? 1.0F : -1.0F;
            this.spin = sign * (0.35F + 0.65F * this.random.nextFloat()) * SMOKE_MAX_SPIN;
            // Small start angle (±0.38 rad) so the lower orange blast rim stays oriented on the underside
            this.roll = (this.random.nextFloat() * 2.0F - 1.0F) * 0.38F;
            this.oRoll = this.roll;
            // Pick one of the first 8 sprites (indices 0..7 are the orange-lined blast smoke sprites)
            setSprite(sprites.get(this.random.nextInt(8), 11));
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

    /** Ground-dust mode for the flat ground smoke & shock-ring puffs; switches to the dusty sprite set. */
    public void setGroundDust() {
        this.groundDust = true;
        // Indices 8..11 of smoke_puff.json are the 4 earthy dusty ground-smoke sprites (dust_puff..d)
        setSprite(this.sprites.get(8 + this.random.nextInt(4), 11));
    }

    /**
     * Dust that drifts freely in 3D (wall and ceiling splashes) instead of hugging the floor: same dusty sprite,
     * constant colour and size curve as {@link #setGroundDust}, but the velocity only damps, it is not pinned to
     * a flat floor slide.
     */
    public void setFreeDustDrift() {
        setGroundDust();
        this.freeDrift = true;
    }

    /** Scales the upward push of blast smoke (1 default, 0 for smoke pinned under a ceiling). */
    public void setBuoyancyScale(float scale) {
        this.buoyancyScale = scale;
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
            return this.quadSize * (0.8F + 0.45F * easeOutQuad(t));
        }
        return this.quadSize * (0.55F + 0.95F * easeOutCubic(t));
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
                blendColor(t / SMOKE_GREY_AT, 1.00F, 0.92F, 0.82F, 0.72F, 0.70F, 0.68F);
            } else if (t < SMOKE_DARK_AT) {
                blendColor(
                        (t - SMOKE_GREY_AT) / (SMOKE_DARK_AT - SMOKE_GREY_AT),
                        0.72F, 0.70F, 0.68F, 0.42F, 0.41F, 0.40F);
            } else {
                blendColor((t - SMOKE_DARK_AT) / (1.0F - SMOKE_DARK_AT), 0.42F, 0.41F, 0.40F, 0.25F, 0.25F, 0.25F);
            }
            this.rCol = Math.min(1.0F, this.rCol * this.brightness);
            this.gCol = Math.min(1.0F, this.gCol * this.brightness);
            this.bCol = Math.min(1.0F, this.bCol * this.brightness);
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
            if (this.groundDust) {
                // Flat ground smoke uses an earthy dusty colour curve directly (no orange fire tint)
                // Constant colour for the whole life (no age gradient, no fire gradient bleed-through).
                this.rCol = Mth.clamp(this.tintR * this.brightness, 0.0F, 1.0F);
                this.gCol = Mth.clamp(this.tintG * this.brightness, 0.0F, 1.0F);
                this.bCol = Mth.clamp(this.tintB * this.brightness, 0.0F, 1.0F);
            } else {
                this.rCol = Mth.lerp(TINT_BLEND, this.rCol, this.tintR);
                this.gCol = Mth.lerp(TINT_BLEND, this.gCol, this.tintG);
                this.bCol = Mth.lerp(TINT_BLEND, this.bCol, this.tintB);
            }
        }
    }

    @Override
    protected float fadeSize() {
        // Ground-dust puffs are wide and flat: fade on a small fraction of the quad so a puff near the
        // camera is not culled just because its quad is large.
        return this.groundDust ? Math.min(0.6F, this.renderSize * 0.2F) : super.fadeSize();
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
            if (this.freeDrift) {
                this.yd *= GROUND_DUST_DAMPING;
            } else {
                this.yd = GROUND_DUST_RISE;
            }
            this.zd *= GROUND_DUST_DAMPING;
            this.oRoll = this.roll;
            this.roll += this.spin * 0.4F;
            this.spin *= SMOKE_SPIN_DAMPING;
        } else {
            // Blast smoke: violent initial burst that brakes hard, then buoyant billowing rise with a slow
            // per-puff swirl so the cloud rolls and churns instead of drifting as one rigid blob.
            float life = (float) this.age / (float) this.lifetime;
            float damp = life < 0.3F ? 0.91F : 0.975F;
            this.xd *= damp;
            this.yd *= damp;
            this.zd *= damp;
            this.yd += 0.0045F * (1.0F - life) * this.buoyancyScale;
            this.xd += SWIRL_AMP * Mth.sin(this.age * 0.18F + this.swirlPhase);
            this.zd += SWIRL_AMP * Mth.cos(this.age * 0.15F + this.swirlPhase * 1.7F);
            this.oRoll = this.roll;
            this.roll += this.spin;
            this.spin *= SMOKE_SPIN_DAMPING;
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
