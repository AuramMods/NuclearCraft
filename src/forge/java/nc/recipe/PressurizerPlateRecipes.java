package nc.recipe;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCItemProcessors.Rate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

final class PressurizerPlateRecipes {
    private static final String PLATES = "plates/";
    private static final String DENSE_PLATES = "plates/dense/";
    private static final String DENSE_PLATE_ALIAS = "dense_plates/";

    private PressurizerPlateRecipes() {}

    static void addDiscoveredRecipes(RecipeManager recipes) {
        if (!NCItemProcessors.defaultRecipes(Rate.PRESSURIZER)) {
            return;
        }
        var discovered = new ArrayList<ItemProcessorRecipe>();
        for (ResourceLocation tag : BuiltInRegistries.ITEM.getTagNames().map(TagKey::location).sorted().toList()) {
            if (!tag.getNamespace().equals("forge")) {
                continue;
            }
            String path = tag.getPath();
            if (path.startsWith(DENSE_PLATES)) {
                add(discovered, recipes, tag, path.substring(DENSE_PLATES.length()), true);
            } else if (path.startsWith(DENSE_PLATE_ALIAS)) {
                String material = path.substring(DENSE_PLATE_ALIAS.length());
                if (!hasItems(forge(DENSE_PLATES + material))) {
                    add(discovered, recipes, tag, material, true);
                }
            } else if (path.startsWith(PLATES) && !path.equals(PLATES + "graphite") && !path.equals(PLATES + "dense")) {
                add(discovered, recipes, tag, path.substring(PLATES.length()), false);
            }
        }
        ProcessorRecipeDefaults.addRecipes(recipes, Machine.PRESSURIZER.recipes(), discovered);
    }

    private static void add(List<ItemProcessorRecipe> discovered, RecipeManager recipes,
                            ResourceLocation tag, String material, boolean dense) {
        if (material.isEmpty()) {
            return;
        }
        Item product = MaterialRecipeOutputs.select(tag, ICondition.IContext.EMPTY).orElse(Items.AIR);
        ResourceLocation id = NuclearCraft.id("pressurizer/" + (dense ? DENSE_PLATE_ALIAS : PLATES) + material);
        ResourceLocation input = forge((dense ? PLATES : "ingots/") + material);
        if (!dense && !hasItems(input)) {
            input = forge("gems/" + material);
        }
        if (product == Items.AIR || !hasItems(input) || recipes.byKey(id).isPresent()) {
            return;
        }
        var ingredient = new ItemProcessorRecipe.Input(Ingredient.of(TagKey.create(Registries.ITEM, input)), dense ? 9 : 1);
        var result = new ItemProcessorRecipe.Product(new ItemStack(product), 100, 0);
        discovered.add(new ItemProcessorRecipe(id, Machine.PRESSURIZER, List.of(ingredient), null, FluidStack.EMPTY,
                List.of(result), List.of(), dense ? 1200 : 600, dense ? 80 : 40, 0D,
                null, false, new ProcessorRecipeDefaults(Rate.PRESSURIZER, false)));
    }

    private static boolean hasItems(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).stream()
                .flatMap(set -> set.stream()).anyMatch(entry -> entry.value() != Items.AIR);
    }

    private static ResourceLocation forge(String path) {
        return ResourceLocation.tryBuild("forge", path);
    }
}
