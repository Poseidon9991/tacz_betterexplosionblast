package com.poseidon.tacz_betterexplosion.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.poseidon.tacz_betterexplosion.ModParticles;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Options of the debris particle: the block state whose particle icon the chunk is drawn with.
 * Modelled on vanilla {@code BlockParticleOption}. Common code, so no client classes here.
 */
@SuppressWarnings("deprecation")
public final class DebrisOption implements ParticleOptions {

    /** Command and network deserializer (the vanilla interface is deprecated but still what {@code ParticleType} takes). */
    public static final ParticleOptions.Deserializer<DebrisOption> DESERIALIZER =
            new ParticleOptions.Deserializer<DebrisOption>() {
                @Override
                public DebrisOption fromCommand(ParticleType<DebrisOption> type, StringReader reader)
                        throws CommandSyntaxException {
                    reader.expect(' ');
                    return new DebrisOption(
                            BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), reader, false)
                                    .blockState());
                }

                @Override
                public DebrisOption fromNetwork(ParticleType<DebrisOption> type, FriendlyByteBuf buffer) {
                    return new DebrisOption(buffer.readById(Block.BLOCK_STATE_REGISTRY));
                }
            };

    /** Codec used by {@code ParticleType#codec()}: the options are just the block state. */
    public static final Codec<DebrisOption> CODEC = BlockState.CODEC.xmap(DebrisOption::new, DebrisOption::state);

    private final BlockState state;

    public DebrisOption(BlockState state) {
        this.state = state;
    }

    /** The block state the debris looks like. */
    public BlockState state() {
        return state;
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.DEBRIS.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeId(Block.BLOCK_STATE_REGISTRY, state);
    }

    @Override
    public String writeToString() {
        return ForgeRegistries.PARTICLE_TYPES.getKey(getType()) + " " + BlockStateParser.serialize(state);
    }
}
