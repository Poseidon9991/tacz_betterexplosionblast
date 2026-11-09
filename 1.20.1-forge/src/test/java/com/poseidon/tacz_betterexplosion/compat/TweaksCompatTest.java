package com.poseidon.tacz_betterexplosion.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fake.tweaks.FakeConfig;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleBiFunction;
import org.junit.jupiter.api.Test;

class TweaksCompatTest {

    private static final String PLAYER_DAMAGE = "fake.tweaks.FakeConfig$PlayerDamage";
    private static final ToDoubleBiFunction<Object, Double> HALVE = (modifier, value) -> value * 0.5;

    @Test
    void halvesDamage() {
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, HALVE);
        assertEquals(5.0f, compat.apply(10.0f), 0.0f);
        assertEquals(1.25f, compat.apply(2.5f), 0.0f);
        assertFalse(compat.isDisabled());
        assertEquals(0, compat.warnings());
    }

    @Test
    void resolvesOnceAndReadsTheModifierPerCall() {
        AtomicInteger evaluations = new AtomicInteger();
        // The evaluator receives whatever toTaCZ() returns right now; the fake config can change at runtime.
        ToDoubleBiFunction<Object, Double> useModifier = (modifier, value) -> {
            evaluations.incrementAndGet();
            return (Double) modifier * value;
        };
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, useModifier);
        FakeConfig.PlayerDamage.INSTANCE.multiplier = 0.5;
        try {
            assertEquals(5.0f, compat.apply(10.0f), 0.0f);
            TweaksCompat.Resolved first = compat.resolved();
            assertNotNull(first);
            FakeConfig.PlayerDamage.INSTANCE.multiplier = 3.0;
            assertEquals(30.0f, compat.apply(10.0f), 0.0f);
            assertSame(first, compat.resolved(), "class lookup must happen once");
            assertEquals(2, evaluations.get());
        } finally {
            FakeConfig.PlayerDamage.INSTANCE.multiplier = 1.0;
        }
    }

    @Test
    void resolveBuildsHandleFromInstanceAndSuperclassMethod() throws Exception {
        TweaksCompat.Resolved handle = TweaksCompat.resolve(PLAYER_DAMAGE, HALVE);
        assertSame(FakeConfig.PlayerDamage.INSTANCE, handle.instance());
        assertEquals("toTaCZ", handle.toTaCZ().getName());
        assertEquals(FakeConfig.ModifierConfig.class, handle.toTaCZ().getDeclaringClass());
        assertEquals(4.0, handle.eval(8.0), 0.0);
    }

    @Test
    void missingClassLeavesDamageUnchangedAndDisablesOnce() {
        TweaksCompat compat = new TweaksCompat("fake.tweaks.DoesNotExist$PlayerDamage", HALVE);
        assertEquals(10.0f, compat.apply(10.0f), 0.0f);
        assertTrue(compat.isDisabled());
        assertEquals(1, compat.warnings());
        assertEquals(7.0f, compat.apply(7.0f), 0.0f);
        assertEquals(1, compat.warnings(), "second call must not log again");
    }

    @Test
    void missingMembersDisable() {
        for (String className : new String[] {
                "fake.tweaks.FakeConfig$NoInstance", "fake.tweaks.FakeConfig$NoMethod",
                "fake.tweaks.FakeConfig$NullInstance"}) {
            TweaksCompat compat = new TweaksCompat(className, HALVE);
            assertEquals(10.0f, compat.apply(10.0f), 0.0f, className);
            assertTrue(compat.isDisabled(), className);
            assertEquals(1, compat.warnings(), className);
        }
    }

    @Test
    void resolveReportsMissingMembers() {
        assertThrows(ReflectiveOperationException.class,
                () -> TweaksCompat.resolve("fake.tweaks.DoesNotExist", HALVE));
        assertThrows(NoSuchFieldException.class,
                () -> TweaksCompat.resolve("fake.tweaks.FakeConfig$NoInstance", HALVE));
        assertThrows(NoSuchMethodException.class,
                () -> TweaksCompat.resolve("fake.tweaks.FakeConfig$NoMethod", HALVE));
        assertThrows(IllegalStateException.class,
                () -> TweaksCompat.resolve("fake.tweaks.FakeConfig$NullInstance", HALVE));
    }

    @Test
    void throwingToTaczDisables() {
        TweaksCompat compat = new TweaksCompat("fake.tweaks.FakeConfig$ThrowingPlayerDamage", HALVE);
        assertEquals(10.0f, compat.apply(10.0f), 0.0f);
        assertTrue(compat.isDisabled());
        assertEquals(1, compat.warnings());
        assertEquals(4.0f, compat.apply(4.0f), 0.0f);
        assertEquals(1, compat.warnings());
    }

    @Test
    void throwingEvaluatorDisablesAndStopsCalling() {
        AtomicInteger calls = new AtomicInteger();
        ToDoubleBiFunction<Object, Double> boom = (modifier, value) -> {
            calls.incrementAndGet();
            throw new NoClassDefFoundError("com/tacz/guns/resource/modifier/AttachmentPropertyManager");
        };
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, boom);
        assertEquals(10.0f, compat.apply(10.0f), 0.0f);
        assertTrue(compat.isDisabled());
        assertEquals(1, compat.warnings());
        assertEquals(10.0f, compat.apply(10.0f), 0.0f);
        assertEquals(1, calls.get(), "a disabled compat must not call the evaluator again");
        assertEquals(1, compat.warnings());
    }

    @Test
    void classCastInEvaluatorDisables() {
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, (modifier, value) -> ((String) modifier).length());
        assertEquals(10.0f, compat.apply(10.0f), 0.0f);
        assertTrue(compat.isDisabled());
    }

    @Test
    void nanResultFallsBackToOriginalAmount() {
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, (modifier, value) -> Double.NaN);
        assertEquals(10.0f, compat.apply(10.0f), 0.0f);
        assertFalse(compat.isDisabled());
        assertEquals(0, compat.warnings());
    }

    @Test
    void negativeResultClampsToZero() {
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, (modifier, value) -> -3.0);
        assertEquals(0.0f, compat.apply(10.0f), 0.0f);
        assertFalse(compat.isDisabled());
    }

    @Test
    void positiveInfinityResultPassesThroughUnchanged() {
        // Tweaks parity: the evaluator result is not capped, only clamped at zero from below.
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, (modifier, value) -> Double.POSITIVE_INFINITY);
        assertEquals(Float.POSITIVE_INFINITY, compat.apply(10.0f), 0.0f);
        assertFalse(compat.isDisabled());
        assertEquals(0, compat.warnings());
    }

    @Test
    void negativeInfinityResultClampsToZero() {
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, (modifier, value) -> Double.NEGATIVE_INFINITY);
        assertEquals(0.0f, compat.apply(10.0f), 0.0f);
        assertFalse(compat.isDisabled());
        assertEquals(0, compat.warnings());
    }

    @Test
    void nonFiniteAmountIsReturnedUntouchedWithoutEvaluating() {
        AtomicInteger calls = new AtomicInteger();
        TweaksCompat compat = new TweaksCompat(PLAYER_DAMAGE, (modifier, value) -> {
            calls.incrementAndGet();
            return 1.0;
        });
        assertTrue(Float.isNaN(compat.apply(Float.NaN)));
        assertEquals(Float.POSITIVE_INFINITY, compat.apply(Float.POSITIVE_INFINITY), 0.0f);
        assertEquals(Float.NEGATIVE_INFINITY, compat.apply(Float.NEGATIVE_INFINITY), 0.0f);
        assertEquals(0, calls.get());
        assertNull(compat.resolved(), "a rejected amount must not trigger resolution");
        assertFalse(compat.isDisabled());
    }

    @Test
    void publicEntryPointIsIdentityWithoutAModList() {
        // Plain unit-test JVM: no Forge loading context, so ModList.get() is null.
        assertEquals(10.0f, TweaksCompat.playerDamage(10.0f), 0.0f);
        assertEquals(0.0f, TweaksCompat.playerDamage(0.0f), 0.0f);
        assertTrue(Float.isNaN(TweaksCompat.playerDamage(Float.NaN)));
    }
}
