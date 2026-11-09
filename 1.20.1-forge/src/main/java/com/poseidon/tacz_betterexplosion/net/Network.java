package com.poseidon.tacz_betterexplosion.net;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.client.ClientFxHandler;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import com.tacz.guns.config.common.AmmoConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The mod's {@link SimpleChannel} and the explosion fx packet. The mod is required on both sides, so the
 * protocol version is compared with strict equality: a client and server on different packet formats are
 * refused at connect time instead of failing on the first explosion.
 */
public final class Network {
    /** Bump whenever the {@link ExplosionFx} wire format changes. */
    private static final String PROTOCOL = "1";

    // The (namespace, path) constructor is flagged for removal in Forge 47.4.0 but works on every 1.20.1 build;
    // fromNamespaceAndPath needs a newer Forge 47.x and OptiFine strips it, so it is avoided even though the
    // mod range ([47.4.0,)) would admit it.
    @SuppressWarnings("removal")
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TaczBetterExplosion.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Network() {
    }

    /** Registers the packet types. Call from {@code FMLCommonSetupEvent}. */
    public static void register() {
        CHANNEL.registerMessage(0, ExplosionFx.class,
                (fx, buf) -> fx.encode(buf),
                ExplosionFx::decode,
                Network::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Sends {@code fx} to the players near it in {@code level}. */
    public static void sendFx(ServerLevel level, ExplosionFx fx) {
        int range = Math.max(AmmoConfig.EXPLOSIVE_AMMO_VISIBLE_DISTANCE.get(),
                ExplosionTier.fromRadius(fx.radius()).packetRange());
        CHANNEL.send(PacketDistributor.NEAR.with(
                () -> new PacketDistributor.TargetPoint(fx.x(), fx.y(), fx.z(), range, level.dimension())), fx);
    }

    private static void handle(ExplosionFx fx, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        try {
            context.enqueueWork(() -> runOnClient(fx));
        } catch (Exception e) {
            TaczBetterExplosion.LOGGER.error("Failed to queue explosion fx at ({}, {}, {})",
                    fx.x(), fx.y(), fx.z(), e);
        } finally {
            context.setPacketHandled(true);
        }
    }

    /** The only route from this common class into client code; a no-op on a dedicated server. */
    private static void runOnClient(ExplosionFx fx) {
        try {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFxHandler.handle(fx));
        } catch (Exception | LinkageError e) {
            TaczBetterExplosion.LOGGER.error("Explosion fx handler failed at ({}, {}, {})",
                    fx.x(), fx.y(), fx.z(), e);
        }
    }
}
