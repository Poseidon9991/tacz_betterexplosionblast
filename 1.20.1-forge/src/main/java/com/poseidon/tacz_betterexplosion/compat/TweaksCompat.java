package com.poseidon.tacz_betterexplosion.compat;

import com.poseidon.tacz_betterexplosion.TaczBetterExplosion;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.ToDoubleBiFunction;

/**
 * Soft compatibility with TaCZ Tweaks. Tweaks wraps the {@code hurt} call in TaCZ's explosion so that
 * players take damage run through a configurable modifier; our damage pass bypasses that wrapper, so
 * {@link #playerDamage} applies the same modifier. Tweaks is reached by reflection only (no compile
 * dependency); any failure disables the compat after one warning and damage stays unmodified.
 */
public final class TweaksCompat {

    static final String MOD_ID = "tacztweaks";
    static final String PLAYER_DAMAGE_CLASS = "me.muksc.tacztweaks.config.Config$Modifiers$PlayerDamage";

    private static final TweaksCompat PLAYER_DAMAGE = new TweaksCompat(PLAYER_DAMAGE_CLASS, TweaksCompat::taczEval);

    private final String className;
    private final ToDoubleBiFunction<Object, Double> evaluator;
    private volatile Resolved resolved;
    private volatile boolean disabled;
    private volatile int warnings;

    TweaksCompat(String className, ToDoubleBiFunction<Object, Double> evaluator) {
        this.className = className;
        this.evaluator = evaluator;
    }

    /**
     * Player damage after the TaCZ Tweaks player-damage modifier. Returns {@code amount} unchanged when
     * Tweaks is not loaded, the compat is disabled, or {@code amount} is not finite. Never throws.
     */
    public static float playerDamage(float amount) {
        if (PLAYER_DAMAGE.disabled || !Float.isFinite(amount)) {
            return amount;
        }
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded(MOD_ID)) {
            return amount;
        }
        return PLAYER_DAMAGE.apply(amount);
    }

    /** What TaCZ Tweaks' wrapper does: {@code AttachmentPropertyManager.eval(modifier, amount)}. */
    private static double taczEval(Object modifier, Double amount) {
        return AttachmentPropertyManager.eval((Modifier) modifier, amount);
    }

    /** Applies the modifier to {@code amount}; resolves lazily, and never throws. */
    float apply(float amount) {
        if (disabled || !Float.isFinite(amount)) {
            return amount;
        }
        try {
            Resolved handle = resolved;
            if (handle == null) {
                handle = resolve(className, evaluator);
                resolved = handle;
            }
            double result = handle.eval(amount);
            return Double.isNaN(result) ? amount : Math.max(0.0f, (float) result);
        } catch (Throwable e) {
            // Reflection into another mod can fail in many ways (including linkage errors); never let one escape.
            disable(e);
            return amount;
        }
    }

    private synchronized void disable(Throwable cause) {
        if (disabled) {
            return;
        }
        disabled = true;
        warnings++;
        // Reflective calls wrap the real failure in InvocationTargetException; show the real one in the message.
        Throwable shown = cause instanceof InvocationTargetException && cause.getCause() != null
                ? cause.getCause() : cause;
        TaczBetterExplosion.LOGGER.warn("TaCZ Tweaks player-damage compat disabled: {}", shown.toString(), cause);
    }

    boolean isDisabled() {
        return disabled;
    }

    /** Number of warnings logged; at most one. */
    int warnings() {
        return warnings;
    }

    Resolved resolved() {
        return resolved;
    }

    /**
     * Looks up the singleton {@code INSTANCE} of the named class and its public no-arg {@code toTaCZ} method.
     *
     * @throws ReflectiveOperationException if the class, field or method is missing or inaccessible
     * @throws IllegalStateException        if {@code INSTANCE} is null
     */
    static Resolved resolve(String className, ToDoubleBiFunction<Object, Double> evaluator)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(className, false, TweaksCompat.class.getClassLoader());
        Object instance = type.getField("INSTANCE").get(null);
        if (instance == null) {
            throw new IllegalStateException(className + ".INSTANCE is null");
        }
        Method toTaCZ = type.getMethod("toTaCZ");
        return new Resolved(instance, toTaCZ, evaluator);
    }

    /** Reflection handle: the config singleton, its {@code toTaCZ} method and the modifier evaluator. */
    record Resolved(Object instance, Method toTaCZ, ToDoubleBiFunction<Object, Double> evaluator) {

        /** Evaluates the config's current modifier (read on every call, the config can change) on {@code amount}. */
        double eval(double amount) throws ReflectiveOperationException {
            Object modifier = toTaCZ.invoke(instance);
            return evaluator.applyAsDouble(modifier, amount);
        }
    }
}
