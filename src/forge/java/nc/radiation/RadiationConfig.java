package nc.radiation;

import net.minecraft.resources.ResourceLocation;
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
    static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK =
            BUILDER.defineInRange("radiation_world_chunks_per_tick", 5, 1, 400);
    static final ForgeConfigSpec.DoubleValue SPREAD =
            BUILDER.defineInRange("radiation_spread_rate", 0.1D, 0D, 1D);
    static final ForgeConfigSpec.DoubleValue GRADIENT =
            BUILDER.defineInRange("radiation_spread_gradient", 0.5D, 0D, 1E9);
    static final ForgeConfigSpec.DoubleValue CHUNK_DECAY =
            BUILDER.defineInRange("radiation_decay_rate", 0.001D, 0D, 1D);
    static final ForgeConfigSpec.DoubleValue MINIMUM_RATE =
            BUILDER.defineInRange("radiation_lowest_rate", 1E-15, 0D, 1D);
    static final ForgeConfigSpec.DoubleValue CHUNK_LIMIT =
            BUILDER.defineInRange("radiation_chunk_limit", -1D, -1D, Double.MAX_VALUE);
    static final ForgeConfigSpec.BooleanValue CHECK_BLOCKS = BUILDER.define("radiation_check_blocks", true);
    static final ForgeConfigSpec.BooleanValue MACHINE_SOURCES = BUILDER.define("radiation_tile_entities", true);
    static final ForgeConfigSpec.BooleanValue INVENTORY_SOURCES = BUILDER.define("radiation_hardcore_stacks", true);
    static final ForgeConfigSpec.BooleanValue DROPPED_SOURCES = BUILDER.define("radiation_dropped_items", true);
    static final ForgeConfigSpec.DoubleValue CONTAINER_SOURCES =
            BUILDER.defineInRange("radiation_hardcore_containers", 0D, 0D, 1D);
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> WORLD_RATES =
            rates("radiation_worlds", List.of("nuclearcraft:wasteland_1.75"));
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> WORLD_LIMITS =
            rates("radiation_world_limits", List.of());
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_RATES =
            rates("radiation_biomes", List.of("nuclearcraft:nuclear_wasteland_0.25"));
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_LIMITS =
            rates("radiation_biome_limits", List.of());
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_BLACKLIST =
            BUILDER.defineListAllowEmpty("radiation_from_biomes_dims_blacklist", List.of(),
                    name -> name instanceof String id && validId(id));
    static final ForgeConfigSpec.DoubleValue GEIGER_SCALE =
            BUILDER.defineInRange("radiation_geiger_block_redstone", 3D, -127D, 127D);
    static final ForgeConfigSpec.BooleanValue REQUIRE_COUNTER = BUILDER.define("radiation_require_counter", true);
    static final ForgeConfigSpec.IntValue UNIT_DIGITS =
            BUILDER.defineInRange("radiation_unit_prefixes", 0, 0, 15);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> SOUND_VOLUMES =
            BUILDER.comment("Legacy radiation sound multipliers. Index 0 is the Geiger click, index 6 is the warning.")
                    .defineListAllowEmpty("radiation_sound_volumes", List.of(1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D),
                            value -> value instanceof Number number
                                    && number.doubleValue() >= 0 && number.doubleValue() <= 15);
    private static final ForgeConfigSpec SPEC;

    static {
        ScrubberSettings.define(BUILDER);
        ShieldingSettings.define(BUILDER);
        SPEC = BUILDER.build();
    }

    static boolean loaded() {
        return SPEC.isLoaded();
    }

    private RadiationConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-radiation.toml");
    }

    static float soundVolume(int index) {
        var volumes = SOUND_VOLUMES.get();
        return index < volumes.size() ? volumes.get(index).floatValue() : 1F;
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends String>> rates(String name, List<String> defaults) {
        return BUILDER.comment("Entries use namespace:path_rate, with the rate in Rad/t.")
                .defineListAllowEmpty(name, defaults, entry -> entry instanceof String line && validRate(line));
    }

    private static boolean validId(String id) {
        var name = ResourceLocation.tryParse(id);
        return name != null && name.toString().equals(id);
    }

    private static boolean validRate(String entry) {
        int delimiter = entry.lastIndexOf('_');
        if (delimiter < 1 || !validId(entry.substring(0, delimiter))) {
            return false;
        }
        try {
            double rate = Double.parseDouble(entry.substring(delimiter + 1));
            return Double.isFinite(rate) && rate >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
