package com.poseidon.tacz_betterexplosion.projectile;

import com.poseidon.tacz_betterexplosion.config.ServerConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Manages chunk loading and ticking fallbacks for fast-moving rocket projectiles.
 * Ensures chunks along the flight path remain loaded and entity ticking so the projectile
 * never freezes, drops out of the sky, or disappears in unloaded chunks.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RocketChunkManager {

    /** Chunk ticket with a 40-tick (2.0s) TTL. Automatically pruned by Minecraft's DistanceManager. */
    public static final TicketType<ChunkPos> ROCKET_TICKET = TicketType.create(
            "tacz_betterexplosion_rocket", Comparator.comparingLong(ChunkPos::toLong), 40);

    /** Refresh interval in ticks while remaining in the same chunk (well below the 40-tick TTL). */
    static final int TICKET_REFRESH_INTERVAL = 15;

    private static final Map<EntityKineticBullet, RocketState> ACTIVE_ROCKETS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RocketChunkManager() {}

    /**
     * Force-loads chunks ahead of the rocket projectile to prevent stalling in unloaded terrain.
     * Deduplicates ticket requests while the rocket stays in the same chunk and the 40-tick TTL is fresh.
     */
    public static void keepChunksLoaded(EntityKineticBullet bullet) {
        if (!ServerConfig.rocketChunkLoading() || !(bullet.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        RocketState state = ACTIVE_ROCKETS.computeIfAbsent(bullet, b -> new RocketState());
        state.lastSeenTick = bullet.tickCount;

        ChunkPos current = new ChunkPos(bullet.blockPosition());
        long currentKey = current.toLong();
        if (!shouldRefreshTickets(currentKey, state.lastTicketChunk, bullet.tickCount, state.lastTicketTick)) {
            return;
        }
        state.lastTicketChunk = currentKey;
        state.lastTicketTick = bullet.tickCount;

        Vec3 motion = bullet.getDeltaMovement();
        ChunkPos next = new ChunkPos(BlockPos.containing(bullet.position().add(motion)));
        ChunkPos nextNext = new ChunkPos(BlockPos.containing(bullet.position().add(motion.scale(2.0))));

        serverLevel.getChunkSource().addRegionTicket(ROCKET_TICKET, current, 2, current);
        if (!next.equals(current)) {
            serverLevel.getChunkSource().addRegionTicket(ROCKET_TICKET, next, 2, next);
        }
        if (!nextNext.equals(next) && !nextNext.equals(current)) {
            serverLevel.getChunkSource().addRegionTicket(ROCKET_TICKET, nextNext, 2, nextNext);
        }
    }

    static boolean shouldRefreshTickets(long currentChunk, long lastTicketChunk, int tickCount, int lastTicketTick) {
        return lastTicketTick < 0
                || currentChunk != lastTicketChunk
                || (tickCount - lastTicketTick) >= TICKET_REFRESH_INTERVAL;
    }

    /**
     * Server tick fallback: if vanilla stopped ticking an active rocket (e.g. in border chunks),
     * manually tick it so it completes its flight.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ServerConfig.rocketChunkLoading() || ACTIVE_ROCKETS.isEmpty()) {
            return;
        }

        synchronized (ACTIVE_ROCKETS) {
            Iterator<Map.Entry<EntityKineticBullet, RocketState>> it = ACTIVE_ROCKETS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<EntityKineticBullet, RocketState> entry = it.next();
                EntityKineticBullet bullet = entry.getKey();
                if (bullet == null || bullet.isRemoved() || !bullet.isAlive() || bullet.level().isClientSide()) {
                    it.remove();
                    continue;
                }

                RocketState state = entry.getValue();
                if (bullet.tickCount == state.lastSeenTick) {
                    try {
                        bullet.tick();
                    } catch (Exception ignored) {
                    }
                }
                state.lastSeenTick = bullet.tickCount;
            }
        }
    }

    private static final class RocketState {
        int lastSeenTick;
        long lastTicketChunk = Long.MIN_VALUE;
        int lastTicketTick = -1;
    }
}
