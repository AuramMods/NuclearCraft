package nc.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Unit;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.resource.ResourcePackLoader;
import org.slf4j.Logger;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.melting.DamageableMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MaterialMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.OreMeltingRecipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class NCTinkersSmeltery {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> SHARED_METALS = Set.of("aluminum", "chromium", "copper", "enderium", "gold",
            "iron", "lead", "nickel", "silver", "steel", "tin", "uranium");
    private static final String FORGE_MOLTEN = "forge:molten_";
    private static final String STANDARD_METAL = "nuclearcraft:tinkers/molten_";
    private static final String TINKERS_DIAMOND = "tconstruct:molten_diamond";

    private NCTinkersSmeltery() {}

    static void onRecipeReload(AddReloadListenerEvent event) {
        var recipes = event.getServerResources().getRecipeManager();
        event.addListener((barrier, resources, preparation, reload, background, game) ->
                barrier.wait(Unit.INSTANCE).thenRunAsync(() -> adjustStockRecipes(recipes, resources), game));
    }

    private static void adjustStockRecipes(RecipeManager manager, ResourceManager resources) {
        var stockPack = ResourcePackLoader.getPackFor("tconstruct").orElseThrow();
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> changedTypes = new LinkedHashMap<>();
        var byName = new LinkedHashMap<>(manager.byName);
        for (Recipe<?> recipe : manager.getRecipes()) {
            var id = recipe.getId();
            if (!id.getNamespace().equals("tconstruct")) {
                continue;
            }
            var file = ResourceLocation.tryBuild(id.getNamespace(), "recipes/" + id.getPath() + ".json");
            if (file == null || resources.getResource(file)
                    .filter(resource -> resource.sourcePackId().equals(stockPack.packId())).isEmpty()) {
                continue;
            }
            try {
                var adjusted = adjustRecipe(recipe);
                if (adjusted != recipe) {
                    changedTypes.computeIfAbsent(recipe.getType(), type ->
                            new LinkedHashMap<>(manager.recipes.get(type))).put(id, adjusted);
                    byName.put(id, adjusted);
                }
            } catch (JsonParseException error) {
                LOGGER.warn("Could not adapt Tinkers' smeltery recipe {}", id, error);
            }
        }
        if (!changedTypes.isEmpty()) {
            var types = new LinkedHashMap<>(manager.recipes);
            changedTypes.forEach((type, recipes) -> types.put(type, Collections.unmodifiableMap(recipes)));
            manager.recipes = Collections.unmodifiableMap(types);
            manager.byName = Collections.unmodifiableMap(byName);
        }
    }

    private static Recipe<?> adjustRecipe(Recipe<?> recipe) {
        // The loaded object includes changes made by recipe scripting during RecipeManager.apply.
        var serializer = recipe.getSerializer();
        var kind = ForgeRegistries.RECIPE_SERIALIZERS.getKey(serializer);
        if (kind == null || !kind.getNamespace().equals("tconstruct")) {
            return recipe;
        }
        JsonElement fields = switch (kind.getPath()) {
            case "casting_table", "casting_basin" -> ItemCastingRecipe.LOADER.serialize((ItemCastingRecipe) recipe);
            case "material_fluid" -> MaterialFluidRecipe.LOADER.serialize((MaterialFluidRecipe) recipe);
            case "alloy" -> AlloyRecipe.LOADER.serialize((AlloyRecipe) recipe);
            case "melting" -> MeltingRecipe.LOADER.serialize((MeltingRecipe) recipe);
            case "ore_melting" -> OreMeltingRecipe.LOADER.serialize((OreMeltingRecipe) recipe);
            case "damagable_melting" -> DamageableMeltingRecipe.LOADER.serialize((DamageableMeltingRecipe) recipe);
            case "material_melting" -> MaterialMeltingRecipe.LOADER.serialize((MaterialMeltingRecipe) recipe);
            default -> null;
        };
        if (fields == null) {
            return recipe;
        }
        var json = fields.getAsJsonObject();
        boolean changed = switch (kind.getPath()) {
            case "casting_table", "casting_basin", "material_fluid" -> adjustInput(json, "fluid");
            case "alloy" -> adjustAlloy(json);
            default -> adjustMelting(json);
        };
        return changed ? serializer.fromJson(recipe.getId(), json) : recipe;
    }

    private static boolean adjustMelting(JsonObject recipe) {
        boolean changed = adjustOutput(recipe.get("result"));
        for (JsonElement byproduct : GsonHelper.getAsJsonArray(recipe, "byproducts", new JsonArray())) {
            changed |= adjustOutput(byproduct);
        }
        return changed;
    }

    private static boolean adjustAlloy(JsonObject recipe) {
        boolean changed = adjustOutput(recipe.get("result"));
        var inputs = GsonHelper.getAsJsonArray(recipe, "inputs");
        for (int index = 0; index < inputs.size(); index++) {
            var input = inputs.get(index);
            if (!input.isJsonObject()) {
                continue;
            }
            var object = input.getAsJsonObject();
            if (object.has("match")) {
                changed |= adjustInput(object, "match");
            } else {
                var alternatives = inputAlternatives(object);
                if (alternatives != null) {
                    var match = new JsonObject();
                    match.add("match", alternatives);
                    if (object.has("catalyst")) {
                        match.add("catalyst", object.get("catalyst"));
                    }
                    inputs.set(index, match);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean adjustInput(JsonObject recipe, String key) {
        var input = recipe.get(key);
        if (input == null || !input.isJsonObject()) {
            return false;
        }
        var alternatives = inputAlternatives(input.getAsJsonObject());
        if (alternatives == null) {
            return false;
        }
        recipe.add(key, alternatives);
        return true;
    }

    private static JsonArray inputAlternatives(JsonObject input) {
        String tag = GsonHelper.getAsString(input, "tag", "");
        int amount = GsonHelper.getAsInt(input, "amount", 0);
        String metal = sharedMetal(tag);
        boolean diamond = tag.equals(TINKERS_DIAMOND);
        if (amount <= 0 || (metal.isEmpty() && !diamond)) {
            return null;
        }
        var legacy = new JsonObject();
        legacy.addProperty("fluid", "nuclearcraft:" + (diamond ? "diamond" : metal));
        // Round a fractional volume up so repeated conversions cannot create material.
        legacy.addProperty("amount", diamond ? (amount * 666L + 99) / 100 : (amount * 144L + 89) / 90);
        var standard = input.deepCopy();
        standard.remove("catalyst");
        if (!diamond) {
            standard.addProperty("tag", STANDARD_METAL + metal);
        }
        var result = new JsonArray();
        result.add(legacy);
        result.add(standard);
        return result;
    }

    private static boolean adjustOutput(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        var output = element.getAsJsonObject();
        var metal = sharedMetal(GsonHelper.getAsString(output, "tag", ""));
        if (metal.isEmpty()) {
            return false;
        }
        output.addProperty("tag", STANDARD_METAL + metal);
        return true;
    }

    private static String sharedMetal(String tag) {
        if (tag.startsWith(FORGE_MOLTEN)) {
            var metal = tag.substring(FORGE_MOLTEN.length());
            if (SHARED_METALS.contains(metal)) {
                return metal;
            }
        }
        return "";
    }
}
