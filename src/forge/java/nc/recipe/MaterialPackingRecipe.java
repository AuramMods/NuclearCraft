package nc.recipe;

import com.google.gson.JsonObject;
import nc.init.NCRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

public final class MaterialPackingRecipe extends ShapedRecipe {
    private MaterialPackingRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getWidth(), recipe.getHeight(),
                recipe.getIngredients(), recipe.getResultItem(RegistryAccess.EMPTY), recipe.showNotification());
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        if (!super.matches(grid, level)) {
            return false;
        }
        // The legacy recipe registered a separate nine-ingot pattern for each ore-dictionary item.
        ItemStack first = ItemStack.EMPTY;
        for (ItemStack stack : grid.getItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = stack;
            } else if (!stack.is(first.getItem())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRegistries.MATERIAL_SHAPED.get();
    }

    public static final class Serializer implements RecipeSerializer<ShapedRecipe> {
        @Override
        public ShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override
        public ShapedRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            boolean taggedInput = MaterialRecipeSerializer.usesTaggedInput(json);
            var recipe = SHAPED_RECIPE.fromJson(id, MaterialRecipeSerializer.resolve(json, context, taggedInput), context);
            return taggedInput ? new MaterialPackingRecipe(recipe) : recipe;
        }

        @Override
        public @Nullable ShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var recipe = SHAPED_RECIPE.fromNetwork(id, buffer);
            return recipe == null ? null : new MaterialPackingRecipe(recipe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ShapedRecipe recipe) {
            SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
