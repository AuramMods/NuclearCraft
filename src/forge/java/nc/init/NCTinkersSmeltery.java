package nc.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.melting.DamageableMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MaterialMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.OreMeltingRecipe;

final class NCTinkersSmeltery {
    private static final String TINKERS_DIAMOND = "tconstruct:molten_diamond";

    private NCTinkersSmeltery() {}

    static Recipe<?> adjustRecipe(Recipe<?> recipe) {
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
        if (TINKERS_DIAMOND.equals(GsonHelper.getAsString(input, "tag", ""))) {
            return MoltenMetalVolumes.alternatives(input, "diamond", (GsonHelper.getAsInt(input, "amount") * 666L + 99) / 100,
                    TINKERS_DIAMOND);
        }
        return MoltenMetalVolumes.metalAlternatives(input);
    }

    private static boolean adjustOutput(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        var output = element.getAsJsonObject();
        var tag = MoltenMetalVolumes.standardTag(ResourceLocation.tryParse(GsonHelper.getAsString(output, "tag", "")));
        if (tag == null) {
            return false;
        }
        output.addProperty("tag", tag.toString());
        return true;
    }
}
