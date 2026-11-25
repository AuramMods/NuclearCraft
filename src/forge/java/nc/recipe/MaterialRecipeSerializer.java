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

/** Resolves the material output at load time and lets the vanilla serializer build and sync the recipe. */
public record MaterialRecipeSerializer<T extends Recipe<?>>(RecipeSerializer<T> vanilla) implements RecipeSerializer<T> {
    @Override
    public T fromJson(ResourceLocation id, JsonObject json) {
        return fromJson(id, json, ICondition.IContext.EMPTY);
    }

    @Override
    public T fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
        var selected = MaterialRecipeOutputs.select(GsonHelper.getAsString(json, "result_tag"), context);
        if (selected.isEmpty()) {
            return vanilla.fromJson(id, json, context);
        }

        String item = BuiltInRegistries.ITEM.getKey(selected.get()).toString();
        JsonObject resolved = json.deepCopy();
        if (resolved.get("result").isJsonObject()) {
            resolved.getAsJsonObject("result").addProperty("item", item);
        } else {
            resolved.addProperty("result", item);
        }
        return vanilla.fromJson(id, resolved, context);
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
