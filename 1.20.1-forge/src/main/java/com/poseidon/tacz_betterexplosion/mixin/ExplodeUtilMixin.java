package com.poseidon.tacz_betterexplosion.mixin;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.config.ServerConfig;
import com.poseidon.tacz_betterexplosion.explosion.BetterExplosions;
import com.poseidon.tacz_betterexplosion.explosion.ExplosionContext;
import com.tacz.guns.util.ExplodeUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The one hook: every TaCZ explosion goes through ExplodeUtil.createExplosion. require = 1 makes a
// future TaCZ change fail loudly at launch instead of silently doing nothing.
@Mixin(value = ExplodeUtil.class, remap = false)
public abstract class ExplodeUtilMixin {

    @Inject(method = "createExplosion", at = @At("HEAD"), cancellable = true, require = 1)
    private static void tbe$replace(@Nullable Entity owner, Entity exploder, float damage, float radius,
                                    boolean knockback, boolean destroyBlock, Vec3 pos, CallbackInfo ci) {
        if (!ServerConfig.enabled() || !(exploder.level() instanceof ServerLevel level)) {
            return;
        }
        // From here on TaCZ's original never runs, even if a pass fails.
        try {
            BetterExplosions.detonate(
                    new ExplosionContext(level, owner, exploder, damage, radius, knockback, destroyBlock, pos));
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Better explosion failed at {}", pos, e);
        }
        ci.cancel();
    }
}
