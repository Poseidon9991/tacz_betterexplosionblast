package com.poseidon.tacz_betterexplosion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Distance-banded explosion sounds of the mod. Registered on both sides (the registry has to match);
 * playback happens client-side in {@code client.fx.ExplosionSounds}. Common code, so no client classes here.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TaczBetterExplosion.MOD_ID);

    public static final RegistryObject<SoundEvent> EXPLOSION_CLOSE =
            REGISTER.register("explosion_close", () -> SoundEvent.createVariableRangeEvent(id("explosion_close")));
    public static final RegistryObject<SoundEvent> EXPLOSION_MID =
            REGISTER.register("explosion_mid", () -> SoundEvent.createVariableRangeEvent(id("explosion_mid")));
    public static final RegistryObject<SoundEvent> EXPLOSION_FAR =
            REGISTER.register("explosion_far", () -> SoundEvent.createVariableRangeEvent(id("explosion_far")));

    private ModSounds() {}

    // The (namespace, path) constructor is flagged for removal in Forge 47.4.0 but works on every 1.20.1 build;
    // fromNamespaceAndPath needs a newer Forge 47.x and OptiFine strips it.
    @SuppressWarnings("removal")
    private static ResourceLocation id(String name) {
        return new ResourceLocation(TaczBetterExplosion.MOD_ID, name);
    }
}
