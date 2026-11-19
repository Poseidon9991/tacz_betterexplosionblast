package com.poseidon.tacz_betterexplosion.explosion;

/**
 * Pure explosion maths with no Minecraft types. Any NaN, non-positive radius or non-positive
 * damage input yields 0, so callers never see NaN or an exception from bad data. A negative or NaN
 * damage or knockback multiplier counts as 0.
 *
 * <pre>
 * reach  = radius * reachMultiplier
 * cover  = coverFloor + (1 - coverFloor) * seen
 * p      = (1 - distance / reach) * cover          (0 when distance &gt;= reach)
 * damage = D * damageMultiplier * (2p - p^2)   (ease-out: strong near, soft tail)
 * </pre>
 */
public final class DamageModel {

    private static final double DIRECTION_EPSILON = 1.0e-6;

    private DamageModel() {
    }

    /**
     * @param reachMultiplier     blast reach as a multiple of the explosion radius
     * @param coverFloor          fraction of the blast that always gets through cover, in [0, 1]
     * @param damageMultiplier    scales the final damage; negative or NaN counts as 0
     * @param knockbackMultiplier scales the final knockback; negative or NaN counts as 0
     */
    public record Params(double reachMultiplier, double coverFloor, double damageMultiplier,
                         double knockbackMultiplier) {
    }

    /** Distance at which the blast stops doing damage; 0 for an invalid radius or multiplier. */
    public static double reach(double radius, double reachMultiplier) {
        double reach = radius * reachMultiplier;
        return reach > 0.0 && Double.isFinite(reach) ? reach : 0.0;
    }

    /**
     * Fraction of the blast that reaches the target, in [0, 1].
     *
     * @param distance distance from the centre to the closest point of the target's bounding box
     * @param seen     fraction of the target visible from the centre, in [0, 1]
     */
    public static double blastFactor(double radius, double distance, double seen, Params p) {
        if (!(radius > 0.0) || Double.isNaN(distance) || Double.isNaN(seen)) {
            return 0.0;
        }
        double reach = reach(radius, p.reachMultiplier());
        if (reach <= 0.0) {
            return 0.0;
        }
        double fromDistance = clamp01(1.0 - Math.max(distance, 0.0) / reach);
        double floor = clamp01(p.coverFloor());
        double cover = floor + (1.0 - floor) * clamp01(seen);
        return finiteOrZero(clamp01(fromDistance * cover));
    }

    /** Final damage for a blast factor from {@link #blastFactor}. */
    public static double damage(double damage, double blastFactor, Params p) {
        if (!(damage > 0.0) || !Double.isFinite(damage) || !(blastFactor > 0.0)) {
            return 0.0;
        }
        double factor = clamp01(blastFactor);
        return finiteOrZero(damage * nonNegative(p.damageMultiplier()) * (2.0 * factor - factor * factor));
    }

    /** Knockback magnitude for a blast factor (after any enchantment dampening by the caller). */
    public static double knockback(double damage, double radius, double blastFactor, Params p) {
        if (!(damage > 0.0) || !Double.isFinite(damage) || !(radius > 0.0) || !(blastFactor > 0.0)) {
            return 0.0;
        }
        return finiteOrZero(damage * (2.0 * radius) / 500.0 * clamp01(blastFactor)
                * nonNegative(p.knockbackMultiplier()));
    }

    /** Unit vector of (dx, dy, dz), or {0, 0, 0} when the length is below 1e-6 or not finite. */
    public static double[] direction(double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length >= DIRECTION_EPSILON) || !Double.isFinite(length)) {
            return new double[] {0.0, 0.0, 0.0};
        }
        return new double[] {dx / length, dy / length, dz / length};
    }

    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }

    /** The value itself, or 0 when it is negative or NaN. */
    private static double nonNegative(double value) {
        return value > 0.0 ? value : 0.0;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
