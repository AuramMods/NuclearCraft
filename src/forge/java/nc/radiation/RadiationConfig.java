package nc.radiation;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;
import java.util.UUID;

public final class RadiationConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER.define("radiation_enabled", true);
    static final ForgeConfigSpec.IntValue UPDATE_TICKS =
            BUILDER.defineInRange("radiation_player_tick_rate", 5, 1, 400);
    static final ForgeConfigSpec.DoubleValue MAX_DOSE =
            BUILDER.defineInRange("max_player_rads", 1000D, 1D, 1E9);
    static final ForgeConfigSpec.DoubleValue DECAY =
            BUILDER.defineInRange("radiation_player_decay_rate", 5E-7, 0D, 1D);
    static final ForgeConfigSpec.DoubleValue WATER_MULTIPLIER =
            BUILDER.defineInRange("radiation_swim_mult", 2D, 1E-6, 1E6);
    static final ForgeConfigSpec.DoubleValue RAIN_MULTIPLIER =
            BUILDER.defineInRange("radiation_rain_mult", 1D, 1E-6, 1E6);
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> IMMUNE_PLAYERS =
            BUILDER.defineListAllowEmpty("radiation_immune_players", List.of(), RadiationConfig::validUuid);
    static final ForgeConfigSpec.BooleanValue FATAL = BUILDER.define("radiation_player_rads_fatal", true);
    static final ForgeConfigSpec.BooleanValue PERSIST_DEATH = BUILDER.define("radiation_death_persist", true);
    static final ForgeConfigSpec.DoubleValue DEATH_FRACTION =
            BUILDER.defineInRange("radiation_death_persist_fraction", 0.75D, 0D, 1D);
    static final ForgeConfigSpec.DoubleValue DEATH_IMMUNITY_SECONDS =
            BUILDER.defineInRange("radiation_death_immunity_time", 90D, 0D, 3600D);
    static final ForgeConfigSpec.DoubleValue RADAWAY_AMOUNT =
            BUILDER.defineInRange("radiation_radaway_amount", 300D, 0.001D, 1E9);
    static final ForgeConfigSpec.DoubleValue RADAWAY_RATE =
            BUILDER.defineInRange("radiation_radaway_rate", 5D, 0.001D, 1E9);
    static final ForgeConfigSpec.DoubleValue SLOW_RADAWAY_AMOUNT =
            BUILDER.defineInRange("radiation_radaway_slow_amount", 300D, 0.001D, 1E9);
    static final ForgeConfigSpec.DoubleValue SLOW_RADAWAY_RATE =
            BUILDER.defineInRange("radiation_radaway_slow_rate", 0.025D, 1E-5, 1E7);
    static final ForgeConfigSpec.DoubleValue RADAWAY_COOLDOWN =
            BUILDER.defineInRange("radiation_radaway_cooldown", 0D, 0D, 100_000D);
    static final ForgeConfigSpec.DoubleValue RAD_X_AMOUNT =
            BUILDER.defineInRange("radiation_rad_x_amount", 25D, 0.001D, 1E9);
    static final ForgeConfigSpec.DoubleValue RAD_X_LIFETIME =
            BUILDER.defineInRange("radiation_rad_x_lifetime", 12000D, 20D, 1E9);
    static final ForgeConfigSpec.DoubleValue RAD_X_COOLDOWN =
            BUILDER.defineInRange("radiation_rad_x_cooldown", 0D, 0D, 100_000D);
    private static final ForgeConfigSpec SPEC = BUILDER.build();

    private RadiationConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-radiation.toml");
    }

    private static boolean validUuid(Object value) {
        if (!(value instanceof String string)) {
            return false;
        }
        try {
            return UUID.fromString(string).toString().equals(string);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
