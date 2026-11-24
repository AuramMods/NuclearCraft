package nc.init;

import com.mojang.logging.LogUtils;
import nc.multiblock.turbine.TurbineDynamoRules;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class TurbineConfig {
    public static final int MAX_INTERIOR_SIZE = 255;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> DEFAULT_COILS = List.of("one bearing || one connector", "one magnesium coil",
            "two magnesium coils", "one aluminum coil", "one beryllium coil", "one gold coil && one copper coil");
    private static final List<String> DEFAULT_CONNECTOR = List.of("one of any coil");
    private static final AtomicLong REVISION = new AtomicLong();
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue MIN_SIZE;
    private static final ForgeConfigSpec.IntValue MAX_SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> BLADE_EFFICIENCY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> BLADE_EXPANSION;
    private static final ForgeConfigSpec.DoubleValue STATOR_EXPANSION;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> COIL_CONDUCTIVITY;
    private static final ForgeConfigSpec.ConfigValue<List<String>> COIL_RULE;
    private static final ForgeConfigSpec.ConfigValue<List<String>> CONNECTOR_RULE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> RECIPE_POWER;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> RECIPE_EXPANSION;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> RECIPE_SPIN_UP;
    private static final ForgeConfigSpec.DoubleValue GLOBAL_SPIN_UP;
    private static final ForgeConfigSpec.DoubleValue SPIN_DOWN;
    private static final ForgeConfigSpec.IntValue MB_PER_BLADE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> THROUGHPUT_LENIENCY;
    private static final ForgeConfigSpec.DoubleValue TENSION_THROUGHPUT_FACTOR;
    private static final ForgeConfigSpec.DoubleValue TENSION_LENIENCY;
    private static final ForgeConfigSpec.DoubleValue POWER_BONUS;
    private static final ForgeConfigSpec.IntValue BASE_ENERGY_CAPACITY;

    private static final ForgeConfigSpec.DoubleValue BLADE_WIDTH;
    private static final ForgeConfigSpec.BooleanValue FAST_BLADES;
    private static final ForgeConfigSpec.DoubleValue EXPANSION;
    private static final ForgeConfigSpec.DoubleValue SPEED;
    private static final ForgeConfigSpec.DoubleValue PARTICLES;
    private static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;

    private static final TurbineDynamoRules DEFAULT_RULES = compileRules(DEFAULT_COILS, DEFAULT_CONNECTOR);
    private static volatile TurbineDynamoRules rules = DEFAULT_RULES;

    static {
        var builder = new ForgeConfigSpec.Builder();
        MIN_SIZE = builder.defineInRange("min_size", 1, 1, MAX_INTERIOR_SIZE);
        MAX_SIZE = builder.defineInRange("max_size", 24, 3, MAX_INTERIOR_SIZE);
        BLADE_EFFICIENCY = array(builder, "blade_efficiency", "Steel, extreme alloy, SiC-SiC CMC.",
                List.of(1D, 1.1D, 1.2D), 0.01D, 15D);
        BLADE_EXPANSION = array(builder, "blade_expansion", "Steel, extreme alloy, SiC-SiC CMC.",
                List.of(1.4D, 1.6D, 1.8D), 1D, 15D);
        STATOR_EXPANSION = builder.defineInRange("stator_expansion", 0.75D, 0.01D, 1D);
        COIL_CONDUCTIVITY = array(builder, "coil_conductivity", "Magnesium, beryllium, aluminum, gold, copper, silver.",
                List.of(0.88D, 0.9D, 1D, 1.04D, 1.06D, 1.12D), 0.01D, 15D);
        COIL_RULE = ruleArray(builder, "coil_rule", "Magnesium, beryllium, aluminum, gold, copper, silver.", DEFAULT_COILS);
        CONNECTOR_RULE = ruleArray(builder, "connector_rule", "Placement rule for the coil connector.", DEFAULT_CONNECTOR);
        String recipeOrder = "High-pressure steam, low-pressure steam, steam, hot gas.";
        RECIPE_POWER = array(builder, "power_per_mb", recipeOrder, List.of(16D, 4D, 4D, 32D), 0D, 255D);
        RECIPE_EXPANSION = array(builder, "expansion_level", recipeOrder, List.of(4D, 2D, 2D, 2D), 1D, 255D);
        RECIPE_SPIN_UP = array(builder, "spin_up_multiplier", recipeOrder, List.of(1D, 1D, 1D, 1D), 0D, 255D);
        GLOBAL_SPIN_UP = builder.defineInRange("spin_up_multiplier_global", 1D, 0D, 255D);
        SPIN_DOWN = builder.defineInRange("spin_down_multiplier", 1D, 0.01D, 255D);
        MB_PER_BLADE = builder.defineInRange("mb_per_blade", 100, 1, 32767);
        THROUGHPUT_LENIENCY = array(builder, "throughput_leniency_params",
                "Minimum throughput efficiency and throughput fraction at which efficiency reaches one.",
                List.of(0.5D, 0.75D), 0D, 1D);
        TENSION_THROUGHPUT_FACTOR = builder.defineInRange("tension_throughput_factor", 2D, 1D, 255D);
        TENSION_LENIENCY = builder.defineInRange("tension_leniency", 0.05D, 0D, 1D);
        POWER_BONUS = builder.defineInRange("power_bonus_multiplier", 1D, 0D, 255D);
        BASE_ENERGY_CAPACITY = builder.defineInRange("base_energy_capacity", 16000, 1, Integer.MAX_VALUE);

        BLADE_WIDTH = builder.defineInRange("render_blade_width", Math.sqrt(2D), 0.01D, 4D);
        FAST_BLADES = builder.define("render_blade_fast", false);
        EXPANSION = builder.defineInRange("render_rotor_expansion", 4D, 1D, 15D);
        SPEED = builder.defineInRange("render_rotor_speed", 1D, 0D, 15D);
        PARTICLES = builder.defineInRange("particles", 0.025D, 0D, 1D);
        SOUND_VOLUME = builder.defineInRange("sound_volume", 1D, 0D, 15D);
        SPEC = builder.build();
    }

    private TurbineConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener((ModConfigEvent.Loading event) -> changed(event));
        context.getModEventBus().addListener((ModConfigEvent.Reloading event) -> changed(event));
        context.getModEventBus().addListener((ModConfigEvent.Unloading event) -> {
            if (event.getConfig().getSpec() == SPEC) {
                rules = DEFAULT_RULES;
                REVISION.incrementAndGet();
            }
        });
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-turbine.toml");
    }

    private static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            rules = compileRules(value(COIL_RULE), value(CONNECTOR_RULE));
            REVISION.incrementAndGet();
        }
    }

    private static TurbineDynamoRules compileRules(List<String> coils, List<String> connector) {
        return TurbineDynamoRules.compile(coils, connector.get(0), DEFAULT_COILS, DEFAULT_CONNECTOR.get(0), LOGGER::warn);
    }

    public static TurbineDynamoRules dynamoRules() { return rules; }
    public static long revision() { return REVISION.get(); }
    public static int minSize() { return value(MIN_SIZE); }
    public static int maxSize() { return value(MAX_SIZE); }
    public static int searchLength() { return maxSize() + 2; }
    public static double bladeEfficiency(int material) { return number(BLADE_EFFICIENCY, material); }
    public static double bladeExpansion(int material) { return number(BLADE_EXPANSION, material); }
    public static double statorExpansion() { return value(STATOR_EXPANSION); }
    public static double coilConductivity(int material) { return number(COIL_CONDUCTIVITY, material); }
    public static double recipePower(int profile) { return number(RECIPE_POWER, profile); }
    public static double recipeExpansion(int profile) { return number(RECIPE_EXPANSION, profile); }
    public static double recipeSpinUp(int profile) { return number(RECIPE_SPIN_UP, profile); }
    public static double globalSpinUp() { return value(GLOBAL_SPIN_UP); }
    public static double spinDown() { return value(SPIN_DOWN); }
    public static int mbPerBlade() { return value(MB_PER_BLADE); }
    public static double throughputFloor() { return number(THROUGHPUT_LENIENCY, 0); }
    public static double throughputThreshold() { return number(THROUGHPUT_LENIENCY, 1); }
    public static double tensionThroughputFactor() { return value(TENSION_THROUGHPUT_FACTOR); }
    public static double tensionLeniency() { return value(TENSION_LENIENCY); }
    public static double powerBonus() { return value(POWER_BONUS); }
    public static int baseEnergyCapacity() { return value(BASE_ENERGY_CAPACITY); }

    public static double bladeWidth() { return value(BLADE_WIDTH); }
    public static boolean fastBlades() { return value(FAST_BLADES); }
    public static double expansion() { return value(EXPANSION); }
    public static double rotorSpeed() { return value(SPEED); }
    public static double particles() { return value(PARTICLES); }
    public static double soundVolume() { return value(SOUND_VOLUME); }

    private static ForgeConfigSpec.ConfigValue<List<String>> ruleArray(ForgeConfigSpec.Builder builder,
            String key, String description, List<String> defaults) {
        return builder.comment(description).define(key, defaults, candidate -> candidate instanceof List<?> values
                && values.size() == defaults.size() && values.stream().allMatch(String.class::isInstance));
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> array(ForgeConfigSpec.Builder builder,
            String key, String description, List<? extends Number> defaults, double min, double max) {
        return builder.comment(description, "Each value must be between " + min + " and " + max + ".")
                .define(key, defaults, candidate -> candidate instanceof List<?> values
                        && values.size() == defaults.size() && values.stream().allMatch(element ->
                                element instanceof Number number && number.doubleValue() >= min && number.doubleValue() <= max));
    }

    private static double number(ForgeConfigSpec.ConfigValue<List<? extends Number>> setting, int index) {
        return value(setting).get(index).doubleValue();
    }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> setting) {
        return SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }
}
