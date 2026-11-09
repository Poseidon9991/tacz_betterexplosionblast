package com.poseidon.tacz_betterexplosion;

import com.mojang.logging.LogUtils;
import com.poseidon.tacz_betterexplosion.config.ClientConfig;
import com.poseidon.tacz_betterexplosion.config.ServerConfig;
import com.poseidon.tacz_betterexplosion.explosion.BlockPass;
import com.poseidon.tacz_betterexplosion.net.Network;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TaczBetterExplosion.MOD_ID)
public class TaczBetterExplosion {
    public static final String MOD_ID = "tacz_betterexplosion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaczBetterExplosion(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(BlockPass.class);
        ModParticles.REGISTER.register(context.getModEventBus());
        ModSounds.REGISTER.register(context.getModEventBus());
        context.getModEventBus().addListener(TaczBetterExplosion::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
    }
}
