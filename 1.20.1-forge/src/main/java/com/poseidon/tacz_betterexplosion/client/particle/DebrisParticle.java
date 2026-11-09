package com.poseidon.tacz_betterexplosion.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.poseidon.tacz_betterexplosion.client.fx.FxState;
import com.poseidon.tacz_betterexplosion.particle.DebrisOption;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block debris chunk: a {@link TerrainParticle} (terrain sheet, the block's particle icon) with a fixed flight.
 * Lifetime 20..45. Gravity {@code yd -= 0.04} per tick, drag {@code * 0.98} on x and z, no collision. The
 * ground height is looked up once in the constructor (first non-empty collision shape within 8 blocks below
 * the spawn cell, else {@code y - 8}); the particle removes itself when {@code y <= groundY} or at the end
 * of its life. Size {@code quadSize * (0.12..0.22)} (random per particle, capped at 6 blocks), slow random
 * spin through {@code roll}, camera fade like {@link FxParticle}. The light is read once at spawn.
 * After the constructor there is no world access. The block tint comes from {@code TerrainParticle}.
 */
public final class DebrisParticle extends TerrainParticle {

    private static final int GROUND_SCAN_BLOCKS = 8;
    private static final int MIN_LIFETIME = 20;
    private static final int LIFETIME_SPREAD = 26;
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.98;
    private static final float MIN_SIZE_FACTOR = 0.12F;
    private static final float SIZE_FACTOR_SPREAD = 0.10F;
    private static final float MAX_SPIN = 0.15F;

    private final double groundY;
    private final float sizeFactor;
    private final float spin;
    private final int light;
    private boolean counted;

    DebrisParticle(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, BlockState state) {
        super(level, x, y, z, xd, yd, zd, state);
        // TerrainParticle jitters the velocity and halves quadSize; take over both.
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.quadSize = 1.0F;
        this.hasPhysics = false;
        this.lifetime = MIN_LIFETIME + this.random.nextInt(LIFETIME_SPREAD);
        this.sizeFactor = MIN_SIZE_FACTOR + this.random.nextFloat() * SIZE_FACTOR_SPREAD;
        this.spin = (this.random.nextFloat() * 2.0F - 1.0F) * MAX_SPIN;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.groundY = findGroundY(level, x, y, z);
        this.light = FxParticle.lightAt(level, x, y, z);
        this.counted = true;
        FxState.particleAdded();
    }

    private static double findGroundY(ClientLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int i = 0; i <= GROUND_SCAN_BLOCKS; i++) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(Direction.Axis.Y);
            }
            pos.move(Direction.DOWN);
        }
        return y - GROUND_SCAN_BLOCKS;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            remove();
            return;
        }
        this.yd -= GRAVITY;
        move(this.xd, this.yd, this.zd);
        this.xd *= DRAG;
        this.zd *= DRAG;
        this.roll += this.spin;
        if (this.y <= this.groundY) {
            remove();
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        double wx = Mth.lerp((double) partialTicks, this.xo, this.x);
        double wy = Mth.lerp((double) partialTicks, this.yo, this.y);
        double wz = Mth.lerp((double) partialTicks, this.zo, this.z);
        float fade = FxParticle.cameraFade(camera, wx, wy, wz, getQuadSize(partialTicks));
        if (fade <= FxParticle.MIN_FADE) {
            return;
        }
        this.alpha = fade;
        super.render(buffer, camera, partialTicks);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return Math.min(this.quadSize * this.sizeFactor, FxParticle.MAX_RENDER_SIZE);
    }

    @Override
    public int getLightColor(float partialTick) {
        return this.light;
    }

    /** Large debris quads would frustum-pop at the screen edge; same as {@link FxParticle#shouldCull()}. */
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

    /** Provider for the registered debris type (no particle json; registered with {@code registerSpecial}). */
    public static final class Provider implements ParticleProvider<DebrisOption> {
        @Override
        public Particle createParticle(
                DebrisOption option,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            BlockState state = option.state();
            if (state.isAir() || state.is(Blocks.MOVING_PISTON)) {
                return null;
            }
            return new DebrisParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, state);
        }
    }
}
