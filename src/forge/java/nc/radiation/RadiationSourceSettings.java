package nc.radiation;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class RadiationSourceSettings {
    private static final Snapshot DEFAULT = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), List.of(),
            Set.of(), Set.of(), Set.of(), Set.of());
    private static volatile Snapshot active = DEFAULT;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> items;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> blocks;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> itemTags;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> fluids;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> structures;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> excludedItems;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> excludedBlocks;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> excludedTags;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> excludedFluids;

    private RadiationSourceSettings() {}

    static void define(ForgeConfigSpec.Builder builder) {
        items = rates(builder, "radiation_items", "Rad/t per item.");
        blocks = rates(builder, "radiation_blocks", "Rad/t per placed block or corresponding block item.");
        itemTags = rates(builder, "radiation_ores", "Rad/t per item. Use a Forge item tag ID for each former ore entry.");
        fluids = rates(builder, "radiation_fluids", "Rad/t per 1,000 mB or sampled fluid block.");
        structures = rates(builder, "radiation_structures", "Rad/t in the start chunk of the configured structure.");
        excludedItems = exclusions(builder, "radiation_items_blacklist");
        excludedBlocks = exclusions(builder, "radiation_blocks_blacklist");
        excludedTags = exclusions(builder, "radiation_ores_blacklist");
        excludedFluids = exclusions(builder, "radiation_fluids_blacklist");
    }

    static Snapshot active() {
        return active;
    }

    static void refresh() {
        active = new Snapshot(numbers(items), numbers(blocks), numbers(itemTags), numbers(fluids),
                List.copyOf(numbers(structures).entrySet()), ids(excludedItems), ids(excludedBlocks),
                ids(excludedTags), ids(excludedFluids));
    }

    static void clear() {
        active = DEFAULT;
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends String>> rates(ForgeConfigSpec.Builder builder,
                                                                            String key, String units) {
        return builder.comment("Entries use namespace:path_rate. " + units)
                .defineListAllowEmpty(key, List.of(), entry -> entry instanceof String line && RadiationConfig.validRate(line));
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends String>> exclusions(ForgeConfigSpec.Builder builder,
                                                                                  String key) {
        return builder.defineListAllowEmpty(key, List.of(), entry -> entry instanceof String id && RadiationConfig.validId(id));
    }

    private static Map<ResourceLocation, Double> numbers(ForgeConfigSpec.ConfigValue<List<? extends String>> values) {
        var result = new HashMap<ResourceLocation, Double>();
        for (String value : values.get()) {
            int split = value.lastIndexOf('_');
            result.put(ResourceLocation.tryParse(value.substring(0, split)), Double.parseDouble(value.substring(split + 1)));
        }
        return Map.copyOf(result);
    }

    private static Set<ResourceLocation> ids(ForgeConfigSpec.ConfigValue<List<? extends String>> values) {
        return values.get().stream().map(ResourceLocation::tryParse).collect(Collectors.toUnmodifiableSet());
    }

    record Snapshot(Map<ResourceLocation, Double> items, Map<ResourceLocation, Double> blocks,
                    Map<ResourceLocation, Double> itemTags, Map<ResourceLocation, Double> fluids,
                    List<Map.Entry<ResourceLocation, Double>> structures,
                    Set<ResourceLocation> excludedItems, Set<ResourceLocation> excludedBlocks,
                    Set<ResourceLocation> excludedTags, Set<ResourceLocation> excludedFluids) {}
}
