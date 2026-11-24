package nc.init;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class TurbineVisualConfig {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue BLADE_WIDTH;
    private static final ForgeConfigSpec.BooleanValue FAST_BLADES;
    private static final ForgeConfigSpec.DoubleValue EXPANSION;
    private static final ForgeConfigSpec.DoubleValue SPEED;
    private static final ForgeConfigSpec.DoubleValue PARTICLES;
    private static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;

    static {
        var builder = new ForgeConfigSpec.Builder();
        BLADE_WIDTH = builder.defineInRange("render_blade_width", Math.sqrt(2D), 0.01D, 4D);
        FAST_BLADES = builder.define("render_blade_fast", false);
        EXPANSION = builder.defineInRange("render_rotor_expansion", 4D, 1D, 15D);
        SPEED = builder.defineInRange("render_rotor_speed", 1D, 0D, 15D);
        PARTICLES = builder.defineInRange("particles", 0.025D, 0D, 1D);
        SOUND_VOLUME = builder.defineInRange("sound_volume", 1D, 0D, 15D);
        SPEC = builder.build();
    }

    private TurbineVisualConfig() {}

    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-turbine.toml");
    }

    public static double bladeWidth() { return value(BLADE_WIDTH); }
    public static boolean fastBlades() { return value(FAST_BLADES); }
    public static double expansion() { return value(EXPANSION); }
    public static double rotorSpeed() { return value(SPEED); }
    public static double particles() { return value(PARTICLES); }
    public static double soundVolume() { return value(SOUND_VOLUME); }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> setting) {
        return SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }
}
