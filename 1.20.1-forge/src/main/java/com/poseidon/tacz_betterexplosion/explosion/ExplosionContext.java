package com.poseidon.tacz_betterexplosion.explosion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The arguments of one TaCZ {@code ExplodeUtil.createExplosion} call, passed through the passes.
 *
 * @param level        the level the explosion happens in
 * @param owner        the shooter, null when unknown
 * @param exploder     the bullet that exploded
 * @param damage       TaCZ explosion damage (centre damage before our multipliers)
 * @param radius       TaCZ explosion radius
 * @param knockback    the gunpack knockback flag
 * @param destroyBlock whether the explosion breaks blocks
 * @param pos          the explosion centre
 */
public record ExplosionContext(ServerLevel level, @Nullable Entity owner, Entity exploder, float damage,
                               float radius, boolean knockback, boolean destroyBlock, Vec3 pos) {
}
