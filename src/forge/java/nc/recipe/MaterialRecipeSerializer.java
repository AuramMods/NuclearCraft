package nc.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

/** Resolves configured material tags before the vanilla serializer reads the recipe. */
public record MaterialRecipeSerializer<T extends Recipe<?>>(RecipeSerializer<T> vanilla) implements RecipeSerializer<T> {
    @Override
    public T fromJson(ResourceLocation id, JsonObject json) {
        return fromJson(id, json, ICondition.IContext.EMPTY);
    }

    @Override
    public T fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
        return vanilla.fromJson(id, resolve(json, context, usesTaggedInput(json)), context);
    }

    static JsonObject resolve(JsonObject json, ICondition.IContext context, boolean taggedInput) {
        JsonObject resolved = json.deepCopy();
        MaterialRecipeOutputs.select(GsonHelper.getAsString(json, "result_tag"), context).ifPresent(selected -> {
            String item = BuiltInRegistries.ITEM.getKey(selected).toString();
            if (resolved.get("result").isJsonObject()) {
                resolved.getAsJsonObject("result").addProperty("item", item);
            } else {
                resolved.addProperty("result", item);
            }
        });
        if (taggedInput) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("tag", GsonHelper.getAsString(json, "input_tag"));
            if (resolved.has("key")) {
                resolved.getAsJsonObject("key").add("I", ingredient);
            } else if (resolved.has("ingredients")) {
                resolved.getAsJsonArray("ingredients").set(0, ingredient);
            } else {
                resolved.add("ingredient", ingredient);
            }
        }
        return resolved;
    }

    static boolean usesTaggedInput(JsonObject json) {
        return json.has("input_tag") && MaterialRecipeOutputs.taggedInputs();
    }

    @Override
    public @Nullable T fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        return vanilla.fromNetwork(id, buffer);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, T recipe) {
        vanilla.toNetwork(buffer, recipe);
    }
}
