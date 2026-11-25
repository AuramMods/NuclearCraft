package nc.init;

import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.resource.ResourcePackLoader;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class StockRecipeAdjustments {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StockRecipeAdjustments() {}

    static void register(String modId, BiFunction<Recipe<?>, Consumer<Recipe<?>>, Recipe<?>> adjustment) {
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> onRecipeReload(event, modId, adjustment));
    }

    private static void onRecipeReload(AddReloadListenerEvent event, String modId,
                                       BiFunction<Recipe<?>, Consumer<Recipe<?>>, Recipe<?>> adjustment) {
        var recipes = event.getServerResources().getRecipeManager();
        event.addListener((barrier, resources, preparation, reload, background, game) ->
                barrier.wait(Unit.INSTANCE).thenRunAsync(() -> adjustStockRecipes(recipes, resources, modId, adjustment), game));
    }

    private static void adjustStockRecipes(RecipeManager manager, ResourceManager resources, String modId,
                                           BiFunction<Recipe<?>, Consumer<Recipe<?>>, Recipe<?>> adjustment) {
        var stockPack = ResourcePackLoader.getPackFor(modId).orElseThrow();
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> changedTypes = new LinkedHashMap<>();
        var byName = new LinkedHashMap<>(manager.byName);
        for (Recipe<?> recipe : manager.getRecipes()) {
            var id = recipe.getId();
            if (!id.getNamespace().equals(modId)) {
                continue;
            }
            var file = ResourceLocation.tryBuild(id.getNamespace(), "recipes/" + id.getPath() + ".json");
            if (file == null || resources.getResource(file)
                    .filter(resource -> resource.sourcePackId().equals(stockPack.packId())).isEmpty()) {
                continue;
            }
            try {
                var additions = new ArrayList<Recipe<?>>();
                var adjusted = adjustment.apply(recipe, additions::add);
                if (adjusted != recipe) {
                    changedTypes.computeIfAbsent(recipe.getType(), type ->
                            new LinkedHashMap<>(manager.recipes.get(type))).put(id, adjusted);
                    byName.put(id, adjusted);
                }
                for (Recipe<?> addition : additions) {
                    if (byName.putIfAbsent(addition.getId(), addition) == null) {
                        changedTypes.computeIfAbsent(addition.getType(), type ->
                                new LinkedHashMap<>(manager.recipes.getOrDefault(type, Map.of())))
                                .put(addition.getId(), addition);
                    }
                }
            } catch (JsonParseException | ArithmeticException error) {
                LOGGER.warn("Could not adapt molten metal recipe {}", id, error);
            }
        }
        if (!changedTypes.isEmpty()) {
            var types = new LinkedHashMap<>(manager.recipes);
            changedTypes.forEach((type, recipes) -> types.put(type, Collections.unmodifiableMap(recipes)));
            manager.recipes = Collections.unmodifiableMap(types);
            manager.byName = Collections.unmodifiableMap(byName);
        }
    }
}
