package fake.tweaks;

/** Stand-in for TaCZ Tweaks' {@code Config}: same nesting and shape, no Minecraft or TaCZ types. */
public final class FakeConfig {

    private FakeConfig() {
    }

    /** Like {@code Config$ModifierConfig}: the public {@code toTaCZ()} is declared on an abstract superclass. */
    public abstract static class ModifierConfig {
        /** Mutable on purpose: the real config can change at runtime. */
        public volatile double multiplier = 1.0;

        public Object toTaCZ() {
            return multiplier;
        }
    }

    /** Like {@code Config$Modifiers$PlayerDamage}. */
    public static final class PlayerDamage extends ModifierConfig {
        public static final PlayerDamage INSTANCE = new PlayerDamage();

        private PlayerDamage() {
        }
    }

    /** {@code toTaCZ()} throws. */
    public static final class ThrowingPlayerDamage extends ModifierConfig {
        public static final ThrowingPlayerDamage INSTANCE = new ThrowingPlayerDamage();

        private ThrowingPlayerDamage() {
        }

        @Override
        public Object toTaCZ() {
            throw new IllegalStateException("config not ready");
        }
    }

    /** No {@code INSTANCE} field. */
    public static final class NoInstance extends ModifierConfig {
    }

    /** Has {@code INSTANCE} but no {@code toTaCZ()}. */
    public static final class NoMethod {
        public static final NoMethod INSTANCE = new NoMethod();

        private NoMethod() {
        }
    }

    /** {@code INSTANCE} exists but was never initialised. */
    public static final class NullInstance extends ModifierConfig {
        public static final NullInstance INSTANCE = null;
    }
}
