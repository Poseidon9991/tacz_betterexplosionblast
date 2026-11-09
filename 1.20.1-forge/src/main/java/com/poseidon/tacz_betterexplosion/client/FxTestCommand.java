package com.poseidon.tacz_betterexplosion.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.client.fx.FxState;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * {@code /betterexplosion test <tier> [count]}: the client-side dev command behind the section-7
 * acceptance checks. Spawns {@code count} explosions (default 1, at most 50) of the tier's midpoint
 * radius at the look target, each through the same {@link ClientFxHandler#handle} path the fx packet
 * takes, then prints particles spawned, the median handler time and the peak live count in one chat line.
 */
public final class FxTestCommand {

    /** Test radius per tier: the midpoint of the tier's radius band. */
    private static final Map<ExplosionTier, Float> TEST_RADII = Map.of(
            ExplosionTier.MINI, 1.5F,
            ExplosionTier.SMALL, 2.75F,
            ExplosionTier.MEDIUM, 4.75F,
            ExplosionTier.LARGE, 8.0F,
            ExplosionTier.HUGE, 12.0F);

    /** Tier names offered as suggestions for the {@code tier} argument. */
    private static final List<String> TIER_NAMES =
            Arrays.stream(ExplosionTier.values()).map(Enum::name).toList();

    /** Blocks ahead of the camera when the crosshair is not on a block. */
    private static final double FALLBACK_DISTANCE = 8.0;
    /** Offset out of the hit face so the effect does not start inside the block. */
    private static final double FACE_NUDGE = 0.5;

    private FxTestCommand() {
    }

    /** Builds and registers the command tree on the client command dispatcher. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("betterexplosion")
                .then(Commands.literal("test")
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(TIER_NAMES, builder))
                                .executes(context -> run(context, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                        .executes(context -> run(context,
                                                IntegerArgumentType.getInteger(context, "count")))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, int count) {
        String tierName = StringArgumentType.getString(context, "tier");
        ExplosionTier tier;
        try {
            tier = ExplosionTier.valueOf(tierName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("[BetterExplosion] Unknown tier '"
                    + tierName + "'; expected one of " + String.join(", ", TIER_NAMES)));
            return 0;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            context.getSource().sendFailure(
                    Component.literal("[BetterExplosion] Not in a world; load a world first"));
            return 0;
        }

        Vec3 pos = target(mc);
        float radius = TEST_RADII.get(tier);
        FxState.resetStats();
        long[] nanos = new long[count];
        for (int i = 0; i < count; i++) {
            ExplosionFx fx = new ExplosionFx(pos.x, pos.y, pos.z, radius, (byte) 0, new Random().nextInt());
            long start = System.nanoTime();
            ClientFxHandler.handle(fx);
            nanos[i] = System.nanoTime() - start;
        }

        String message = String.format(Locale.ROOT,
                "[BetterExplosion] %s x%d: spawned=%d, handler median=%.2fms, peak live=%d",
                tier.name(), count, FxState.spawnedTotal(), median(nanos) / 1.0e6, FxState.peakLive());
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        } else {
            TaczBetterExplosion.LOGGER.info(message);
        }
        return 1;
    }

    /**
     * The look target: the hit position nudged half a block out of the clicked face, or the camera
     * position plus eight times the look vector when the crosshair is not on a block.
     */
    private static Vec3 target(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit) {
            Direction face = blockHit.getDirection();
            return blockHit.getLocation().add(
                    face.getStepX() * FACE_NUDGE, face.getStepY() * FACE_NUDGE, face.getStepZ() * FACE_NUDGE);
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        Vector3f look = camera.getLookVector();
        return cam.add(
                look.x() * FALLBACK_DISTANCE, look.y() * FALLBACK_DISTANCE, look.z() * FALLBACK_DISTANCE);
    }

    /** Median of {@code nanos} (sorted in place), in nanoseconds. Averages the two middles for even counts. */
    private static double median(long[] nanos) {
        Arrays.sort(nanos);
        int mid = nanos.length / 2;
        return nanos.length % 2 == 1 ? nanos[mid] : (nanos[mid - 1] + nanos[mid]) / 2.0;
    }
}
