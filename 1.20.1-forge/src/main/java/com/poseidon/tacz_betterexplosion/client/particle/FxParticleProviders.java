package com.poseidon.tacz_betterexplosion.client.particle;

import com.poseidon.tacz_betterexplosion.ModParticles;
import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the client providers of the mod's particle types. Client only, mod event bus. */
@Mod.EventBusSubscriber(modid = TaczBetterExplosion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FxParticleProviders {

    private FxParticleProviders() {}

    @SubscribeEvent
    public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ModParticles.FIRE_PUFF.get(), sprites -> new PuffParticle.Provider(PuffParticle.Variant.FIRE, sprites));
        event.registerSpriteSet(
                ModParticles.SMOKE_PUFF.get(),
                sprites -> new PuffParticle.Provider(PuffParticle.Variant.SMOKE, sprites));
        event.registerSpriteSet(ModParticles.FLASH.get(), FlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPARK.get(), SparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHRAPNEL.get(), ShrapnelParticle.Provider::new);
        event.registerSpecial(ModParticles.DEBRIS.get(), new DebrisParticle.Provider());
    }
}
