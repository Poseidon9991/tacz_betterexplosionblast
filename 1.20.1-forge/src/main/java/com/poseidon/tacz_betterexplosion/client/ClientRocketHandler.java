package com.poseidon.tacz_betterexplosion.client;

import com.poseidon.tacz_betterexplosion.ModParticles;
import com.poseidon.tacz_betterexplosion.client.sound.RocketFlightSoundInstance;
import com.poseidon.tacz_betterexplosion.config.ClientConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side rocket flight effect handler: looping flight sound with Doppler shift,
 * and distance-LOD smoke trail and exhaust flare.
 */
public final class ClientRocketHandler {

    /** Full-density smoke + spark trail within 80 blocks; coarse smoke-only trail up to 160 blocks; culled beyond. */
    static final double FULL_TRAIL_DIST_SQR = 80.0 * 80.0;
    static final double MAX_TRAIL_DIST_SQR = 160.0 * 160.0;

    private static final Map<Integer, TrackedSound> ACTIVE_SOUNDS = new HashMap<>();

    private ClientRocketHandler() {}

    public static void clear() {
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        for (TrackedSound ts : ACTIVE_SOUNDS.values()) {
            sm.stop(ts.sound);
        }
        ACTIVE_SOUNDS.clear();
    }

    /** Prunes finished or removed rockets once per client tick so detonated entries never linger. */
    public static void tick() {
        if (ACTIVE_SOUNDS.isEmpty()) {
            return;
        }
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        Iterator<Map.Entry<Integer, TrackedSound>> it = ACTIVE_SOUNDS.entrySet().iterator();
        while (it.hasNext()) {
            TrackedSound ts = it.next().getValue();
            if (ts.bullet.isRemoved() || !ts.bullet.isAlive() || ts.sound.isStopped()) {
                sm.stop(ts.sound);
                it.remove();
            }
        }
    }

    public static void handleClientTick(EntityKineticBullet bullet) {
        int id = bullet.getId();
        if (bullet.level() == null || bullet.isRemoved() || !bullet.isAlive()) {
            TrackedSound removed = ACTIVE_SOUNDS.remove(id);
            if (removed != null) {
                Minecraft.getInstance().getSoundManager().stop(removed.sound);
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (ClientConfig.rocketFlightSound() && ClientConfig.soundVolume() > 0.0) {
            SoundManager soundManager = mc.getSoundManager();
            TrackedSound existing = ACTIVE_SOUNDS.get(id);
            if (existing == null || existing.sound.isStopped() || !soundManager.isActive(existing.sound)) {
                RocketFlightSoundInstance instance = new RocketFlightSoundInstance(bullet);
                ACTIVE_SOUNDS.put(id, new TrackedSound(bullet, instance));
                soundManager.play(instance);
            }
        }

        if (ClientConfig.rocketSmokeTrail() && bullet.tickCount >= 2) {
            Vec3 cur = bullet.position();
            Camera cam = mc.gameRenderer != null ? mc.gameRenderer.getMainCamera() : null;
            double distSqr = cam != null ? cur.distanceToSqr(cam.getPosition()) : 0.0;
            if (distSqr > MAX_TRAIL_DIST_SQR) {
                return;
            }
            boolean fullDetail = distSqr <= FULL_TRAIL_DIST_SQR;
            double stepSpacing = fullDetail ? 0.55 : 1.25;

            Vec3 prev = new Vec3(bullet.xo, bullet.yo, bullet.zo);
            Vec3 diff = cur.subtract(prev);
            double dist = diff.length();
            if (dist > 0.01) {
                int steps = (int) Math.max(1, Math.ceil(dist / stepSpacing));
                Vec3 dir = diff.normalize();
                RandomSource rand = bullet.level().random;
                for (int i = 0; i < steps; i++) {
                    double t = (double) i / steps;
                    Vec3 p = prev.add(diff.scale(t));
                    double jx = (rand.nextDouble() - 0.5) * 0.12;
                    double jy = (rand.nextDouble() - 0.5) * 0.12;
                    double jz = (rand.nextDouble() - 0.5) * 0.12;

                    bullet.level().addParticle(ModParticles.SMOKE_PUFF.get(),
                            p.x + jx, p.y + jy, p.z + jz,
                            -dir.x * 0.02 + (rand.nextDouble() - 0.5) * 0.02,
                            -dir.y * 0.02 + 0.01,
                            -dir.z * 0.02 + (rand.nextDouble() - 0.5) * 0.02);

                    if (fullDetail && rand.nextFloat() < 0.4f) {
                        bullet.level().addParticle(ModParticles.SPARK.get(),
                                p.x, p.y, p.z,
                                -dir.x * 0.06, -dir.y * 0.06, -dir.z * 0.06);
                    }
                }
            }
        }
    }

    private record TrackedSound(EntityKineticBullet bullet, RocketFlightSoundInstance sound) {}
}
