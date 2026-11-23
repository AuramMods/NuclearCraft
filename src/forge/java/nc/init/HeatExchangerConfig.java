package nc.init;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class HeatExchangerConfig {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue MIN_SIZE;
    private static final ForgeConfigSpec.IntValue MAX_SIZE;
    private static final ForgeConfigSpec.DoubleValue[] CONDUCTIVITY;
    private static final ForgeConfigSpec.DoubleValue[] RETENTION;
    private static final ForgeConfigSpec.DoubleValue GAS_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue NAK_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue NAK_HEAT;
    private static final ForgeConfigSpec.BooleanValue LOG_MEAN;
    private static final ForgeConfigSpec.BooleanValue ALT_HIGH_PRESSURE;
    private static final ForgeConfigSpec.BooleanValue ALT_EXHAUST;

    static {
        var builder = new ForgeConfigSpec.Builder();
        MIN_SIZE = builder.comment("Minimum interior length on each axis").defineInRange("min_size", 1, 1, 255);
        MAX_SIZE = builder.comment("Maximum interior length on each axis").defineInRange("max_size", 24, 2, 255);
        builder.push("conductivity");
        CONDUCTIVITY = new ForgeConfigSpec.DoubleValue[]{
                builder.defineInRange("copper", 16D, 0.001D, Integer.MAX_VALUE),
                builder.defineInRange("hard_carbon", 24D, 0.001D, Integer.MAX_VALUE),
                builder.defineInRange("thermoconducting", 32D, 0.001D, Integer.MAX_VALUE)
        };
        builder.pop().push("retention");
        RETENTION = new ForgeConfigSpec.DoubleValue[]{
                builder.defineInRange("copper", 0.9D, 0.01D, 1D),
                builder.defineInRange("hard_carbon", 0.95D, 0.01D, 1D),
                builder.defineInRange("thermoconducting", 1D, 0.01D, 1D)
        };
        builder.pop();
        GAS_MULTIPLIER = builder.defineInRange("gas_coolant_heat_multiplier", 1D, 0.001D, Integer.MAX_VALUE);
        NAK_MULTIPLIER = builder.defineInRange("nak_coolant_heat_multiplier", 2D, 0.001D, Integer.MAX_VALUE);
        NAK_HEAT = builder.defineInRange("nak_coolant_heat_per_mb", 128D, 0.001D, Integer.MAX_VALUE);
        LOG_MEAN = builder.define("logarithmic_mean_temperature_difference", false);
        ALT_HIGH_PRESSURE = builder.define("alternate_high_pressure_steam_recipe", false);
        ALT_EXHAUST = builder.define("alternate_exhaust_steam_recipe", false);
        SPEC = builder.build();
    }

    private HeatExchangerConfig() {}

    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-heat-exchangers.toml");
    }

    public static int minSize() { return value(MIN_SIZE); }
    public static int maxSize() { return value(MAX_SIZE); }
    public static double conductivity(int tube) { return value(CONDUCTIVITY[tube]); }
    public static double retention(int tube) { return value(RETENTION[tube]); }
    public static double gasHeat() { return NCPebbleFission.heatPerMb() * value(GAS_MULTIPLIER); }
    public static double nakHeat() { return value(NAK_HEAT) * value(NAK_MULTIPLIER); }
    public static boolean alternateHighPressure() { return value(ALT_HIGH_PRESSURE); }
    public static boolean alternateExhaust() { return value(ALT_EXHAUST); }

    public static double meanDifference(double start, double end) {
        if (!value(LOG_MEAN) || start == end) {
            return Math.abs((start + end) / 2D);
        }
        if (start == 0D || end == 0D) {
            return 0D;
        }
        double first = Math.abs(start);
        double second = Math.abs(end);
        return (first - second) / Math.log(first / second);
    }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
