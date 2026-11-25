package nc.recipe;

import com.almostreliable.unified.api.AlmostUnifiedLookup;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;
import java.util.Optional;

public final class MaterialRecipeOutputs {
    private static final List<String> DEFAULT_ORDER = List.of("minecraft", "thermalfoundation", "techreborn",
            "nuclearcraft", "qmd", "trinity", "immersiveengineering", "mekanism", "ic2", "appliedenergistics2",
            "refinedstorage", "actuallyadditions", "libvulpes", "advancedrocketry", "thaumcraft", "biomesoplenty");
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue TAGGED_INPUTS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ORDER;
    private static final ForgeConfigSpec SPEC;

    static {
        var builder = new ForgeConfigSpec.Builder();
        TAGGED_INPUTS = builder.comment("Accept shared material tags in stock ore, dust and ingot-block recipes.")
                .define("ore_dict_raw_material_recipes", false);
        ENABLED = builder.comment("Use mod priority for material tag outputs.")
                .define("ore_dict_priority_bool", true);
        ORDER = builder.comment("Mod IDs in descending order. Unlisted items follow the tag's order.")
                .defineListAllowEmpty("ore_dict_priority", DEFAULT_ORDER,
                        value -> value instanceof String name && ResourceLocation.tryBuild(name, "item") != null);
        SPEC = builder.build();
    }

    private MaterialRecipeOutputs() {}

    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SPEC, "nuclearcraft-recipe-outputs.toml");
    }

    static boolean taggedInputs() {
        return SPEC.isLoaded() && TAGGED_INPUTS.get();
    }

    static Optional<Item> select(String name, ICondition.IContext context) {
        var id = ResourceLocation.tryParse(name);
        if (id == null) {
            throw new JsonSyntaxException("Invalid item output tag " + name);
        }
        return select(id, context);
    }

    static Optional<Item> select(ResourceLocation id, ICondition.IContext context) {
        var tag = TagKey.create(Registries.ITEM, id);
        var entries = context == ICondition.IContext.EMPTY
                ? BuiltInRegistries.ITEM.getTag(tag).stream().flatMap(set -> set.stream())
                : context.getTag(tag).stream();
        var items = entries.map(Holder::value).filter(item -> item != Items.AIR).toList();
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Item unified = ModList.get().isLoaded("almostunified")
                ? AlmostUnifiedLookup.INSTANCE.getPreferredItemForTag(tag) : null;
        if (unified != null && items.contains(unified)) {
            return Optional.of(unified);
        }
        var order = priorityOrder();
        Item selected = items.get(0);
        int bestRank = order.size();
        for (Item item : items) {
            String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            int rank = order.indexOf(namespace);
            int legacyRank = switch (namespace) {
                case "thermal" -> order.indexOf("thermalfoundation");
                case "ae2" -> order.indexOf("appliedenergistics2");
                default -> -1;
            };
            if (legacyRank >= 0 && (rank < 0 || legacyRank < rank)) {
                rank = legacyRank;
            }
            if (rank >= 0 && rank < bestRank) {
                selected = item;
                bestRank = rank;
            }
        }
        return Optional.of(selected);
    }

    private static List<? extends String> priorityOrder() {
        if (!SPEC.isLoaded()) {
            return DEFAULT_ORDER;
        }
        return ENABLED.get() ? ORDER.get() : List.of();
    }
}
