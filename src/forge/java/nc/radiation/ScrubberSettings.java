package nc.radiation;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.Locale;

public final class ScrubberSettings {
    private static ForgeConfigSpec.BooleanValue nonlinear;
    private static ForgeConfigSpec.DoubleValue maximum;
    private static ForgeConfigSpec.IntValue radius;
    private static ForgeConfigSpec.DoubleValue base;
    private static ForgeConfigSpec.DoubleValue divisor;
    private static ForgeConfigSpec.DoubleValue scale;
    private static ForgeConfigSpec.DoubleValue root;
    private static final EnumMap<Medium, Settings> MEDIA = new EnumMap<>(Medium.class);

    private ScrubberSettings() {}

    static void define(ForgeConfigSpec.Builder builder) {
        maximum = builder.defineInRange("radiation_scrubber_fraction", 0.125D, 0.001D, 1D);
        radius = builder.defineInRange("radiation_scrubber_radius", 4, 1, 10);
        nonlinear = builder.define("radiation_scrubber_non_linear", true);
        builder.push("scrubber");
        base = builder.defineInRange("curve_base", 2.14280951676725D, 1D, 15D);
        divisor = builder.defineInRange("curve_divisor", 3D, 1D, 15D);
        scale = builder.defineInRange("curve_scale", 4D, 1D, 15D);
        root = builder.defineInRange("curve_root", 2D, 1D, 15D);
        for (Medium medium : Medium.values()) {
            builder.push(medium.name().toLowerCase(Locale.ROOT));
            MEDIA.put(medium, new Settings(
                    builder.defineInRange("ticks", medium.ticks, 1, Integer.MAX_VALUE),
                    builder.defineInRange("energy_per_tick", medium.power, 0, Integer.MAX_VALUE),
                    builder.defineInRange("efficiency", medium.efficiency, 0D, 255D)));
            builder.pop();
        }
        builder.pop();
    }

    public static boolean radiationEnabled() {
        return value(RadiationConfig.ENABLED);
    }

    public static int radius() {
        return value(radius);
    }

    static boolean nonlinear() {
        return value(nonlinear);
    }

    public static double maximumFraction() {
        return nonlinear() ? 1D : value(maximum);
    }

    static double nonlinearFraction(double scrubbers) {
        double curveBase = value(base);
        if (curveBase == 1 || scrubbers <= 0) {
            return 0;
        }
        return 1D - Math.pow(curveBase, -Math.pow(scrubbers / value(divisor),
                Math.pow(1D + scrubbers / value(scale), 1D / value(root))));
    }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> value) {
        return RadiationConfig.loaded() ? value.get() : value.getDefault();
    }

    private record Settings(ForgeConfigSpec.IntValue ticks, ForgeConfigSpec.IntValue power,
                            ForgeConfigSpec.DoubleValue efficiency) {}

    public enum Medium {
        BORAX(12000, 200, 1), RADAWAY(2400, 40, 5), SLOW_RADAWAY(96000, 20, 0.25D);

        private final int ticks;
        private final int power;
        private final double efficiency;

        Medium(int ticks, int power, double efficiency) {
            this.ticks = ticks;
            this.power = power;
            this.efficiency = efficiency;
        }

        public int ticks() { return RadiationConfig.loaded() ? MEDIA.get(this).ticks().get() : ticks; }
        public int power() { return RadiationConfig.loaded() ? MEDIA.get(this).power().get() : power; }
        public double efficiency() { return RadiationConfig.loaded() ? MEDIA.get(this).efficiency().get() : efficiency; }
    }
}
