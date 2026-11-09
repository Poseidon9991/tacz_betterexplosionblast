package com.poseidon.tacz_betterexplosion.client;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.client.fx.ExplosionSounds;
import com.poseidon.tacz_betterexplosion.client.fx.ExplosionVfx;
import com.poseidon.tacz_betterexplosion.client.fx.FxState;
import com.poseidon.tacz_betterexplosion.client.fx.ScreenFlash;
import com.poseidon.tacz_betterexplosion.client.fx.ScreenShake;
import com.poseidon.tacz_betterexplosion.config.ClientConfig;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import com.poseidon.tacz_betterexplosion.fx.FxBudget;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client-side explosion effect. Reached only through {@code DistExecutor} from the network handler, so a
 * dedicated server never loads it. Computes the distance and the quality budget, splits it over the visual
 * layers of {@link ExplosionVfx}, then always plays the distance-banded sound plus screen shake and flash.
 * Per-tier shake strength in degrees: MINI 0.4, SMALL 0.8, MEDIUM 1.5, LARGE 2.5, HUGE 4.0, times 1.3 for an
 * underwater blast (before the config and threshold checks).
 */
public final class ClientFxHandler {

    /** Distance beyond which an explosion behind the camera gets the {@link FxBudget#lodFactor} cut. */
    private static final double BEHIND_DISTANCE = 24.0;
    /** Degrees of shake per tier at zero distance. */
    private static final float[] TIER_SHAKE = {0.4F, 0.8F, 1.5F, 2.5F, 4.0F};
    /** Smallest shake worth applying. */
    private static final float MIN_SHAKE = 0.05F;
    /** Extra blocks of flash range past the explosion radius. */
    private static final double FLASH_EXTRA_RANGE = 3.0;
    /** Shake multiplier when the blast is underwater. */
    private static final float UNDERWATER_SHAKE = 1.3F;

    private ClientFxHandler() {
    }

    public static void handle(ExplosionFx fx) {
        long startNanos = System.nanoTime();
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) {
                return;
            }
            double x = fx.x();
            double y = fx.y();
            double z = fx.z();
            float radius = fx.radius();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(radius)) {
                TaczBetterExplosion.LOGGER.warn(
                        "Ignoring non-finite explosion effect ({}, {}, {}) radius {}", x, y, z, radius);
                return;
            }

            boolean underwater = fx.underwater();
            ExplosionTier tier = ExplosionTier.fromRadius(radius);
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 cam = camera.getPosition();
            double dx = x - cam.x();
            double dy = y - cam.y();
            double dz = z - cam.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            boolean behind = false;
            if (dist > BEHIND_DISTANCE) {
                Vector3f look = camera.getLookVector();
                behind = (look.x() * dx + look.y() * dy + look.z() * dz) / dist < 0.0;
            }

            FxBudget.VanillaParticles vanilla =
                    switch (mc.options.particles().get()) {
                        case ALL -> FxBudget.VanillaParticles.ALL;
                        case DECREASED -> FxBudget.VanillaParticles.DECREASED;
                        case MINIMAL -> FxBudget.VanillaParticles.MINIMAL;
                    };
            int fps = ClientConfig.adaptiveQuality() ? mc.getFps() : 0;
            int recent = FxState.recentExplosions(level.getGameTime());
            int budget = FxBudget.budget(
                    tier,
                    ClientConfig.quality(),
                    vanilla,
                    FxBudget.burstFactor(recent),
                    FxBudget.fpsFactor(fps),
                    FxBudget.lodFactor(dist, tier, behind),
                    FxState.liveParticles(),
                    ClientConfig.maxLiveParticles());

            EnumMap<FxBudget.Layer, Integer> split = FxBudget.split(budget);
            for (Map.Entry<FxBudget.Layer, Integer> entry : split.entrySet()) {
                if (!FxBudget.layerAllowed(entry.getKey(), dist)) {
                    entry.setValue(0);
                }
            }

            if (budget > 0) {
                double groundY = ExplosionVfx.findGround(level, x, y, z, radius);
                BlockState groundState = Double.isNaN(groundY)
                        ? Blocks.AIR.defaultBlockState()
                        : level.getBlockState(BlockPos.containing(x, groundY - 0.01, z));
                int spawned = ExplosionVfx.spawn(fx, level, split, groundY, groundState, underwater);
                FxState.addSpawned(spawned);
            }

            ExplosionSounds.play(fx, level, dist, underwater);

            if (ClientConfig.screenShake()) {
                float strength = (float) (TIER_SHAKE[tier.ordinal()]
                        * Math.max(0.0, 1.0 - dist / tier.particleRange())
                        * ClientConfig.shakeStrength());
                if (underwater) {
                    strength *= UNDERWATER_SHAKE;
                }
                if (strength >= MIN_SHAKE) {
                    ScreenShake.kick(strength);
                }
            }
            if (ClientConfig.screenFlash() && dist <= radius + FLASH_EXTRA_RANGE) {
                ScreenFlash.flash();
            }
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Explosion client effect failed at ({}, {}, {})",
                    fx.x(), fx.y(), fx.z(), e);
        } finally {
            FxState.lastHandlerNanos = System.nanoTime() - startNanos;
        }
    }
}
