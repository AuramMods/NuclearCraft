package nc.recipe;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Rate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public record ProcessorRecipeDefaults(Rate processor, boolean ore) {
    static @Nullable ProcessorRecipeDefaults fromJson(JsonObject json) {
        if (!json.has("nuclearcraft_default")) {
            return null;
        }
        String label = GsonHelper.getAsString(json, "nuclearcraft_default");
        boolean ore = label.endsWith("/ore");
        String machine = ore ? label.substring(0, label.length() - "/ore".length()) : label;
        try {
            return new ProcessorRecipeDefaults(Rate.valueOf(machine.toUpperCase(Locale.ROOT)), ore);
        } catch (IllegalArgumentException failure) {
            throw new JsonSyntaxException("Unknown default processor " + label, failure);
        }
    }

    private boolean enabled() {
        return NCItemProcessors.defaultRecipes(processor) && (!ore || NCItemProcessors.oreProcessing());
    }

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        // Forge loads the world's server config after the initial datapack load.
        applyCatalogs(event.getServer().getRecipeManager());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            applyCatalogs(event.getPlayerList().getServer().getRecipeManager());
        }
    }

    private static void applyCatalogs(RecipeManager manager) {
        removeDisabledRecipes(manager);
        IngotFormerMaterialRecipes.addDiscoveredRecipes(manager);
    }

    private static void removeDisabledRecipes(RecipeManager manager) {
        Set<ResourceLocation> disabled = manager.byName.values().stream()
                .filter(recipe -> recipe instanceof ProcessingRecipe processing && processing.defaults() != null
                        && !processing.defaults().enabled())
                .map(Recipe::getId).collect(Collectors.toSet());
        if (disabled.isEmpty()) {
            return;
        }

        var recipes = ImmutableMap.<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>builder();
        manager.recipes.forEach((type, entries) -> {
            if (entries.keySet().stream().noneMatch(disabled::contains)) {
                recipes.put(type, entries);
            } else {
                var filtered = remove(entries, disabled);
                if (!filtered.isEmpty()) {
                    recipes.put(type, filtered);
                }
            }
        });
        manager.recipes = recipes.build();
        manager.byName = remove(manager.byName, disabled);
    }

    private static Map<ResourceLocation, Recipe<?>> remove(Map<ResourceLocation, Recipe<?>> entries,
                                                           Set<ResourceLocation> disabled) {
        var kept = ImmutableMap.<ResourceLocation, Recipe<?>>builder();
        entries.forEach((id, recipe) -> {
            if (!disabled.contains(id)) {
                kept.put(id, recipe);
            }
        });
        return kept.build();
    }
}
