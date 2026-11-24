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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record ElectrolyzerElectrolyteRecipe(ResourceLocation id, String group, FluidIngredient fluid, double efficiency)
        implements Recipe<Container> {
    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCMachines.ELECTROLYTE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCMachines.ELECTROLYTES.get(); }

    public static final class Serializer implements RecipeSerializer<ElectrolyzerElectrolyteRecipe> {
        @Override public ElectrolyzerElectrolyteRecipe fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group");
            var fluid = FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), id);
            double efficiency = GsonHelper.getAsDouble(json, "efficiency", 1);
            if (group.isBlank() || !Double.isFinite(efficiency) || efficiency < 0) {
                throw new JsonSyntaxException("Invalid Electrolyzer electrolyte " + id);
            }
            return new ElectrolyzerElectrolyteRecipe(id, group, fluid, efficiency);
        }

        @Override public ElectrolyzerElectrolyteRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new ElectrolyzerElectrolyteRecipe(id, buffer.readUtf(), FluidIngredient.fromNetwork(buffer), buffer.readDouble());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, ElectrolyzerElectrolyteRecipe recipe) {
            buffer.writeUtf(recipe.group);
            recipe.fluid.toNetwork(buffer);
            buffer.writeDouble(recipe.efficiency);
        }
    }
}
