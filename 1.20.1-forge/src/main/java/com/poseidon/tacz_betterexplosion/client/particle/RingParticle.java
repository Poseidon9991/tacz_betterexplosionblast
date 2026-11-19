package com.poseidon.tacz_betterexplosion.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Expanding dust shock ring: translucent sheet (not additive), full-bright through the normal
 * {@link FxParticle#getLightColor} light, lifetime 14 ticks, one flat horizontal quad centred on the
 * particle position (lifted by 0.03 blocks against z-fighting). Half-extent
 * {@code quadSize * easeOutCubic(t)} (so the quad reaches {@code quadSize} blocks; the ring in the
 * texture sits at 0.9 of the half-extent), alpha {@code 0.55 * (1 - t)^1.5}, warm dust colour
 * (0.82, 0.76, 0.65) — dusty, not a clean glow — {@code t = age / lifetime}. Ignores the velocity.
 * Both windings are written so the ring is visible from above and below; GL cull state is not touched
 * (with culling on only the side facing the camera is drawn, so the ring is not drawn twice).
 */
public final class RingParticle extends FxParticle {

    private static final int LIFETIME = 20;
    private static final float LIFT = 0.03F;
    private static final float MAX_ALPHA = 0.65F;

    private float tintR = 0.80F;
    private float tintG = 0.72F;
    private float tintB = 0.60F;
    private boolean emberScorch;
    private Direction normal = Direction.UP;

    RingParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        this.lifetime = LIFETIME;
        setParticleSpeed(0.0, 0.0, 0.0);
    }

    /** Orients the ring: it lies flat against a surface with this outward normal (default up, a floor ring). */
    public void setNormal(Direction normal) {
        this.normal = normal;
    }

    /** Sets the dusty ground tint of the flat shock ring. */
    public void setTint(float r, float g, float b) {
        this.tintR = r;
        this.tintG = g;
        this.tintB = b;
    }

    /**
     * Switches this ring into a ground scorch/ember ring right under the blast: additive orange-gold
     * glow that expands to {@code quadSize} and cools to dark ember red over 14 ticks.
     */
    public void setEmberScorch() {
        this.emberScorch = true;
        this.lifetime = 14;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.emberScorch ? FxRenderTypes.ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * A single flat, mostly transparent quad; LARGE/HUGE explosions need a ring up to ~18-24 blocks.
     */
    @Override
    protected float maxRenderedSize() {
        return 24.0F;
    }

    @Override
    protected float sizeAt(float t) {
        if (this.emberScorch) {
            return this.quadSize * (0.35F + 0.65F * easeOutCubic(t));
        }
        return this.quadSize * easeOutCubic(t);
    }

    @Override
    protected void applyLook(float t) {
        if (this.emberScorch) {
            blendColor(t, 1.00F, 0.68F, 0.24F, 0.45F, 0.10F, 0.02F);
            float remaining = 1.0F - t;
            this.alpha = 0.85F * remaining * remaining;
            return;
        }
        setColor(this.tintR, this.tintG, this.tintB);
        float remaining = 1.0F - t;
        this.alpha = MAX_ALPHA * remaining * Mth.sqrt(remaining);
    }

    @Override
    protected void stepVelocity() {}

    @Override
    protected void emit(VertexConsumer buffer, Camera camera, float partialTicks, float cx, float cy, float cz) {
        float h = this.renderSize;
        float x = cx + this.normal.getStepX() * LIFT;
        float y = cy + this.normal.getStepY() * LIFT;
        float z = cz + this.normal.getStepZ() * LIFT;
        // Corners (u0,v0) (u0,v1) (u1,v1) (u1,v0) of a quad lying in the plane perpendicular to the normal.
        float[][] c;
        switch (this.normal.getAxis()) {
            case X -> c = new float[][] {
                {x, y - h, z - h}, {x, y - h, z + h}, {x, y + h, z + h}, {x, y + h, z - h}};
            case Z -> c = new float[][] {
                {x - h, y - h, z}, {x - h, y + h, z}, {x + h, y + h, z}, {x + h, y - h, z}};
            default -> c = new float[][] {
                {x - h, y, z - h}, {x - h, y, z + h}, {x + h, y, z + h}, {x + h, y, z - h}};
        }
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        float[] us = {u0, u0, u1, u1};
        float[] vs = {v0, v1, v1, v0};
        int light = getLightColor(partialTicks);
        // Both windings so the ring is visible from either side; GL cull state is not touched.
        for (int i = 0; i < 4; i++) {
            vertex(buffer, c[i][0], c[i][1], c[i][2], us[i], vs[i], light);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(buffer, c[i][0], c[i][1], c[i][2], us[i], vs[i], light);
        }
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
