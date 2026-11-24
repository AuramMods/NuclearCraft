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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LargeElectrolyzerRecipe(ResourceLocation id, List<Input> items, List<FluidIngredient> fluids,
                                     List<ItemStack> itemOutputs, List<FluidStack> fluidOutputs,
                                     double time, double power, String electrolyte) implements Recipe<Container>, MachineInputs {
    public static final int ITEM_OUTPUTS = 4;
    public static final int FLUID_OUTPUTS = 4;

    public LargeElectrolyzerRecipe {
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
        itemOutputs = itemOutputs.stream().map(ItemStack::copy).toList();
        fluidOutputs = fluidOutputs.stream().map(FluidStack::copy).toList();
    }

    @Override public List<ItemStack> itemOutputs() { return itemOutputs.stream().map(ItemStack::copy).toList(); }
    @Override public List<FluidStack> fluidOutputs() { return fluidOutputs.stream().map(FluidStack::copy).toList(); }
    public ItemStack itemOutput(int slot) { return itemOutputs.get(slot).copy(); }
    public FluidStack fluidOutput(int slot) { return fluidOutputs.get(slot).copy(); }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return getResultItem(access); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) {
        return itemOutputs.stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY).copy();
    }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCMachines.ELECTROLYZER_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCMachines.ELECTROLYZER_RECIPES.get(); }

    public static final class Serializer implements RecipeSerializer<LargeElectrolyzerRecipe> {
        @Override public LargeElectrolyzerRecipe fromJson(ResourceLocation id, JsonObject json) {
            var items = MachineInputs.readItems(GsonHelper.getAsJsonArray(json, "item_inputs"), id);
            var fluids = MachineInputs.readFluids(GsonHelper.getAsJsonArray(json, "fluid_inputs"), id);
            var itemResults = GsonHelper.getAsJsonArray(json, "item_outputs");
            var fluidResults = GsonHelper.getAsJsonArray(json, "fluid_outputs");
            if (itemResults.size() > ITEM_OUTPUTS || fluidResults.size() > FLUID_OUTPUTS) {
                throw new JsonSyntaxException("Too many Electrolyzer outputs for " + id);
            }
            var itemOutputs = new ArrayList<>(Collections.nCopies(ITEM_OUTPUTS, ItemStack.EMPTY));
            var fluidOutputs = new ArrayList<>(Collections.nCopies(FLUID_OUTPUTS, FluidStack.EMPTY));
            for (int i = 0; i < itemResults.size(); i++) {
                if (itemResults.get(i).isJsonNull()) continue;
                var result = CraftingHelper.getItemStack(GsonHelper.convertToJsonObject(itemResults.get(i), "item output"), true);
                if (result.isEmpty() || result.getCount() > result.getMaxStackSize()) {
                    throw new JsonSyntaxException("Invalid Electrolyzer item output for " + id);
                }
                itemOutputs.set(i, result);
            }
            for (int i = 0; i < fluidResults.size(); i++) {
                if (!fluidResults.get(i).isJsonNull()) fluidOutputs.set(i, FluidIngredient.readResult(
                        GsonHelper.convertToJsonObject(fluidResults.get(i), "fluid output"), id));
            }
            double time = GsonHelper.getAsDouble(json, "time_multiplier", 1);
            double power = GsonHelper.getAsDouble(json, "power_multiplier", 1);
            String electrolyte = GsonHelper.getAsString(json, "electrolyte_group");
            if (items.isEmpty() && fluids.isEmpty() || itemOutputs.stream().allMatch(ItemStack::isEmpty)
                    && fluidOutputs.stream().allMatch(FluidStack::isEmpty) || !Double.isFinite(time) || time <= 0
                    || !Double.isFinite(power) || power < 0 || electrolyte.isBlank()) {
                throw new JsonSyntaxException("Invalid Electrolyzer recipe " + id);
            }
            return new LargeElectrolyzerRecipe(id, items, fluids, itemOutputs, fluidOutputs, time, power, electrolyte);
        }

        @Override public LargeElectrolyzerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var items = MachineInputs.readItems(buffer);
            var fluids = MachineInputs.readFluids(buffer);
            var itemOutputs = new ArrayList<ItemStack>(ITEM_OUTPUTS);
            var fluidOutputs = new ArrayList<FluidStack>(FLUID_OUTPUTS);
            for (int i = 0; i < ITEM_OUTPUTS; i++) itemOutputs.add(buffer.readItem());
            for (int i = 0; i < FLUID_OUTPUTS; i++) fluidOutputs.add(buffer.readFluidStack());
            return new LargeElectrolyzerRecipe(id, items, fluids, itemOutputs, fluidOutputs,
                    buffer.readDouble(), buffer.readDouble(), buffer.readUtf());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, LargeElectrolyzerRecipe recipe) {
            recipe.writeInputs(buffer);
            for (var output : recipe.itemOutputs) buffer.writeItem(output);
            for (var output : recipe.fluidOutputs) buffer.writeFluidStack(output);
            buffer.writeDouble(recipe.time);
            buffer.writeDouble(recipe.power);
            buffer.writeUtf(recipe.electrolyte);
        }
    }
}
