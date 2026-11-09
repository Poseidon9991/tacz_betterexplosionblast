package com.poseidon.tacz_betterexplosion.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Shared base of the glowing streak particles ({@link SparkParticle}, {@link ShrapnelParticle}): additive,
 * full-bright, drawn as a quad stretched along the velocity. Subclasses supply the lifetime and the colour and
 * alpha curves; the physics and the shape come from the {@link Shape} given to the constructor.
 *
 * <p>Physics per tick: {@code yd -= gravity}, then all three velocity components {@code *= drag}.
 * Half-length (the {@code size} of {@link FxParticle}) is {@code clamp(speed * lengthPerSpeed, minHalfLength,
 * maxHalfLength) * quadSize}, half-width is {@code halfWidth * quadSize} (both limited by the 6 block cap),
 * where {@code speed} is the live speed in blocks per tick.
 *
 * <p>The quad is an axial billboard: it lies along the velocity and is turned about that axis to face the
 * camera. The texture has its bright HEAD at the BOTTOM (rows near the bottom edge, V1) and fades towards the
 * top (V0, the tail); the head vertices are the ones in front in the direction of motion. Below a speed of
 * 1e-4 (or when the velocity points straight at the camera) a plain billboard is used.
 */
abstract class StreakParticle extends FxParticle {

    /** Physics and shape constants of one streak kind. */
    record Shape(
            float gravity,
            float drag,
            float lengthPerSpeed,
            float minHalfLength,
            float maxHalfLength,
            float halfWidth) {}

    private static final float MIN_SPEED = 1.0E-4F;
    private static final float MIN_AXIS = 1.0E-4F;

    private final Shape shape;

    protected StreakParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            SpriteSet sprites,
            Shape shape) {
        super(level, x, y, z, xd, yd, zd, sprites);
        this.shape = shape;
    }

    @Override
    public final ParticleRenderType getRenderType() {
        return FxRenderTypes.ADDITIVE;
    }

    private float speed() {
        return (float) Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
    }

    @Override
    protected final float sizeAt(float t) {
        float halfLength = Mth.clamp(
                speed() * this.shape.lengthPerSpeed(), this.shape.minHalfLength(), this.shape.maxHalfLength());
        return halfLength * this.quadSize;
    }

    @Override
    protected final void stepVelocity() {
        this.yd -= this.shape.gravity();
        this.xd *= this.shape.drag();
        this.yd *= this.shape.drag();
        this.zd *= this.shape.drag();
    }

    @Override
    protected final void emit(
            VertexConsumer buffer, Camera camera, float partialTicks, float cx, float cy, float cz) {
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
        float width = this.shape.halfWidth() * Math.min(this.quadSize, MAX_RENDER_SIZE) / sideLength;
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
}
