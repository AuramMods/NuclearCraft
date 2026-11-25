package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCMachines;
import nc.recipe.ItemProcessorRecipe.Input;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public record InfiltratorRecipe(ResourceLocation id, List<Input> items, List<FluidIngredient> fluids, ItemStack product,
                                 double time, double power, double heatingBonus, double radiation)
        implements ProcessingRecipe, MachineInputs {

    public InfiltratorRecipe {
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
        product = product.copy();
    }

    public InfiltratorRecipe(ResourceLocation id, List<Input> items, List<FluidIngredient> fluids, ItemStack product,
                             double time, double power, double heatingBonus) {
        this(id, items, fluids, product, time, power, heatingBonus, 0D);
    }

    @Override public ItemStack product() { return product.copy(); }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return product(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return product(); }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCMachines.INFILTRATOR_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCMachines.INFILTRATOR_RECIPES.get(); }

    public static final class Serializer implements RecipeSerializer<InfiltratorRecipe> {
        @Override public InfiltratorRecipe fromJson(ResourceLocation id, JsonObject json) {
            var items = MachineInputs.readItems(GsonHelper.getAsJsonArray(json, "item_inputs"), id);
            var fluids = MachineInputs.readFluids(GsonHelper.getAsJsonArray(json, "fluid_inputs"), id);
            var product = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
            double time = GsonHelper.getAsDouble(json, "time_multiplier", 1);
            double power = GsonHelper.getAsDouble(json, "power_multiplier", 1);
            double heating = GsonHelper.getAsDouble(json, "heating_bonus", 0);
            if (items.isEmpty() && fluids.isEmpty()
                    || product.isEmpty() || product.getCount() > product.getMaxStackSize()
                    || !Double.isFinite(time) || time <= 0 || !Double.isFinite(power) || power < 0
                    || !Double.isFinite(heating) || heating < 0) throw new JsonSyntaxException("Invalid Infiltrator recipe " + id);
            return new InfiltratorRecipe(id, items, fluids, product, time, power, heating,
                    ProcessingRecipe.readRadiation(json, id));
        }

        @Override public InfiltratorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var items = MachineInputs.readItems(buffer);
            var fluids = MachineInputs.readFluids(buffer);
            return new InfiltratorRecipe(id, items, fluids, buffer.readItem(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, InfiltratorRecipe recipe) {
            recipe.writeInputs(buffer);
            buffer.writeItem(recipe.product);
            buffer.writeDouble(recipe.time).writeDouble(recipe.power).writeDouble(recipe.heatingBonus);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
