package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCHeatExchangers;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record CondenserCoolantRecipe(ResourceLocation id, FluidInput input, int temperature, int priority)
        implements Recipe<Container> {
    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override public ItemStack assemble(Container container, RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeType<?> getType() { return NCHeatExchangers.COOLANT_RECIPES.get(); }
    @Override public RecipeSerializer<?> getSerializer() { return NCHeatExchangers.COOLANT_SERIALIZER.get(); }

    public static final class Serializer implements RecipeSerializer<CondenserCoolantRecipe> {
        @Override
        public CondenserCoolantRecipe fromJson(ResourceLocation id, JsonObject json) {
            FluidInput input = FluidInput.readRequired(json.get("input"), id, 1);
            int temperature = GsonHelper.getAsInt(json, "temperature", 300);
            if (temperature < 0) {
                throw new JsonSyntaxException("Condenser coolant temperature must be nonnegative for " + id);
            }
            return new CondenserCoolantRecipe(id, input, temperature, GsonHelper.getAsInt(json, "priority", 0));
        }

        @Override
        public CondenserCoolantRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new CondenserCoolantRecipe(id, FluidInput.fromNetwork(buffer), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CondenserCoolantRecipe recipe) {
            FluidInput.write(buffer, recipe.input);
            buffer.writeVarInt(recipe.temperature).writeVarInt(recipe.priority);
        }
    }
}
