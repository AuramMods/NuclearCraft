package nc.recipe;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCItemProcessors.Rate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ManufactoryMaterialRecipes {
    private static final List<String> NAMESPACES = List.of("forge", "c");
    private static final String INGOT_PREFIX = "ingots/";
    private static final TagKey<Item> EXCLUDED =
            TagKey.create(Registries.ITEM, NuclearCraft.id("exclude_automatic_grinding"));

    private ManufactoryMaterialRecipes() {}

    static void addDiscoveredRecipes(RecipeManager manager, ResourceManager resources) {
        if (!NCItemProcessors.defaultRecipes(Rate.MANUFACTORY)) {
            return;
        }
        var type = Machine.MANUFACTORY.recipes();
        var existing = manager.getAllRecipesFor(type);
        var covered = new HashSet<>(contents(EXCLUDED));
        var materials = BuiltInRegistries.ITEM.getTags()
                .filter(tag -> tag.getSecond().size() > 0)
                .map(pair -> pair.getFirst().location())
                .filter(id -> NAMESPACES.contains(id.getNamespace()) && id.getPath().startsWith(INGOT_PREFIX))
                .map(id -> id.getPath().substring(INGOT_PREFIX.length()))
                .filter(material -> !material.equals("silicon"))
                .distinct().sorted().toList();
        var additions = new ArrayList<ItemProcessorRecipe>();
        boolean oreProcessing = NCItemProcessors.oreProcessing();
        for (String material : materials) {
            Item dust = dust(material);
            if (dust == Items.AIR) {
                continue;
            }
            for (Form form : Form.values()) {
                if (form == Form.INGOT || oreProcessing) {
                    addRecipe(manager, resources, existing, additions, covered, material, form, dust);
                }
            }
        }
        ProcessorRecipeDefaults.addRecipes(manager, type, additions);
    }

    private static void addRecipe(RecipeManager manager, ResourceManager resources, List<ItemProcessorRecipe> existing,
                                  List<ItemProcessorRecipe> additions, Set<Item> covered, String material,
                                  Form form, Item dust) {
        var id = NuclearCraft.id("manufactory/" + material + "_dust_from_" + form.suffix);
        // Keep the ID reserved when a source recipe is disabled by a condition or removed by a pack script.
        if (manager.byKey(id).isPresent()
                || resources.getResource(NuclearCraft.id("recipes/" + id.getPath() + ".json")).isPresent()) {
            return;
        }
        var inputs = new LinkedHashSet<Item>();
        for (String namespace : NAMESPACES) {
            var key = TagKey.create(Registries.ITEM, ResourceLocation.tryBuild(namespace, form.tagFolder + "/" + material));
            inputs.addAll(contents(key));
        }
        inputs.removeIf(item -> covered.contains(item)
                || existing.stream().anyMatch(recipe -> recipe.acceptsItem(item.getDefaultInstance())));
        if (inputs.isEmpty() || dust.getDefaultInstance().getMaxStackSize() < form.count) {
            return;
        }
        var ingredient = new ItemProcessorRecipe.Input(Ingredient.of(inputs.toArray(Item[]::new)), 1);
        var product = new ItemProcessorRecipe.Product(new ItemStack(dust, form.count), 100, 0);
        additions.add(new ItemProcessorRecipe(id, Machine.MANUFACTORY, List.of(ingredient), null, FluidStack.EMPTY,
                List.of(product), List.of(), form.ticks, 20, 0D, null, false,
                new ProcessorRecipeDefaults(Rate.MANUFACTORY, form != Form.INGOT)));
        covered.addAll(inputs);
    }

    private static Item dust(String material) {
        for (String namespace : NAMESPACES) {
            var tag = ResourceLocation.tryBuild(namespace, "dusts/" + material);
            var dust = MaterialRecipeOutputs.select(tag, ICondition.IContext.EMPTY);
            if (dust.isPresent()) {
                return dust.get();
            }
        }
        return Items.AIR;
    }

    private static List<Item> contents(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).stream().flatMap(values -> values.stream())
                .map(Holder::value).filter(item -> item != Items.AIR).toList();
    }

    private enum Form {
        INGOT("ingots", "ingot", 1, 400),
        ORE("ores", "ore", 2, 500),
        RAW("raw_materials", "raw", 1, 500);

        private final String tagFolder;
        private final String suffix;
        private final int count;
        private final int ticks;

        Form(String tagFolder, String suffix, int count, int ticks) {
            this.tagFolder = tagFolder;
            this.suffix = suffix;
            this.count = count;
            this.ticks = ticks;
        }
    }
}
