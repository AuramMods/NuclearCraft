package nc.recipe;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCItemProcessors.Rate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class IngotFormerMaterialRecipes {
    private static final List<String> TAG_NAMESPACES = List.of("forge", "c");
    private static final Set<String> EXCLUDED = Set.of("glass", "coal", "redstone", "glowstone", "prismarine",
            "obsidian", "silicon", "cesium_137", "caesium_137", "marshmallow");
    private static final TagKey<Fluid> EXCLUDED_FLUIDS =
            TagKey.create(Registries.FLUID, NuclearCraft.id("exclude_automatic_casting"));
    private static final TagKey<Fluid> STANDARD_INGOT_FLUIDS =
            TagKey.create(Registries.FLUID, NuclearCraft.id("ingot_former_90mb"));
    private static final int INGOT_VOLUME = 144;
    private static final int GEM_VOLUME = 666;

    private IngotFormerMaterialRecipes() {}

    static void addDiscoveredRecipes(RecipeManager manager) {
        if (!NCItemProcessors.defaultRecipes(Rate.INGOT_FORMER)) {
            return;
        }
        var type = Machine.INGOT_FORMER.recipes();
        var covered = new HashSet<Fluid>();
        BuiltInRegistries.FLUID.getTag(EXCLUDED_FLUIDS).ifPresent(tag ->
                tag.stream().map(Holder::value).forEach(covered::add));
        for (ItemProcessorRecipe recipe : manager.getAllRecipesFor(type)) {
            recipe.fluidInput().alternatives().stream().map(FluidStack::getFluid).forEach(covered::add);
        }

        var discovered = new ArrayList<ItemProcessorRecipe>();
        for (String namespace : TAG_NAMESPACES) {
            BuiltInRegistries.FLUID.getTags()
                    .filter(tag -> tag.getFirst().location().getNamespace().equals(namespace))
                    .filter(tag -> isMoltenTag(tag.getFirst().location().getPath()))
                    .sorted(Comparator.comparing(tag -> tag.getFirst().location().getPath()))
                    .forEach(tag -> {
                        String material = materialKey(tag.getFirst().location().getPath());
                        for (Holder<Fluid> entry : tag.getSecond()) {
                            addRecipe(manager, discovered, covered, entry.value(), material);
                        }
                    });
        }
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (!covered.contains(fluid) && isSource(fluid)) {
                var id = BuiltInRegistries.FLUID.getKey(fluid);
                addRecipe(manager, discovered, covered, fluid, materialKey(id.getPath()));
            }
        }
        ProcessorRecipeDefaults.addRecipes(manager, type, discovered);
    }

    private static void addRecipe(RecipeManager manager, List<ItemProcessorRecipe> recipes, Set<Fluid> covered,
                                  Fluid fluid, String material) {
        if (material.isEmpty() || EXCLUDED.contains(material) || covered.contains(fluid) || !isSource(fluid)) {
            return;
        }
        var fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        if (EXCLUDED.contains(materialKey(fluidId.getPath()))) {
            return;
        }
        var recipeId = NuclearCraft.id("ingot_former/materials/" + fluidId.getNamespace() + "/" + fluidId.getPath());
        if (manager.byKey(recipeId).isPresent()) {
            return;
        }

        Item product = taggedItem("ingots/" + material);
        int volume = fluid.is(STANDARD_INGOT_FLUIDS) ? 90 : INGOT_VOLUME;
        if (product == Items.AIR) {
            product = taggedItem("gems/" + material);
            volume = GEM_VOLUME;
        }
        if (product == Items.AIR) {
            return;
        }

        var ingredient = new FluidIngredient(fluidId, false, volume);
        var result = new ItemProcessorRecipe.Product(new ItemStack(product), 100, 0);
        recipes.add(new ItemProcessorRecipe(recipeId, Machine.INGOT_FORMER, List.of(), ingredient, FluidStack.EMPTY,
                List.of(result), List.of(), 400, 0, 0D, 1D, false,
                new ProcessorRecipeDefaults(Rate.INGOT_FORMER, false)));
        covered.add(fluid);
    }

    private static boolean isSource(Fluid fluid) {
        return fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState());
    }

    private static Item taggedItem(String path) {
        for (String namespace : TAG_NAMESPACES) {
            var result = MaterialRecipeOutputs.select(namespace + ":" + path, ICondition.IContext.EMPTY);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return Items.AIR;
    }

    private static boolean isMoltenTag(String path) {
        return path.startsWith("molten_") || path.startsWith("molten/");
    }

    private static String materialKey(String path) {
        return isMoltenTag(path) ? path.substring("molten_".length()) : path;
    }
}
