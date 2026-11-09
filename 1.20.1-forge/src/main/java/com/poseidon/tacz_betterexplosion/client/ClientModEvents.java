package com.poseidon.tacz_betterexplosion.client;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.client.fx.FxState;
import com.poseidon.tacz_betterexplosion.client.fx.ScreenFlash;
import com.poseidon.tacz_betterexplosion.client.fx.ScreenShake;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client hooks: resetting the fx state on resource reload. */
@Mod.EventBusSubscriber(modid = TaczBetterExplosion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    private ClientModEvents() {}

    /**
     * Resets all transient fx state on every resource reload (F3+T included). {@code ParticleEngine.reload}
     * clears its particles without calling {@code remove()} on them, so the live-particle counter would
     * otherwise drift upwards until it hits the cap.
     */
    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            FxState.reset();
            ScreenShake.clear();
            ScreenFlash.clear();
        });
    }
}
