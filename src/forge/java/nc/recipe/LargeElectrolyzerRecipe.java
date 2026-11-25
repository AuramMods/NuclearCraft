package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCMachines;
import nc.recipe.ItemProcessorRecipe.Input;
import nc.recipe.ItemProcessorRecipe.Product;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.conditions.ICondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LargeElectrolyzerRecipe(ResourceLocation id, List<Input> items, List<FluidInput> fluids,
                                     List<Product> itemProducts, List<FluidProduct> fluidProducts,
                                     double time, double power, String electrolyte, double radiation)
        implements ProcessingRecipe, MachineInputs {
    public static final int ITEM_OUTPUTS = 4;
    public static final int FLUID_OUTPUTS = 4;

    public LargeElectrolyzerRecipe {
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
        itemProducts = List.copyOf(itemProducts);
        fluidProducts = List.copyOf(fluidProducts);
    }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return getResultItem(access); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) {
        return itemProducts.stream().filter(product -> !product.isEmpty()).findFirst().orElse(Product.EMPTY).stack();
    }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCMachines.ELECTROLYZER_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCMachines.ELECTROLYZER_RECIPES.get(); }

    public static final class Serializer implements RecipeSerializer<LargeElectrolyzerRecipe> {
        @Override public LargeElectrolyzerRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override public LargeElectrolyzerRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            var items = MachineInputs.readItems(GsonHelper.getAsJsonArray(json, "item_inputs"), id);
            var fluids = MachineInputs.readFluids(GsonHelper.getAsJsonArray(json, "fluid_inputs"), id);
            var itemResults = GsonHelper.getAsJsonArray(json, "item_outputs");
            var fluidResults = GsonHelper.getAsJsonArray(json, "fluid_outputs");
            if (itemResults.size() > ITEM_OUTPUTS || fluidResults.size() > FLUID_OUTPUTS) {
                throw new JsonSyntaxException("Too many Electrolyzer outputs for " + id);
            }
            var itemProducts = new ArrayList<>(Collections.nCopies(ITEM_OUTPUTS, Product.EMPTY));
            var fluidProducts = new ArrayList<>(Collections.nCopies(FLUID_OUTPUTS, FluidProduct.EMPTY));
            for (int i = 0; i < itemResults.size(); i++) {
                var result = itemResults.get(i);
                if (!MachineInputs.unusedSlot(result)) {
                    itemProducts.set(i, Product.fromJson(GsonHelper.convertToJsonObject(result, "item output"), context));
                }
            }
            for (int i = 0; i < fluidResults.size(); i++) {
                var result = fluidResults.get(i);
                if (!MachineInputs.unusedSlot(result)) {
                    fluidProducts.set(i, FluidProduct.fromJson(id, GsonHelper.convertToJsonObject(result, "fluid output")));
                }
            }
            double time = GsonHelper.getAsDouble(json, "time_multiplier", 1);
            double power = GsonHelper.getAsDouble(json, "power_multiplier", 1);
            String electrolyte = GsonHelper.getAsString(json, "electrolyte_group");
            boolean empty = items.isEmpty() && fluids.isEmpty() && itemProducts.stream().allMatch(Product::isEmpty)
                    && fluidProducts.stream().allMatch(FluidProduct::isEmpty);
            if (empty || !Double.isFinite(time) || time <= 0
                    || !Double.isFinite(power) || power < 0 || electrolyte.isBlank()) {
                throw new JsonSyntaxException("Invalid Electrolyzer recipe " + id);
            }
            return new LargeElectrolyzerRecipe(id, items, fluids, itemProducts, fluidProducts, time, power, electrolyte,
                    ProcessingRecipe.readRadiation(json, id));
        }

        @Override public LargeElectrolyzerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var items = MachineInputs.readItems(buffer);
            var fluids = MachineInputs.readFluids(buffer);
            var itemProducts = new ArrayList<Product>(ITEM_OUTPUTS);
            var fluidProducts = new ArrayList<FluidProduct>(FLUID_OUTPUTS);
            for (int i = 0; i < ITEM_OUTPUTS; i++) itemProducts.add(Product.fromNetwork(buffer));
            for (int i = 0; i < FLUID_OUTPUTS; i++) fluidProducts.add(FluidProduct.fromNetwork(buffer));
            return new LargeElectrolyzerRecipe(id, items, fluids, itemProducts, fluidProducts,
                    buffer.readDouble(), buffer.readDouble(), buffer.readUtf(), buffer.readDouble());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, LargeElectrolyzerRecipe recipe) {
            recipe.writeInputs(buffer);
            for (var output : recipe.itemProducts) output.toNetwork(buffer);
            for (var output : recipe.fluidProducts) output.toNetwork(buffer);
            buffer.writeDouble(recipe.time);
            buffer.writeDouble(recipe.power);
            buffer.writeUtf(recipe.electrolyte);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
