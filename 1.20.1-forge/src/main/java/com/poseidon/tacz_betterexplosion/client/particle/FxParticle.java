package com.poseidon.tacz_betterexplosion.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.poseidon.tacz_betterexplosion.client.fx.FxState;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Shared base of the explosion particles that draw a sprite from the particle atlas.
 *
 * <p>Parameterisation contract (how {@code ExplosionVfx} drives every particle): the provider applies the
 * incoming velocity exactly (the 4-argument constructor chain, no random jitter) and returns a "unit"
 * particle with {@code quadSize == 1}. Afterwards {@code scale(s)} multiplies {@code quadSize} and
 * {@code setLifetime(n)} replaces the lifetime. Every size, colour and alpha curve is evaluated at render
 * time from the live {@code quadSize}, {@code age} and {@code lifetime}, so both calls take effect.
 *
 * <p>"Size" follows the vanilla convention: a half-extent in blocks (the quad is {@code 2 * size} wide).
 * The rendered size is capped at {@value #MAX_RENDER_SIZE} blocks. Particles fade out when the camera is
 * closer than {@code size + 1.5} blocks and emit no vertices once the fade is near zero (fill-rate control).
 *
 * <p>Counter: {@link FxState#particleAdded()} runs in the constructor and {@link FxState#particleRemoved()}
 * in {@link #remove()}, once per instance. Particles that are cleared or evicted without {@code remove()}
 * are not subtracted; whoever clears the engine has to call {@code FxState.reset()}.
 *
 * <p>No world access in {@code tick()} or {@code render()}.
 */
public abstract class FxParticle extends TextureSheetParticle {

    /** Packed lightmap coordinates for block light 15 and sky light 15. */
    static final int FULL_BRIGHT = 0xF000F0;
    /** Upper bound of the rendered half-extent in blocks. */
    static final float MAX_RENDER_SIZE = 6.0F;
    /** Fade stops emitting vertices at or below this factor. */
    static final float MIN_FADE = 0.01F;
    /** No vertices are emitted at or below this final alpha. */
    static final float MIN_ALPHA = 0.003F;
    /** Distance range over which the camera fade goes from 0 to 1. */
    private static final float FADE_RANGE = 1.5F;

    /** Half-extent used for this frame (after the cap); set by {@link #render}. */
    protected float renderSize;
    private boolean counted;

    protected FxParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.quadSize = 1.0F;
        this.hasPhysics = false;
        pickSprite(sprites);
        this.counted = true;
        FxState.particleAdded();
    }

    /** Half-extent in blocks before the {@link #MAX_RENDER_SIZE} cap, for the life fraction {@code t}. */
    protected abstract float sizeAt(float t);

    /** Sets {@code rCol, gCol, bCol} and {@code alpha} (before the camera fade) for the life fraction {@code t}. */
    protected abstract void applyLook(float t);

    /** Updates {@code xd, yd, zd} after the particle moved this tick. */
    protected abstract void stepVelocity();

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            remove();
            return;
        }
        move(this.xd, this.yd, this.zd);
        stepVelocity();
    }

    @Override
    public final void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        double wx = Mth.lerp((double) partialTicks, this.xo, this.x);
        double wy = Mth.lerp((double) partialTicks, this.yo, this.y);
        double wz = Mth.lerp((double) partialTicks, this.zo, this.z);
        float t = lifeFraction(partialTicks);
        this.renderSize = Math.min(sizeAt(t), MAX_RENDER_SIZE);
        applyLook(t);
        float fade = cameraFade(camera, wx, wy, wz, this.renderSize);
        this.alpha *= fade;
        if (fade <= MIN_FADE || this.alpha <= MIN_ALPHA) {
            return;
        }
        Vec3 cam = camera.getPosition();
        emit(buffer, camera, partialTicks, (float) (wx - cam.x()), (float) (wy - cam.y()), (float) (wz - cam.z()));
    }

    /**
     * Writes the vertices. {@code cx, cy, cz} is the particle centre relative to the camera. The default is
     * the vanilla camera-facing billboard (rotation, roll, uv and light as in {@code SingleQuadParticle}).
     */
    protected void emit(VertexConsumer buffer, Camera camera, float partialTicks, float cx, float cy, float cz) {
        super.render(buffer, camera, partialTicks);
    }

    /** Writes one vertex in the particle vertex format order (position, uv, colour, light). */
    protected final void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return this.renderSize;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    /**
     * Frustum culling uses the particle's tiny bounding box (about {@code 0.2 * scale}), but the quads are
     * up to a dozen blocks wide, so culling would make them pop at the screen edge.
     */
    @Override
    public boolean shouldCull() {
        return false;
    }

    @Override
    public void remove() {
        super.remove();
        if (this.counted) {
            this.counted = false;
            FxState.particleRemoved();
        }
    }

    /** Fraction of the lifetime elapsed including the partial tick, clamped to [0, 1]. */
    protected final float lifeFraction(float partialTicks) {
        return this.lifetime <= 0 ? 1.0F : Mth.clamp((this.age + partialTicks) / this.lifetime, 0.0F, 1.0F);
    }

    /** Sets the colour to the linear blend from (r0, g0, b0) to (r1, g1, b1) at {@code f}. */
    protected final void blendColor(float f, float r0, float g0, float b0, float r1, float g1, float b1) {
        this.rCol = Mth.lerp(f, r0, r1);
        this.gCol = Mth.lerp(f, g0, g1);
        this.bCol = Mth.lerp(f, b0, b1);
    }

    /**
     * Camera fade: {@code clamp((distance - size) / 1.5, 0, 1)} for a particle centred at the world position
     * {@code (wx, wy, wz)}.
     */
    static float cameraFade(Camera camera, double wx, double wy, double wz, float size) {
        Vec3 cam = camera.getPosition();
        double dx = wx - cam.x();
        double dy = wy - cam.y();
        double dz = wz - cam.z();
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Mth.clamp((distance - size) / FADE_RANGE, 0.0F, 1.0F);
    }

    /**
     * Lightmap coordinates at a world position, 0 when the chunk is not loaded. The one world read a particle
     * may do, and only once at spawn. Same call as vanilla {@code Particle#getLightColor}.
     */
    @SuppressWarnings("deprecation")
    static int lightAt(ClientLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return level.hasChunkAt(pos) ? LevelRenderer.getLightColor(level, pos) : 0;
    }

    static float easeOutCubic(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    static float easeOutQuad(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u;
    }
}
