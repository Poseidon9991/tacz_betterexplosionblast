package com.poseidon.tacz_betterexplosion;

import com.mojang.serialization.Codec;
import com.poseidon.tacz_betterexplosion.particle.DebrisOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Particle types of the mod. Registered on both sides (the registry has to match for sync); the client
 * providers live in {@code client.particle.FxParticleProviders}. Common code, so no client classes here.
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, TaczBetterExplosion.MOD_ID);

    public static final RegistryObject<SimpleParticleType> FIRE_PUFF =
            REGISTER.register("fire_puff", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SMOKE_PUFF =
            REGISTER.register("smoke_puff", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FLASH =
            REGISTER.register("flash", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SPARK =
            REGISTER.register("spark", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SHRAPNEL =
            REGISTER.register("shrapnel", () -> new SimpleParticleType(false));

    /** Block debris chunk; built like vanilla {@code ParticleTypes.BLOCK}. */
    @SuppressWarnings("deprecation")
    public static final RegistryObject<ParticleType<DebrisOption>> DEBRIS =
            REGISTER.register("debris", () -> new ParticleType<DebrisOption>(false, DebrisOption.DESERIALIZER) {
                @Override
                public Codec<DebrisOption> codec() {
                    return DebrisOption.CODEC;
                }
            });

    private ModParticles() {}
}
