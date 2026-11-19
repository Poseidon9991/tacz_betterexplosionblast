package com.poseidon.tacz_betterexplosion.projectile;

import com.poseidon.tacz_betterexplosion.client.ClientRocketHandler;
import com.poseidon.tacz_betterexplosion.config.ServerConfig;
import com.poseidon.tacz_betterexplosion.fx.ExplosionType;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Handles realistic rocket-propelled grenade (RPG) and rocket projectile flight mechanics:
 * booster launch, motor ignition puff, sustainer acceleration burn curve, long-range flight,
 * chunk loading, smoke trail, exhaust sparks, and Doppler flight sound.
 */
public final class RocketFlightHandler {

    private RocketFlightHandler() {}

    /**
     * Determines whether an EntityKineticBullet represents a rocket/RPG projectile.
     */
    public static boolean isRocket(EntityKineticBullet bullet) {
        return isRocketIds(bullet.getAmmoId(), bullet.getGunId(), ServerConfig.ammoTypes());
    }

    /**
     * Pure ID check shared by entity classification and {@code /betterexplosion listammo}.
     */
    public static boolean isRocketIds(ResourceLocation ammoId, ResourceLocation gunId, List<? extends String> overrides) {
        if (ammoId != null) {
            String id = ammoId.toString().toLowerCase(Locale.ROOT);
            if (id.contains("rpg") || id.contains("rocket") || id.contains("pg7") || id.contains("pg-7")
                    || id.contains("missile") || id.contains("at4") || id.contains("smaw")
                    || id.contains("javelin") || id.contains("stinger") || id.contains("tandem")) {
                return true;
            }
            if (ExplosionType.classify(id, overrides) == ExplosionType.ROCKET) {
                return true;
            }
        }
        if (gunId != null) {
            String gId = gunId.toString().toLowerCase(Locale.ROOT);
            if (gId.contains("rpg") || gId.contains("rocket") || gId.contains("launcher") || gId.contains("missile")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called on each tick of a confirmed rocket bullet from {@code EntityKineticBulletMixin}.
     */
    public static void onRocketTick(EntityKineticBullet bullet, boolean isClient) {
        if (isClient) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRocketHandler.handleClientTick(bullet));
        } else {
            handleServerTick(bullet);
        }
    }

    /** Backwards-compatible entry point if called without cached state. */
    public static void onBulletTick(EntityKineticBullet bullet, boolean isClient) {
        if (isRocket(bullet)) {
            onRocketTick(bullet, isClient);
        }
    }

    private static void handleServerTick(EntityKineticBullet bullet) {
        if (!ServerConfig.improvedRocketFlight() || !(bullet.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int ticks = bullet.tickCount;

        if (ticks == 3) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, bullet.getX(), bullet.getY(), bullet.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, bullet.getX(), bullet.getY(), bullet.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }

        if (ticks >= 3 && ticks <= 28) {
            Vec3 v = bullet.getDeltaMovement();
            double speed = v.length();
            if (speed < 7.5) {
                bullet.setDeltaMovement(v.scale(1.03).add(0, 0.012, 0));
            }
        }

        RocketChunkManager.keepChunksLoaded(bullet);
    }
}
