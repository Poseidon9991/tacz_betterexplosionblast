package com.poseidon.tacz_betterexplosion.explosion;

import com.poseidon.tacz_betterexplosion.compat.TweaksCompat;
import com.poseidon.tacz_betterexplosion.config.ServerConfig;
import com.tacz.guns.config.common.AmmoConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Hurts and knocks back entities with {@link DamageModel}; TaCZ's own entity damage is switched off by
 * {@link BlockPass}. Like TaCZ's {@code ProjectileExplosion.explode()}, knockback applies to every entity
 * except spectators (not only living ones); dampening by enchantments applies to living entities only.
 * Flying creative players are skipped for the player-sync map in TaCZ; here they get no knockback at all
 * (stricter: TaCZ still changes their server-side motion).
 */
public final class DamagePass {

    private DamagePass() {
    }

    public static void run(ExplosionContext ctx) {
        ServerLevel level = ctx.level();
        Vec3 pos = ctx.pos();
        DamageModel.Params params = new DamageModel.Params(ServerConfig.reachMultiplier(),
                ServerConfig.coverFloor(), ServerConfig.damageMultiplier(), ServerConfig.knockbackMultiplier());
        double reach = DamageModel.reach(ctx.radius(), params.reachMultiplier());
        if (reach <= 0.0) {
            return;
        }

        List<Entity> candidates = new ArrayList<>(
                level.getEntities(ctx.exploder(), new AABB(pos, pos).inflate(reach + 1.0)));
        candidates.removeIf(Entity::ignoreExplosion);

        DamageSource source = level.damageSources().explosion(ctx.exploder(), ctx.owner());
        // Carrier only: lets claim and protection mods trim the target list through the Detonate event.
        ForgeEventFactory.onExplosionDetonate(level, new DamageExplosion(ctx, source), candidates, reach * 2.0);

        List<Targets.Ranked<Entity>> targets = Targets.rankedNearest(candidates, entity -> distanceTo(pos, entity),
                ServerConfig.maxTargets());

        boolean knockbackEnabled = ctx.knockback() && AmmoConfig.EXPLOSIVE_AMMO_KNOCK_BACK.get();
        for (Targets.Ranked<Entity> target : targets) {
            Entity entity = target.item();
            double distance = target.distance();
            double seen = Explosion.getSeenPercent(pos, entity);
            double p = DamageModel.blastFactor(ctx.radius(), distance, seen, params);
            if (p <= 0.0) {
                continue;
            }

            float amount = (float) DamageModel.damage(ctx.damage(), p, params);
            if (amount > 0.0f && entity instanceof Player) {
                // Same as TaCZ Tweaks' wrapper around TaCZ's hurt call: players only.
                amount = TweaksCompat.playerDamage(amount);
            }
            if (amount > 0.0f) {
                // Direct hits: the bullet's own hit must not shield the target from the explosion.
                // A hit that Tweaks zeroed skips this, so it never strips the player's i-frames.
                entity.invulnerableTime = 0;
                entity.hurt(source, amount);
            }

            if (knockbackEnabled && canBePushed(entity)) {
                applyKnockback(ctx, params, entity, p);
            }
        }
    }

    /** Distance from the centre to the closest point of the entity's bounding box. */
    private static double distanceTo(Vec3 pos, Entity entity) {
        return Math.sqrt(entity.getBoundingBox().distanceToSqr(pos));
    }

    private static boolean canBePushed(Entity entity) {
        if (entity.isSpectator()) {
            return false;
        }
        if (entity instanceof Player player) {
            return !(player.isCreative() && player.getAbilities().flying);
        }
        return true;
    }

    private static void applyKnockback(ExplosionContext ctx, DamageModel.Params params, Entity entity,
                                       double blastFactor) {
        Vec3 pos = ctx.pos();
        Vec3 centre = entity.getBoundingBox().getCenter();
        double[] direction = DamageModel.direction(centre.x - pos.x, centre.y - pos.y, centre.z - pos.z);
        if (direction[0] == 0.0 && direction[1] == 0.0 && direction[2] == 0.0) {
            return;
        }
        double dampened = entity instanceof LivingEntity living
                ? ProtectionEnchantment.getExplosionKnockbackAfterDampener(living, blastFactor)
                : blastFactor;
        double magnitude = DamageModel.knockback(ctx.damage(), ctx.radius(), dampened, params);
        if (!(magnitude > 0.0)) {
            // Zero (or NaN) push: leave the motion alone and do not resync the entity.
            return;
        }
        entity.setDeltaMovement(entity.getDeltaMovement()
                .add(direction[0] * magnitude, direction[1] * magnitude, direction[2] * magnitude));
        if (entity instanceof ServerPlayer) {
            entity.hurtMarked = true;
        }
    }

    /** Event carrier for {@link ForgeEventFactory#onExplosionDetonate}; never explodes. */
    private static final class DamageExplosion extends Explosion {
        DamageExplosion(ExplosionContext ctx, DamageSource source) {
            super(ctx.level(), ctx.exploder(), source, null,
                    ctx.pos().x, ctx.pos().y, ctx.pos().z, ctx.radius(), false, Explosion.BlockInteraction.KEEP);
        }
    }
}
