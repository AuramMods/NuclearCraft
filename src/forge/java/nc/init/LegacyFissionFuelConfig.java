package nc.init;

import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LegacyFissionFuelConfig {
    public enum Family {
        THORIUM(5), URANIUM(20), NEPTUNIUM(10), PLUTONIUM(20), MIXED(10),
        AMERICIUM(10), CURIUM(30), BERKELIUM(10), CALIFORNIUM(20);

        private final int entries;

        Family(int entries) {
            this.entries = entries;
        }
    }

    public enum Setting {
        FUEL_TIME(1, Integer.MAX_VALUE, true),
        HEAT_GENERATION(0, 32_767, true),
        EFFICIENCY(0, 32_767, false),
        CRITICALITY(0, 32_767, true),
        INTRINSIC_FLUX(0, 32_767, true),
        DECAY_FACTOR(0, 1, false),
        SELF_PRIMING(0, 0, false),
        RADIATION(0, 1_000, false);

        private final double minimum;
        private final double maximum;
        private final boolean integral;

        Setting(double minimum, double maximum, boolean integral) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.integral = integral;
        }

        private boolean valid(Object entry) {
            if (this == SELF_PRIMING) {
                return entry instanceof Boolean;
            }
            if (entry instanceof Number number) {
                double value = number.doubleValue();
                return value >= minimum && value <= maximum && (!integral || Math.rint(value) == value);
            }
            return false;
        }
    }

    private final Map<Family, Map<Setting, ForgeConfigSpec.ConfigValue<List<?>>>> arrays = new EnumMap<>(Family.class);

    LegacyFissionFuelConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Legacy fission fuel arrays in their original order. Missing entries use the recipe defaults.")
                .push("fuel");
        for (Family family : Family.values()) {
            var settings = new EnumMap<Setting, ForgeConfigSpec.ConfigValue<List<?>>>(Setting.class);
            arrays.put(family, settings);
            for (Setting setting : Setting.values()) {
                String key = "fission_" + family.name().toLowerCase(Locale.ROOT)
                        + "_" + setting.name().toLowerCase(Locale.ROOT);
                // Validate the array as a whole so correction cannot move a value to a different fuel.
                settings.put(setting, builder.define(key, List.of(),
                        value -> value instanceof List<?> list && list.stream().allMatch(setting::valid)));
            }
        }
        builder.pop();
    }

    @Nullable Object value(Selection selection, Setting setting) {
        List<?> list = arrays.get(selection.family).get(setting).get();
        return selection.index < list.size() ? list.get(selection.index) : null;
    }

    public enum Profile {
        STANDARD, YELLORIUM;

        private double scale(Setting setting, double value) {
            if (this != YELLORIUM) {
                return value;
            }
            return switch (setting) {
                case FUEL_TIME -> value * 9D / 16D;
                case HEAT_GENERATION, RADIATION -> value * 8D / 9D;
                default -> value;
            };
        }
    }

    public record Selection(Family family, int index, Profile profile) {
        public Selection(Family family, int index) {
            this(family, index, Profile.STANDARD);
        }

        public Selection {
            if (index < 0 || index >= family.entries) {
                throw new IllegalArgumentException("Fuel index " + index + " is outside the " + family + " array");
            }
        }

        public double number(Setting setting, double fallback) {
            Object value = NCFission.legacyFuelValue(this, setting);
            return value instanceof Number number ? profile.scale(setting, number.doubleValue()) : fallback;
        }

        public boolean selfPriming(boolean fallback) {
            if (profile == Profile.YELLORIUM) {
                return false;
            }
            Object value = NCFission.legacyFuelValue(this, Setting.SELF_PRIMING);
            return value instanceof Boolean enabled ? enabled : fallback;
        }
    }
}
