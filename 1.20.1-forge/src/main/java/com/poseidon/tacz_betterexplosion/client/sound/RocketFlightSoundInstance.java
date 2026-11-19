package com.poseidon.tacz_betterexplosion.client.sound;

import com.poseidon.tacz_betterexplosion.ModSounds;
import com.poseidon.tacz_betterexplosion.config.ClientConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side looping flight sound for rockets in flight with Doppler pitch shift and custom 3D distance falloff.
 */
@OnlyIn(Dist.CLIENT)
public class RocketFlightSoundInstance extends AbstractTickableSoundInstance {

    private static final double FULL_RANGE = 14.0;
    private static final double MAX_RANGE = 192.0;

    private final EntityKineticBullet bullet;
    private double lastDistance = -1.0;
    private int age = 0;

    public RocketFlightSoundInstance(EntityKineticBullet bullet) {
        super(ModSounds.ROCKET_FLY.get(), SoundSource.PLAYERS, bullet.level().random);
        this.bullet = bullet;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.x = bullet.getX();
        this.y = bullet.getY();
        this.z = bullet.getZ();
        updateAudio(true);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (bullet.isRemoved() || !bullet.isAlive() || bullet.level() == null || !ClientConfig.rocketFlightSound()) {
            this.stop();
            return;
        }
        age++;
        this.x = bullet.getX();
        this.y = bullet.getY();
        this.z = bullet.getZ();
        updateAudio(false);
    }

    private void updateAudio(boolean initial) {
        double configVol = ClientConfig.soundVolume();
        if (configVol <= 0.0) {
            this.volume = 0.0F;
            return;
        }

        double speed = bullet.getDeltaMovement().length();
        float speedGain = (float) Math.min(1.0, 0.6 + speed * 0.10);
        float ramp = initial ? 0.70F : Math.min(1.0F, 0.70F + age * 0.30F);
        float basePitch = (float) Math.min(1.35, 0.92 + speed * 0.03);

        double distGain = 1.0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
            double distance = bullet.position().distanceTo(camPos);
            if (distance >= MAX_RANGE) {
                distGain = 0.0;
            } else if (distance > FULL_RANGE) {
                double t = (distance - FULL_RANGE) / (MAX_RANGE - FULL_RANGE);
                distGain = Math.pow(1.0 - t, 1.35);
            }
            if (!initial && lastDistance >= 0.0) {
                double deltaDist = lastDistance - distance;
                basePitch += (float) (0.20 * Math.atan(deltaDist * 0.5));
            }
            lastDistance = distance;
        }

        this.volume = (float) Math.max(0.0, Math.min(2.0, speedGain * ramp * distGain * configVol));
        this.pitch = Math.max(0.5F, Math.min(2.0F, basePitch));
    }
}
