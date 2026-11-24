package nc.recipe;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCItemProcessors.Rate;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class PressurizerPlateRecipes {
    private static final String PLATES = "plates/";
    private static final String DENSE_PLATES = "plates/dense/";
    private static final String DENSE_PLATE_ALIAS = "dense_plates/";
    private static final Map<RecipeManager, List<ItemProcessorRecipe>> SERVER_RECIPES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PressurizerPlateRecipes() {}

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        var recipes = event.getServerResources().getRecipeManager();
        var context = event.getConditionContext();
        event.addListener((barrier, resources, preparation, reload, background, game) ->
                barrier.wait(Unit.INSTANCE).thenRunAsync(() -> SERVER_RECIPES.put(recipes,
                        discover(recipes, context.getAllTags(Registries.ITEM))), game));
    }

    public static List<ItemProcessorRecipe> forServer(RecipeManager recipes) {
        return NCItemProcessors.defaultRecipes(Rate.PRESSURIZER) ? SERVER_RECIPES.getOrDefault(recipes, List.of()) : List.of();
    }

    public static List<ItemProcessorRecipe> fromSyncedTags(RecipeManager recipes, Registry<Item> items) {
        if (!NCItemProcessors.defaultRecipes(Rate.PRESSURIZER)) {
            return List.of();
        }
        var tags = items.getTags().collect(Collectors.toMap(pair -> pair.getFirst().location(),
                pair -> pair.getSecond().stream().toList()));
        return discover(recipes, tags);
    }

    private static List<ItemProcessorRecipe> discover(RecipeManager recipes,
                                                      Map<ResourceLocation, ? extends Collection<Holder<Item>>> tags) {
        var discovered = new ArrayList<ItemProcessorRecipe>();
        for (ResourceLocation tag : tags.keySet().stream().sorted().toList()) {
            if (!tag.getNamespace().equals("forge")) {
                continue;
            }
            String path = tag.getPath();
            if (path.startsWith(DENSE_PLATES)) {
                add(discovered, recipes, tags, tag, path.substring(DENSE_PLATES.length()), true);
            } else if (path.startsWith(DENSE_PLATE_ALIAS)) {
                String material = path.substring(DENSE_PLATE_ALIAS.length());
                if (firstItem(tags, forge(DENSE_PLATES + material)) == Items.AIR) {
                    add(discovered, recipes, tags, tag, material, true);
                }
            } else if (path.startsWith(PLATES) && !path.equals(PLATES + "graphite") && !path.equals(PLATES + "dense")) {
                add(discovered, recipes, tags, tag, path.substring(PLATES.length()), false);
            }
        }
        return List.copyOf(discovered);
    }

    private static void add(List<ItemProcessorRecipe> discovered, RecipeManager recipes,
                            Map<ResourceLocation, ? extends Collection<Holder<Item>>> tags,
                            ResourceLocation tag, String material, boolean dense) {
        if (material.isEmpty()) {
            return;
        }
        Item product = firstItem(tags, tag);
        ResourceLocation id = NuclearCraft.id("pressurizer/" + (dense ? DENSE_PLATE_ALIAS : PLATES) + material);
        ResourceLocation input = forge((dense ? PLATES : "ingots/") + material);
        if (!dense && firstItem(tags, input) == Items.AIR) {
            input = forge("gems/" + material);
        }
        if (product == Items.AIR || firstItem(tags, input) == Items.AIR || recipes.byKey(id).isPresent()) {
            return;
        }
        var ingredient = new ItemProcessorRecipe.Input(Ingredient.of(TagKey.create(Registries.ITEM, input)), dense ? 9 : 1);
        var result = new ItemProcessorRecipe.Product(new ItemStack(product), 100, 0);
        discovered.add(new ItemProcessorRecipe(id, Machine.PRESSURIZER, List.of(ingredient), null, FluidStack.EMPTY,
                List.of(result), List.of(), dense ? 1200 : 600, dense ? 80 : 40, 0D));
    }

    private static Item firstItem(Map<ResourceLocation, ? extends Collection<Holder<Item>>> tags, ResourceLocation id) {
        var entries = tags.get(id);
        return entries == null ? Items.AIR
                : entries.stream().map(Holder::value).filter(item -> item != Items.AIR).findFirst().orElse(Items.AIR);
    }

    private static ResourceLocation forge(String path) {
        return ResourceLocation.tryBuild("forge", path);
    }
}
