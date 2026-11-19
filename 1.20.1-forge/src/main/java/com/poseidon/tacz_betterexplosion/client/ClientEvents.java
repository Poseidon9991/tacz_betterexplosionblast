package com.poseidon.tacz_betterexplosion.client;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.client.fx.FxState;
import com.poseidon.tacz_betterexplosion.client.fx.ScreenFlash;
import com.poseidon.tacz_betterexplosion.client.fx.ScreenShake;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client hooks: the fx clock, logout cleanup, the screen shake and the screen flash. */
@Mod.EventBusSubscriber(modid = TaczBetterExplosion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

    private ClientEvents() {}

    /** Advances the fx tick clock and its delayed tasks once per client tick. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FxState.tick();
            ClientRocketHandler.tick();
        }
    }

    /** Clears all transient fx state when the player leaves the world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearFxState();
    }

    /** Clears all transient fx state when the client level unloads. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide()) {
            clearFxState();
        }
    }

    /** Registers the client-side fx test command when the client command dispatcher is built. */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        FxTestCommand.register(event.getDispatcher());
    }

    /** Adds the screen shake to the camera angles every frame. */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ScreenShake.apply(event);
    }

    /** Draws the fading white flash overlay after the gui. */
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        ScreenFlash.render(event);
    }

    private static void clearFxState() {
        FxState.reset();
        ScreenShake.clear();
        ScreenFlash.clear();
        ClientRocketHandler.clear();
    }
}
