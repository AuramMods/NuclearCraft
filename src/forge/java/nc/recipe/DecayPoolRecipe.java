package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCMachines;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public record DecayPoolRecipe(ResourceLocation id, boolean source, Ingredient itemInput, int itemCount,
                              @Nullable FluidIngredient fluidInput, ItemStack itemResult, FluidStack fluidResult,
                              double lifetime, double heat) implements Recipe<Container> {
    public boolean accepts(ItemStack stack) {
        return itemCount > 0 && !stack.isEmpty() && itemInput.test(stack);
    }

    public boolean accepts(FluidStack stack) {
        return fluidInput != null && fluidInput.accepts(stack);
    }

    @Override public boolean matches(Container container, Level level) {
        return fluidInput == null && !container.isEmpty() && accepts(container.getItem(0))
                && container.getItem(0).getCount() >= itemCount;
    }

    @Override public ItemStack assemble(Container container, RegistryAccess registries) { return itemResult.copy(); }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return itemResult.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return (source ? NCMachines.HEAT_SOURCE_SERIALIZER : NCMachines.POOL_SERIALIZER).get(); }
    @Override public RecipeType<?> getType() { return (source ? NCMachines.HEAT_SOURCES : NCMachines.POOL_RECIPES).get(); }

    public record Serializer(boolean source) implements RecipeSerializer<DecayPoolRecipe> {
        @Override
        public DecayPoolRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override
        public DecayPoolRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            var item = Ingredient.EMPTY;
            int count = 0;
            if (source && json.has("item_input")) {
                var input = GsonHelper.getAsJsonObject(json, "item_input");
                item = Ingredient.fromJson(GsonHelper.getNonNull(input, "ingredient"));
                count = GsonHelper.getAsInt(input, "count", 1);
                if (count < 1 || count > 64) {
                    throw new JsonSyntaxException("Invalid Decay Pool item input count in " + id);
                }
            }
            var fluid = json.has("fluid_input") ? FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), id) : null;
            var itemResult = source && json.has("item_result")
                    ? readItem(GsonHelper.getAsJsonObject(json, "item_result"), context) : ItemStack.EMPTY;
            var fluidResult = json.has("fluid_result")
                    ? FluidIngredient.readResult(GsonHelper.getAsJsonObject(json, "fluid_result"), id) : FluidStack.EMPTY;
            double lifetime = source ? GsonHelper.getAsDouble(json, "lifetime") : 0D;
            double heat = GsonHelper.getAsDouble(json, source ? "heat_per_tick" : "heat_per_batch");
            if ((count == 0 && fluid == null) || (itemResult.isEmpty() && fluidResult.isEmpty())
                    || (!source && (fluid == null || fluidResult.isEmpty())) || !Double.isFinite(lifetime) || lifetime < 0
                    || !Double.isFinite(heat) || heat < 0 || (!source && heat < 1)) {
                throw new JsonSyntaxException("Invalid Decay Pool recipe " + id);
            }
            return new DecayPoolRecipe(id, source, item, count, fluid, itemResult, fluidResult, lifetime, heat);
        }

        private static ItemStack readItem(JsonObject json, ICondition.IContext context) {
            var stack = ItemRecipeResult.read(json, context);
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) {
                throw new JsonSyntaxException("Invalid Decay Pool item output");
            }
            return stack;
        }

        @Override
        public DecayPoolRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new DecayPoolRecipe(id, source, Ingredient.fromNetwork(buffer), buffer.readVarInt(),
                    buffer.readNullable(FluidIngredient::fromNetwork), buffer.readItem(), buffer.readFluidStack(),
                    buffer.readDouble(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, DecayPoolRecipe recipe) {
            recipe.itemInput().toNetwork(buffer);
            buffer.writeVarInt(recipe.itemCount());
            buffer.writeNullable(recipe.fluidInput(), (out, fluid) -> fluid.toNetwork(out));
            buffer.writeItem(recipe.itemResult());
            buffer.writeFluidStack(recipe.fluidResult());
            buffer.writeDouble(recipe.lifetime());
            buffer.writeDouble(recipe.heat());
        }
    }
}
