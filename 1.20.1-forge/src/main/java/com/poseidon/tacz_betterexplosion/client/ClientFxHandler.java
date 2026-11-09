package com.poseidon.tacz_betterexplosion.client;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Client-side explosion effect. Reached only through {@code DistExecutor} from the network handler, so a
 * dedicated server never loads it. Milestone 1 body: vanilla explosion sound and particles.
 */
public final class ClientFxHandler {

    private ClientFxHandler() {
    }

    public static void handle(ExplosionFx fx) {
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            double x = fx.x();
            double y = fx.y();
            double z = fx.z();
            float radius = fx.radius();

            level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
                    (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F, false);

            if (radius >= 2.0F) {
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, true, x, y, z, 1.0, 0.0, 0.0);
            } else {
                level.addParticle(ParticleTypes.EXPLOSION, true, x, y, z, 1.0, 0.0, 0.0);
            }

            RandomSource random = RandomSource.create(fx.seed());
            int smoke = Mth.clamp(Math.round(radius * 2.0F), 4, 16);
            for (int i = 0; i < smoke; i++) {
                double dx = (random.nextDouble() - 0.5) * 2.0 * radius;
                double dy = random.nextDouble() * radius;
                double dz = (random.nextDouble() - 0.5) * 2.0 * radius;
                level.addParticle(ParticleTypes.LARGE_SMOKE, true, x + dx, y + dy, z + dz,
                        0.0, 0.05 + random.nextDouble() * 0.05, 0.0);
            }
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Explosion client effect failed at ({}, {}, {})",
                    fx.x(), fx.y(), fx.z(), e);
        }
    }
}
