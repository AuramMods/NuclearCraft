package nc.init;

import com.mojang.serialization.Codec;
import nc.NuclearCraft;
import nc.worldgen.NuclearOrePlacement;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class NCOres {
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, NuclearCraft.MOD_ID);

    public static final RegistryObject<PlacementModifierType<NuclearOrePlacement>> PLACEMENT =
            PLACEMENTS.register("stock_ore_filter", () -> () -> NuclearOrePlacement.CODEC);

    private static final List<String> DEFAULT_DIMENSIONS = List.of("minecraft:overworld", "nuclearcraft:wasteland");
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;
    private static final ForgeConfigSpec.BooleanValue DENYLIST;
    private static final ForgeConfigSpec.ConfigValue<List<Boolean>> ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<Integer>> SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<Integer>> RATE;
    private static final ForgeConfigSpec.ConfigValue<List<Integer>> MIN_HEIGHT;
    private static final ForgeConfigSpec.ConfigValue<List<Integer>> MAX_HEIGHT;
    private static final ForgeConfigSpec.BooleanValue HIDE_DISABLED;

    static {
        var builder = new ForgeConfigSpec.Builder();
        DIMENSIONS = builder.comment("Dimension IDs eligible for stock ores. Use namespaced IDs.")
                .worldRestart().defineListAllowEmpty("ore_dims", DEFAULT_DIMENSIONS,
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        DENYLIST = builder.comment("False allows only the listed dimensions. True excludes the listed dimensions.")
                .worldRestart().define("ore_dims_list_type", false);
        builder.comment("Array order: copper, tin, lead, thorium, uranium, boron, lithium, magnesium.")
                .push("ores");
        ENABLED = array(builder, "ore_gen", Collections.nCopies(Ore.values().length, true), Boolean.class::isInstance);
        SIZE = array(builder.comment("Vanilla vein size (1 to 64)."), "ore_size",
                Arrays.stream(Ore.values()).map(ore -> ore.size).toList(), integerRange(1, 64));
        RATE = array(builder.comment("Placement attempts per chunk (0 to 256)."), "ore_rate",
                Arrays.stream(Ore.values()).map(ore -> ore.rate).toList(), integerRange(0, 256));
        MIN_HEIGHT = array(builder.comment("Minimum inclusive placement Y."), "ore_min_height",
                Collections.nCopies(Ore.values().length, 0), integerRange(DimensionType.MIN_Y, DimensionType.MAX_Y));
        MAX_HEIGHT = array(builder.comment("Maximum inclusive placement Y."), "ore_max_height",
                Arrays.stream(Ore.values()).map(ore -> ore.maxHeight).toList(), integerRange(DimensionType.MIN_Y, DimensionType.MAX_Y));
        HIDE_DISABLED = builder.comment("Hide the ore, block, ingot, and dust of disabled stock ores in JEI.")
                .worldRestart().define("ore_hide_disabled", false);
        builder.pop();
        SPEC = builder.build();
    }

    private NCOres() {}

    public static void register(FMLJavaModLoadingContext context) {
        PLACEMENTS.register(context.getModEventBus());
        context.getModEventBus().addListener(NCOres::addOrePack);
        context.registerConfig(ModConfig.Type.COMMON, SPEC, "nuclearcraft-ore-worldgen.toml");
    }

    private static void addOrePack(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        if (type != PackType.SERVER_DATA) {
            return;
        }
        var title = Component.literal("NuclearCraft ore generation");
        int format = SharedConstants.getCurrentVersion().getPackVersion(type);
        var info = new Pack.Info(title, format, format, FeatureFlagSet.of(), false);
        // Let the repository insert the required pack at the bottom, below world datapacks.
        var packSource = PackSource.create(PackSource.BUILT_IN::decorate, false);
        event.addRepositorySource(packs -> packs.accept(Pack.create("nuclearcraft_ore_worldgen", title, true,
                id -> new GeneratedPackResources(id, type, oreResources()), info, type,
                Pack.Position.BOTTOM, true, packSource)));
    }

    private static Map<ResourceLocation, byte[]> oreResources() {
        var resources = new HashMap<ResourceLocation, byte[]>();
        for (Ore ore : Ore.values()) {
            String name = ore.getSerializedName();
            String configured = String.format(Locale.ROOT, """
                    {
                        "type": "minecraft:ore",
                        "config": {
                            "size": %d,
                            "discard_chance_on_air_exposure": 0.0,
                            "targets": [{
                                "target": {"predicate_type": "minecraft:tag_match", "tag": "nuclearcraft:ore_replaceables"},
                                "state": {"Name": "nuclearcraft:%s_ore"}
                            }]
                        }
                    }
                    """, ore.size(), name);
            int attempts = ore.enabled() && ore.minHeight() <= ore.maxHeight() ? ore.rate() : 0;
            String placed = String.format(Locale.ROOT, """
                    {
                        "feature": "nuclearcraft:ore_%s",
                        "placement": [
                            {"type": "nuclearcraft:stock_ore_filter", "ore": "%s"},
                            {"type": "minecraft:count", "count": %d},
                            {"type": "minecraft:in_square"},
                            {
                                "type": "minecraft:height_range",
                                "height": {
                                    "type": "minecraft:uniform",
                                    "min_inclusive": {"absolute": %d},
                                    "max_inclusive": {"absolute": %d}
                                }
                            },
                            {"type": "minecraft:biome"}
                        ]
                    }
                    """, name, name, attempts, ore.minHeight(), ore.maxHeight());
            resources.put(NuclearCraft.id("worldgen/configured_feature/ore_" + name + ".json"),
                    configured.getBytes(StandardCharsets.UTF_8));
            resources.put(NuclearCraft.id("worldgen/placed_feature/ore_" + name + ".json"),
                    placed.getBytes(StandardCharsets.UTF_8));
        }
        return resources;
    }

    public static boolean allowsDimension(ResourceLocation dimension) {
        if (!SPEC.isLoaded()) {
            return DEFAULT_DIMENSIONS.contains(dimension.toString());
        }
        return DIMENSIONS.get().stream().anyMatch(id -> dimension.equals(ResourceLocation.tryParse(id))) != DENYLIST.get();
    }

    public static boolean hiddenInJei(Ore ore) {
        return SPEC.isLoaded() && HIDE_DISABLED.get() && !ore.enabled();
    }

    private static <T> ForgeConfigSpec.ConfigValue<List<T>> array(ForgeConfigSpec.Builder builder, String name,
                                                                  List<T> defaults, Predicate<Object> validEntry) {
        return builder.worldRestart().define(name, defaults, value -> value instanceof List<?> list
                && list.size() == Ore.values().length && list.stream().allMatch(validEntry));
    }

    private static Predicate<Object> integerRange(int min, int max) {
        return value -> value instanceof Integer number && number >= min && number <= max;
    }

    public enum Ore implements StringRepresentable {
        COPPER("copper", 6, 5, 48),
        TIN("tin", 6, 4, 40),
        LEAD("lead", 6, 6, 36),
        THORIUM("thorium", 3, 3, 32),
        URANIUM("uranium", 3, 6, 32),
        BORON("boron", 4, 6, 28),
        LITHIUM("lithium", 4, 6, 28),
        MAGNESIUM("magnesium", 5, 4, 24);

        public static final Codec<Ore> CODEC = StringRepresentable.fromEnum(Ore::values);

        private final String id;
        private final int size;
        private final int rate;
        private final int maxHeight;

        Ore(String id, int size, int rate, int maxHeight) {
            this.id = id;
            this.size = size;
            this.rate = rate;
            this.maxHeight = maxHeight;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public boolean enabled() {
            return !SPEC.isLoaded() || ENABLED.get().get(ordinal());
        }

        public int size() {
            return SPEC.isLoaded() ? SIZE.get().get(ordinal()) : size;
        }

        public int rate() {
            return SPEC.isLoaded() ? RATE.get().get(ordinal()) : rate;
        }

        public int minHeight() {
            return SPEC.isLoaded() ? MIN_HEIGHT.get().get(ordinal()) : 0;
        }

        public int maxHeight() {
            return SPEC.isLoaded() ? MAX_HEIGHT.get().get(ordinal()) : maxHeight;
        }
    }
}
