package com.poseidon.tacz_betterexplosion.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class EntityTypeMixin {

    @Inject(method = "clientTrackingRange", at = @At("HEAD"), cancellable = true)
    private void tbe$boostTrackingRange(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == EntityKineticBullet.TYPE) {
            // Expand tracking range from default 5 chunks (80 blocks) to 64 chunks (1024 blocks), matching SBW
            cir.setReturnValue(64);
        }
    }

    @Inject(method = "updateInterval", at = @At("HEAD"), cancellable = true)
    private void tbe$boostUpdateInterval(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == EntityKineticBullet.TYPE) {
            // Synchronize position every single tick for smooth high-speed rocket and bullet flight
            cir.setReturnValue(1);
        }
    }
}
