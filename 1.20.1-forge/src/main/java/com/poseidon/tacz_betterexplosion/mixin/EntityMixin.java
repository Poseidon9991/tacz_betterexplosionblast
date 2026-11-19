package com.poseidon.tacz_betterexplosion.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void tbe$shouldRenderAtSqrDistance(double distance, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof EntityKineticBullet) {
            // Keep kinetic bullets (and rockets) visible across long distances, matching SBW
            cir.setReturnValue(true);
        }
    }
}
