package nc.recipe;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCItemProcessors.Rate;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ManufactoryWoodRecipes {
    private static final List<String> STOCK_WOODS = List.of("acacia", "birch", "cherry", "crimson", "dark_oak",
            "jungle", "mangrove", "oak", "spruce", "warped");
    private static final ResourceLocation STICKS = NuclearCraft.id("manufactory/sticks");
    private static final ProcessorRecipeDefaults DEFAULTS = new ProcessorRecipeDefaults(Rate.MANUFACTORY, false);

    private ManufactoryWoodRecipes() {}

    static void apply(MinecraftServer server) {
        if (!NCItemProcessors.defaultRecipes(Rate.MANUFACTORY)) {
            return;
        }
        var manager = server.getRecipeManager();
        var woodRecipeIds = new HashSet<ResourceLocation>();
        for (String wood : STOCK_WOODS) {
            woodRecipeIds.add(stockId(wood));
        }
        woodRecipeIds.add(STICKS);
        var overrides = manager.getAllRecipesFor(Machine.MANUFACTORY.recipes()).stream()
                .filter(recipe -> !woodRecipeIds.contains(recipe.id()) || recipe.defaults() == null)
                .toList();

        var yields = NCItemProcessors.manufactoryWood();
        var gridItems = NonNullList.withSize(9, ItemStack.EMPTY);
        var grid = new TransientCraftingContainer(null, 3, 3, gridItems);
        var level = server.overworld();
        Map<Item, ItemStack> plankResults = new LinkedHashMap<>();
        var logs = new HashSet<Item>();
        for (String wood : STOCK_WOODS) {
            logs.addAll(contents(stockLogs(wood)));
        }
        logs.addAll(contents(ItemTags.LOGS));
        logs.addAll(contents(itemTag("forge", "logs")));
        logs.addAll(contents(itemTag("c", "logs")));
        for (Item item : logs.stream().sorted(Comparator.comparing(BuiltInRegistries.ITEM::getKey)).toList()) {
            ItemStack input = new ItemStack(item);
            if (overrides.stream().anyMatch(recipe -> recipe.acceptsItem(input))) {
                continue;
            }
            gridItems.set(0, input);
            manager.getRecipeFor(RecipeType.CRAFTING, grid, level).ifPresent(recipe -> {
                ItemStack result = recipe.assemble(grid, level.registryAccess());
                if (!result.isEmpty()) {
                    plankResults.put(item, result.copyWithCount(Math.min(yields.get(0), result.getMaxStackSize())));
                }
            });
        }

        var removed = new HashSet<ResourceLocation>();
        var additions = new ArrayList<ItemProcessorRecipe>();
        for (String wood : STOCK_WOODS) {
            var id = stockId(wood);
            if (!(manager.byKey(id).orElse(null) instanceof ItemProcessorRecipe recipe) || recipe.defaults() == null) {
                contents(stockLogs(wood)).forEach(plankResults::remove);
                continue;
            }
            var members = contents(stockLogs(wood)).stream().filter(plankResults::containsKey).toList();
            removed.add(id);
            if (members.isEmpty()) {
                continue;
            }
            ItemStack original = recipe.getResultItem(level.registryAccess());
            Item preferred = members.stream().filter(item -> ItemStack.isSameItemSameTags(plankResults.get(item), original))
                    .findFirst().orElse(members.get(0));
            ItemStack product = plankResults.get(preferred);
            List<Item> sameOutput = members.stream()
                    .filter(item -> ItemStack.isSameItemSameTags(plankResults.get(item), product)).toList();
            additions.add(withWoodOutput(recipe, Ingredient.of(sameOutput.toArray(Item[]::new)), product));
            sameOutput.forEach(plankResults::remove);
        }
        if (manager.byKey(STICKS).orElse(null) instanceof ItemProcessorRecipe recipe && recipe.defaults() != null) {
            removed.add(STICKS);
            ItemStack sticks = recipe.getResultItem(level.registryAccess());
            additions.add(withWoodOutput(recipe, recipe.inputs().get(0).ingredient(), sticks.copyWithCount(yields.get(1))));
        }
        plankResults.forEach((item, result) -> {
            var itemId = BuiltInRegistries.ITEM.getKey(item);
            var id = NuclearCraft.id("manufactory/logs/" + itemId.getNamespace() + "/" + itemId.getPath());
            if (manager.byKey(id).isEmpty()) {
                additions.add(new ItemProcessorRecipe(id, Machine.MANUFACTORY,
                        List.of(new ItemProcessorRecipe.Input(Ingredient.of(item), 1)), null, FluidStack.EMPTY,
                        List.of(new ItemProcessorRecipe.Product(result, 100, 0)), List.of(), 200, 10, 0D,
                        null, false, DEFAULTS));
            }
        });
        ProcessorRecipeDefaults.updateRecipes(manager, Machine.MANUFACTORY.recipes(), removed, additions);
    }

    private static ItemProcessorRecipe withWoodOutput(ItemProcessorRecipe recipe, Ingredient input, ItemStack output) {
        return new ItemProcessorRecipe(recipe.id(), recipe.machine(),
                List.of(new ItemProcessorRecipe.Input(input, 1)), recipe.fluidInputs(), recipe.fluidProducts(),
                List.of(new ItemProcessorRecipe.Product(output, 100, 0)), recipe.alternateResults(),
                recipe.ticks(), recipe.energyPerTick(), recipe.radiation(), recipe.powerMultiplier(),
                recipe.decayTimeFromRadiation(), recipe.defaults());
    }

    private static ResourceLocation stockId(String wood) {
        return NuclearCraft.id("manufactory/" + wood + "_planks");
    }

    private static TagKey<Item> stockLogs(String wood) {
        return itemTag("minecraft", wood + (wood.equals("crimson") || wood.equals("warped") ? "_stems" : "_logs"));
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.tryBuild(namespace, path));
    }

    private static List<Item> contents(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).stream().flatMap(set -> set.stream())
                .map(Holder::value).toList();
    }
}
