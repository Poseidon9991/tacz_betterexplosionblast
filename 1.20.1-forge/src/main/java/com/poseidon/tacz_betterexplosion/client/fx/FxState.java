package com.poseidon.tacz_betterexplosion.client.fx;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Client-side state for the mod's explosion effects: live particle counters, a recent-explosion window, a
 * delayed-task schedule and per-explosion stats. Plain statics, touched only from the client main thread.
 * No Minecraft types, so it is unit-testable.
 */
public final class FxState {

    /** Window for {@link #recentExplosions(long)} in game ticks. */
    private static final int RECENT_WINDOW = 10;

    private static int live;
    private static int peakLive;
    private static int spawnedTotal;
    private static long clientTicks;
    private static final Deque<Long> RECENT = new ArrayDeque<>();
    private static final List<Scheduled> SCHEDULED = new ArrayList<>();

    /** Nano duration of the last {@code ClientFxHandler.handle} call, for the stats command. */
    public static long lastHandlerNanos;

    private FxState() {}

    /** Number of mod particles currently alive. */
    public static int liveParticles() {
        return live;
    }

    /** Counts one new particle and raises the peak if needed. */
    public static void particleAdded() {
        live++;
        if (live > peakLive) {
            peakLive = live;
        }
    }

    /** Counts one removed particle; never goes below zero. */
    public static void particleRemoved() {
        if (live > 0) {
            live--;
        }
    }

    /** Highest live count since the last {@link #resetPeak()} or {@link #reset()}. */
    public static int peakLive() {
        return peakLive;
    }

    /** Sets the peak to the current live count. */
    public static void resetPeak() {
        peakLive = live;
    }

    /**
     * Explosions recorded in the last {@value #RECENT_WINDOW} ticks (not counting this call), then records
     * {@code gameTime}. Entries older than {@code gameTime - 10} are pruned first.
     */
    public static int recentExplosions(long gameTime) {
        long cutoff = gameTime - RECENT_WINDOW;
        while (!RECENT.isEmpty() && RECENT.peekFirst() < cutoff) {
            RECENT.pollFirst();
        }
        int count = RECENT.size();
        RECENT.addLast(gameTime);
        return count;
    }

    /** Queues {@code task} to run after {@code delayTicks} calls of {@link #tick()} (0 = next tick). */
    public static void schedule(int delayTicks, Runnable task) {
        SCHEDULED.add(new Scheduled(Math.max(0, delayTicks), task));
    }

    /**
     * Advances the client tick counter and runs every scheduled task whose delay elapsed, in schedule order.
     * Due tasks are copied out before running, so a task may schedule more tasks; those wait for the next tick.
     */
    public static void tick() {
        clientTicks++;
        List<Runnable> due = null;
        for (int i = 0; i < SCHEDULED.size(); i++) {
            Scheduled s = SCHEDULED.get(i);
            if (--s.remaining <= 0) {
                if (due == null) {
                    due = new ArrayList<>();
                }
                due.add(s.task);
            }
        }
        if (due != null) {
            SCHEDULED.removeIf(s -> s.remaining <= 0);
            for (Runnable task : due) {
                try {
                    task.run();
                } catch (Exception | LinkageError e) {
                    // Log and keep going: one bad task must not drop the rest of the queue.
                    TaczBetterExplosion.LOGGER.error("Scheduled explosion fx task failed", e);
                }
            }
        }
    }

    /** Ticks elapsed since the last {@link #reset()}; the age source of the screen effects. */
    public static long clientTicks() {
        return clientTicks;
    }

    /** Adds {@code n} spawned particles to the running total. */
    public static void addSpawned(int n) {
        spawnedTotal += n;
    }

    /** Particles spawned since the last {@link #resetStats()} or {@link #reset()}. */
    public static int spawnedTotal() {
        return spawnedTotal;
    }

    /** Zeroes the spawned total and resets the peak to the current live count. */
    public static void resetStats() {
        spawnedTotal = 0;
        resetPeak();
    }

    /** Zeroes all client fx state: counters, the recent window, the schedule and the tick clock. */
    public static void reset() {
        live = 0;
        peakLive = 0;
        spawnedTotal = 0;
        clientTicks = 0;
        lastHandlerNanos = 0L;
        RECENT.clear();
        SCHEDULED.clear();
    }

    private static final class Scheduled {
        private int remaining;
        private final Runnable task;

        private Scheduled(int remaining, Runnable task) {
            this.remaining = remaining;
            this.task = task;
        }
    }
}
