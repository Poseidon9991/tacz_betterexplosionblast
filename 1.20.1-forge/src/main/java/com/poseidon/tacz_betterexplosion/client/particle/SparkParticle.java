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
 * Glowing spark drawn as a streak along its velocity: additive, full-bright, lifetime 10..25. Gravity
 * {@code yd -= 0.05} then drag {@code * 0.98} per tick. Half-length {@code clamp(speed * 2.5, 0.08, 1.2) *
 * quadSize}, half-width {@code 0.05 * quadSize} (both limited by the 6 block cap), where {@code speed} is
 * the live speed in blocks per tick. Colour yellow-white (1.0, 0.9, 0.5) at t=0, orange (1.0, 0.45, 0.1) at
 * t=0.4, red (0.6, 0.1, 0.05) at t=1. Alpha 1 until t=0.5, then linear to 0.
 *
 * <p>The quad is an axial billboard: it lies along the velocity and is turned about that axis to face the
 * camera. {@code spark.png} has its bright HEAD at the BOTTOM (rows near the bottom edge, V1) and fades to
 * nothing at the top (V0, the tail); the head vertices are the ones in front in the direction of motion.
 * Below a speed of 1e-4 (or when the velocity points straight at the camera) a plain billboard is used.
 */
public final class SparkParticle extends FxParticle {

    private static final int MIN_LIFETIME = 10;
    private static final int LIFETIME_SPREAD = 16;
    private static final float GRAVITY = 0.05F;
    private static final float DRAG = 0.98F;
    private static final float LENGTH_PER_SPEED = 2.5F;
    private static final float MIN_HALF_LENGTH = 0.08F;
    private static final float MAX_HALF_LENGTH = 1.2F;
    private static final float HALF_WIDTH = 0.05F;
    private static final float ORANGE_AT = 0.4F;
    private static final float ALPHA_HOLD = 0.5F;
    private static final float MIN_SPEED = 1.0E-4F;
    private static final float MIN_AXIS = 1.0E-4F;

    SparkParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        this.lifetime = MIN_LIFETIME + this.random.nextInt(LIFETIME_SPREAD);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return FxRenderTypes.ADDITIVE;
    }

    private float speed() {
        return (float) Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
    }

    @Override
    protected float sizeAt(float t) {
        return Mth.clamp(speed() * LENGTH_PER_SPEED, MIN_HALF_LENGTH, MAX_HALF_LENGTH) * this.quadSize;
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

    @Override
    protected void stepVelocity() {
        this.yd -= GRAVITY;
        this.xd *= DRAG;
        this.yd *= DRAG;
        this.zd *= DRAG;
    }

    @Override
    protected void emit(VertexConsumer buffer, Camera camera, float partialTicks, float cx, float cy, float cz) {
        float speed = speed();
        float distance = Mth.sqrt(cx * cx + cy * cy + cz * cz);
        if (speed < MIN_SPEED || distance < MIN_AXIS) {
            super.emit(buffer, camera, partialTicks, cx, cy, cz);
            return;
        }
        // a = unit velocity (streak axis), view = unit vector from the camera to the particle.
        float ax = (float) this.xd / speed;
        float ay = (float) this.yd / speed;
        float az = (float) this.zd / speed;
        float vx = cx / distance;
        float vy = cy / distance;
        float vz = cz / distance;
        // side = normalised cross(a, view): in the quad plane, perpendicular to the streak.
        float sx = ay * vz - az * vy;
        float sy = az * vx - ax * vz;
        float sz = ax * vy - ay * vx;
        float sideLength = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (sideLength < MIN_AXIS) {
            super.emit(buffer, camera, partialTicks, cx, cy, cz);
            return;
        }
        float length = this.renderSize;
        float width = HALF_WIDTH * Math.min(this.quadSize, MAX_RENDER_SIZE) / sideLength;
        sx *= width;
        sy *= width;
        sz *= width;
        float hx = ax * length;
        float hy = ay * length;
        float hz = az * length;
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTicks);
        // Counter-clockwise seen from the camera: tail+side, tail-side, head-side, head+side (head = V1).
        vertex(buffer, cx - hx + sx, cy - hy + sy, cz - hz + sz, u0, v0, light);
        vertex(buffer, cx - hx - sx, cy - hy - sy, cz - hz - sz, u1, v0, light);
        vertex(buffer, cx + hx - sx, cy + hy - sy, cz + hz - sz, u1, v1, light);
        vertex(buffer, cx + hx + sx, cy + hy + sy, cz + hz + sz, u0, v1, light);
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
