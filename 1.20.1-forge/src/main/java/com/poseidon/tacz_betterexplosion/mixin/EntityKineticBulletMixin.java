package com.poseidon.tacz_betterexplosion.mixin;

import com.poseidon.tacz_betterexplosion.projectile.RocketFlightHandler;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link EntityKineticBullet#onBulletTick()} to apply rocket flight dynamics and fuse extension.
 *
 * <p>TaCZ field invariants (verified across TaCZ 1.1.5 through 1.1.8-hotfix2, {@code remap = false}):
 * <ul>
 *   <li>{@code life}: total entity lifetime in ticks before despawn (default {@code 200}, or {@code bulletData.lifeSecond * 20}).</li>
 *   <li>{@code explosion}: {@code true} when the bullet carries explosive data.</li>
 *   <li>{@code explosionDelayCount}: air-fuse countdown in ticks; defaults to {@code Integer.MAX_VALUE} when no
 *       timed fuse is configured, or {@code explosionData.delay * 20} (30 ticks = 1.5s on stock TaCZ RPG-7).
 *       Only values {@code <= 40} are extended to {@code 140} so impact-only warheads keep their infinite air fuse.</li>
 * </ul>
 * Re-verify these three field names whenever upgrading against a new major TaCZ release.
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletMixin {

    @Shadow
    private int life;

    @Shadow
    private int explosionDelayCount;

    @Shadow
    private boolean explosion;

    /** Cached rocket verdict: 0 = uncomputed, 1 = rocket, -1 = non-rocket. */
    @Unique
    private byte tbe$rocketState;

    @Inject(method = "onBulletTick", at = @At("HEAD"), remap = false)
    private void tbe$onBulletTick(CallbackInfo ci) {
        if (this.tbe$rocketState < 0) {
            return;
        }

        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (this.tbe$rocketState == 0) {
            ResourceLocation ammoId = bullet.getAmmoId();
            boolean readyToCache = bullet.tickCount >= 1
                    || (ammoId != null && !ammoId.equals(DefaultAssets.EMPTY_AMMO_ID));
            boolean rocket = RocketFlightHandler.isRocket(bullet);
            if (rocket) {
                this.tbe$rocketState = 1;
            } else {
                if (readyToCache) {
                    this.tbe$rocketState = -1;
                }
                return;
            }
        }

        if (!bullet.level().isClientSide()) {
            if (this.life < 200) {
                this.life = 200;
            }
            if (this.explosion && this.explosionDelayCount <= 40) {
                this.explosionDelayCount = 140;
            }
        }

        RocketFlightHandler.onRocketTick(bullet, bullet.level().isClientSide());
    }
}
