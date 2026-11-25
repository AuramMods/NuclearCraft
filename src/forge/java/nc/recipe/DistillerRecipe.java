package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCItemProcessors;
import nc.init.NCMachines;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DistillerRecipe(ResourceLocation id, List<FluidIngredient> inputs, List<FluidProduct> products,
                              double timeMultiplier, double powerMultiplier, int sieveTrays,
                              boolean stockBatch, double radiation) implements ProcessingRecipe {
    public static final int INPUT_COUNT = 2;
    public static final int OUTPUT_COUNT = 8;

    public DistillerRecipe {
        inputs = List.copyOf(inputs);
        products = List.copyOf(products);
    }

    public boolean accepts(FluidStack stack) { return inputs.stream().anyMatch(input -> input.accepts(stack)); }

    public boolean acceptsPair(FluidStack first, FluidStack second) {
        return inputs.size() == INPUT_COUNT && (inputs.get(0).accepts(first) && inputs.get(1).accepts(second)
                || inputs.get(1).accepts(first) && inputs.get(0).accepts(second));
    }

    public int firstInput(FluidStack first, FluidStack second, int factor) {
        if (matches(0, first, factor) && matches(1, second, factor)) return 0;
        if (matches(1, first, factor) && matches(0, second, factor)) return 1;
        return -1;
    }

    private boolean matches(int index, FluidStack stack, int factor) {
        return index < inputs.size() ? inputs.get(index).matches(stack, factor) : stack.isEmpty();
    }

    public int inputAmount(int index, int factor) {
        return index < inputs.size() ? inputs.get(index).amount() / factor : 0;
    }

    public int batchFactor() {
        if (!NCMachines.distillerFactorRecipes() && !(stockBatch && NCItemProcessors.factorRecipes())) {
            return 1;
        }
        int factor = inputs.isEmpty() ? 0 : FluidBatch.inputFactor(inputs);
        for (FluidProduct product : products) {
            factor = product.factor(factor);
        }
        return Math.max(1, factor);
    }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess registryAccess) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess registryAccess) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCMachines.DISTILLER_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCMachines.DISTILLER_RECIPES.get(); }

    public static final class Serializer implements RecipeSerializer<DistillerRecipe> {
        @Override public DistillerRecipe fromJson(ResourceLocation id, JsonObject json) {
            var fluidInputs = GsonHelper.getAsJsonArray(json, "fluid_inputs");
            if (fluidInputs.size() > INPUT_COUNT) throw new JsonSyntaxException("Too many Distiller inputs for " + id);
            var inputs = MachineInputs.readFluids(fluidInputs, id);
            var products = GsonHelper.getAsJsonArray(json, "fluid_outputs");
            if (products.size() > OUTPUT_COUNT) throw new JsonSyntaxException("Too many Distiller outputs for " + id);
            var outputs = new ArrayList<>(Collections.nCopies(OUTPUT_COUNT, FluidProduct.EMPTY));
            for (int slot = 0; slot < products.size(); slot++) {
                var product = products.get(slot);
                if (!MachineInputs.unusedSlot(product)) outputs.set(slot, FluidProduct.fromJson(id,
                        GsonHelper.convertToJsonObject(product, "fluid output")));
            }
            double time = GsonHelper.getAsDouble(json, "time_multiplier", 1D);
            double power = GsonHelper.getAsDouble(json, "power_multiplier", 1D);
            int trays = GsonHelper.getAsInt(json, "sieve_trays", 0);
            if (inputs.isEmpty() && outputs.stream().allMatch(FluidProduct::isEmpty) || time <= 0 || !Double.isFinite(time)
                    || power < 0 || !Double.isFinite(power) || trays < 0) {
                throw new JsonSyntaxException("Invalid Distiller recipe " + id);
            }
            return new DistillerRecipe(id, inputs, outputs, time, power, trays,
                    GsonHelper.getAsBoolean(json, "nuclearcraft_stock_batch", false),
                    ProcessingRecipe.readRadiation(json, id));
        }

        @Override public DistillerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var inputs = buffer.readList(FluidIngredient::fromNetwork);
            var outputs = new ArrayList<FluidProduct>(OUTPUT_COUNT);
            for (int slot = 0; slot < OUTPUT_COUNT; slot++) outputs.add(FluidProduct.fromNetwork(buffer));
            return new DistillerRecipe(id, inputs, outputs, buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readDouble());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, DistillerRecipe recipe) {
            buffer.writeCollection(recipe.inputs, (packet, input) -> input.toNetwork(packet));
            for (var output : recipe.products) output.toNetwork(buffer);
            buffer.writeDouble(recipe.timeMultiplier);
            buffer.writeDouble(recipe.powerMultiplier);
            buffer.writeVarInt(recipe.sieveTrays);
            buffer.writeBoolean(recipe.stockBatch);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
