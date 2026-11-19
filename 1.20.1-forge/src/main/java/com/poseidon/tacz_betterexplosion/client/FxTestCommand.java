package com.poseidon.tacz_betterexplosion.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.poseidon.tacz_betterexplosion.client.fx.FxState;
import com.poseidon.tacz_betterexplosion.config.ServerConfig;
import com.poseidon.tacz_betterexplosion.fx.ExplosionFx;
import com.poseidon.tacz_betterexplosion.fx.ExplosionTier;
import com.poseidon.tacz_betterexplosion.fx.ExplosionType;
import com.poseidon.tacz_betterexplosion.projectile.RocketFlightHandler;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client-side dev and inspection commands under {@code /betterexplosion}:
 * {@code test}, {@code testtype}, and {@code listammo}.
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

    /** Explosion type names offered for {@code /betterexplosion testtype}. */
    private static final List<String> TYPE_NAMES =
            Arrays.stream(ExplosionType.values()).map(Enum::name).toList();

    /** Optional trailing literal that sends the explosions with {@link ExplosionFx#FLAG_UNDERWATER}. */
    private static final String UNDERWATER_LITERAL = "underwater";

    /** Blocks ahead of the camera when the crosshair is not on a block. */
    private static final double FALLBACK_DISTANCE = 8.0;

    private FxTestCommand() {
    }

    /** Builds and registers the command tree on the client command dispatcher. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("betterexplosion")
                .then(Commands.literal("listammo")
                        .executes(FxTestCommand::listAmmo))
                .then(Commands.literal("testtype")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(TYPE_NAMES, builder))
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(TIER_NAMES, builder))
                                        .executes(context -> run(context, 1, false))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                                .executes(context -> run(context,
                                                        IntegerArgumentType.getInteger(context, "count"), false))))))
                .then(Commands.literal("test")
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(TIER_NAMES, builder))
                                .executes(context -> run(context, 1, false))
                                .then(Commands.literal(UNDERWATER_LITERAL)
                                        .executes(context -> run(context, 1, true)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                        .executes(context -> run(context,
                                                IntegerArgumentType.getInteger(context, "count"), false))
                                        .then(Commands.literal(UNDERWATER_LITERAL)
                                                .executes(context -> run(context,
                                                        IntegerArgumentType.getInteger(context, "count"), true)))))));
    }

    private static int listAmmo(CommandContext<CommandSourceStack> context) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            context.getSource().sendFailure(Component.literal("[BetterExplosion] Not in a world"));
            return 0;
        }

        List<? extends String> overrides = ServerConfig.ammoTypes();
        List<String> lines = new ArrayList<>();

        ItemStack held = player.getMainHandItem();
        if (IGun.getIGunOrNull(held) == null && IAmmo.getIAmmoOrNull(held) == null) {
            held = player.getOffhandItem();
        }

        IGun heldGun = IGun.getIGunOrNull(held);
        IAmmo heldAmmo = IAmmo.getIAmmoOrNull(held);
        if (heldGun != null) {
            ResourceLocation gunId = heldGun.getGunId(held);
            Optional<CommonGunIndex> idx = TimelessAPI.getCommonGunIndex(gunId);
            ResourceLocation ammoId = idx.map(g -> g.getGunData().getAmmoId()).orElse(null);
            ExplosionData exp = idx.map(g -> g.getBulletData().getExplosionData()).orElse(null);
            boolean explodes = exp != null && exp.isExplode();
            ExplosionType type = ammoId != null ? ExplosionType.classify(ammoId.toString(), overrides) : ExplosionType.STANDARD;
            boolean rocket = RocketFlightHandler.isRocketIds(ammoId, gunId, overrides);
            lines.add(String.format(Locale.ROOT,
                    "[BetterExplosion] Held gun %s -> ammo=%s, type=%s, explodes=%s%s, rocketFlight=%s",
                    gunId,
                    ammoId != null ? ammoId : "unknown",
                    type.name(),
                    explodes,
                    explodes ? String.format(Locale.ROOT, " (r=%.1f)", exp.getRadius()) : "",
                    rocket));
        } else if (heldAmmo != null) {
            ResourceLocation ammoId = heldAmmo.getAmmoId(held);
            ExplosionType type = ExplosionType.classify(ammoId.toString(), overrides);
            boolean rocket = RocketFlightHandler.isRocketIds(ammoId, null, overrides);
            lines.add(String.format(Locale.ROOT,
                    "[BetterExplosion] Held ammo %s -> type=%s, rocketFlight=%s",
                    ammoId, type.name(), rocket));
        }

        Map<String, String> explosiveAmmo = new TreeMap<>();
        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : TimelessAPI.getAllCommonGunIndex()) {
            CommonGunIndex gunIndex = entry.getValue();
            ExplosionData exp = gunIndex.getBulletData().getExplosionData();
            if (exp == null || !exp.isExplode()) {
                continue;
            }
            ResourceLocation gunId = entry.getKey();
            ResourceLocation ammoId = gunIndex.getGunData().getAmmoId();
            if (ammoId == null) {
                continue;
            }
            String key = ammoId.toString();
            ExplosionType type = ExplosionType.classify(key, overrides);
            boolean rocket = RocketFlightHandler.isRocketIds(ammoId, gunId, overrides);
            explosiveAmmo.putIfAbsent(key, String.format(Locale.ROOT,
                    "  %s -> %s (r=%.1f, rocket=%s, e.g. %s)",
                    key, type.name(), exp.getRadius(), rocket ? "yes" : "no", gunId));
        }

        lines.add("[BetterExplosion] Loaded explosive ammo (" + explosiveAmmo.size() + "):");
        lines.addAll(explosiveAmmo.values());
        lines.add("Override in serverconfig/tacz_betterexplosion-server.toml: ammoTypes = [\"namespace:ammo=HEAVY\"]");

        for (String line : lines) {
            player.displayClientMessage(Component.literal(line), false);
        }
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> context, int count, boolean underwater) {
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

        ExplosionType type = ExplosionType.STANDARD;
        try {
            type = ExplosionType.valueOf(StringArgumentType.getString(context, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // "test" has no type argument, and a bad name falls back to STANDARD.
        }
        Target target = target(mc);
        Vec3 pos = target.pos();
        float radius = TEST_RADII.get(tier);
        FxState.resetStats();
        byte flags = ExplosionFx.pack(underwater, type, target.surface());
        long[] nanos = new long[count];
        for (int i = 0; i < count; i++) {
            ExplosionFx fx = new ExplosionFx(pos.x, pos.y, pos.z, radius, flags, new Random().nextInt());
            long start = System.nanoTime();
            ClientFxHandler.handle(fx);
            nanos[i] = System.nanoTime() - start;
        }

        String message = String.format(Locale.ROOT,
                "[BetterExplosion] %s x%d: spawned=%d, handler median=%.2fms, peak live=%d",
                tier.name() + "/" + type.name() + "/surface" + target.surface(), count, FxState.spawnedTotal(), median(nanos) / 1.0e6, FxState.peakLive());
        if (underwater) {
            message += " (underwater)";
        }
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        } else {
            TaczBetterExplosion.LOGGER.info(message);
        }
        return 1;
    }

    /** The explosion position and the packed surface code of the face it was aimed at (0 = air). */
    private record Target(Vec3 pos, int surface) {
    }

    private static Target target(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit) {
            return new Target(blockHit.getLocation(), blockHit.getDirection().get3DDataValue() + 1);
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        Vector3f look = camera.getLookVector();
        return new Target(cam.add(
                look.x() * FALLBACK_DISTANCE, look.y() * FALLBACK_DISTANCE, look.z() * FALLBACK_DISTANCE), 0);
    }

    private static double median(long[] nanos) {
        Arrays.sort(nanos);
        int mid = nanos.length / 2;
        return nanos.length % 2 == 1 ? nanos[mid] : (nanos[mid - 1] + nanos[mid]) / 2.0;
    }
}
