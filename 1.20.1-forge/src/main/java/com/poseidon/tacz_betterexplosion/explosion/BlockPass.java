package com.poseidon.tacz_betterexplosion.explosion;

import com.tacz.guns.util.block.ProjectileExplosion;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

/**
 * Runs TaCZ's own {@link ProjectileExplosion} for the block behaviour only. A marker subclass lets
 * {@link #onDetonate} empty the entity list of that explosion, so TaCZ does no entity damage and
 * {@link DamagePass} is the only thing that hurts entities.
 *
 * <p>Register this class on {@link MinecraftForge#EVENT_BUS} once (from the mod constructor).
 */
public final class BlockPass {

    private BlockPass() {
    }

    /**
     * First half, as in vanilla and TaCZ: posts {@code ExplosionEvent.Start} and runs {@code explode()},
     * which computes the affected blocks but changes nothing in the world yet. Entities are hurt between
     * this and {@link #finish}, so walls the explosion breaks still shield them and the drops and XP
     * spawned by {@link #finish} are never hurt.
     *
     * @return the started explosion to hand to {@link #finish}, or empty when {@code ExplosionEvent.Start}
     *         was cancelled, in which case nothing else may run
     */
    public static Optional<Explosion> begin(ExplosionContext ctx) {
        Explosion.BlockInteraction interaction =
                ctx.destroyBlock() ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.KEEP;
        BlockOnlyExplosion explosion = new BlockOnlyExplosion(ctx, interaction);
        if (ForgeEventFactory.onExplosionStart(ctx.level(), explosion)) {
            return Optional.empty();
        }
        explosion.explode();
        return Optional.of(explosion);
    }

    /** Second half: destroys the blocks computed by {@link #begin}, spawns drops and fire. */
    public static void finish(Explosion started) {
        started.finalizeExplosion(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (event.getExplosion() instanceof BlockOnlyExplosion) {
            event.getAffectedEntities().clear();
        }
    }

    /** Marker type: identical to TaCZ's explosion, recognised by {@link #onDetonate}. */
    private static final class BlockOnlyExplosion extends ProjectileExplosion {
        BlockOnlyExplosion(ExplosionContext ctx, Explosion.BlockInteraction interaction) {
            super(ctx.level(), ctx.owner(), ctx.exploder(), null, null,
                    ctx.pos().x, ctx.pos().y, ctx.pos().z, ctx.damage(), ctx.radius(), ctx.knockback(), interaction);
        }
    }
}
