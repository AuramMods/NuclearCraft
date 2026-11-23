package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCRegistries;
import nc.processor.ProcessorInputs;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public record ElectrolyzerRecipe(ResourceLocation id, FluidIngredient input, List<FluidStack> results,
                                 int ticks, int energyPerTick) implements Recipe<Container> {
    public static final int OUTPUTS = 4;

    public ElectrolyzerRecipe {
        results = results.stream().map(FluidStack::copy).toList();
    }

    @Override
    public List<FluidStack> results() {
        return results.stream().map(FluidStack::copy).toList();
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container instanceof ProcessorInputs processor && input.matches(processor.fluid());
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRegistries.ELECTROLYZER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.ELECTROLYSIS.get();
    }

    public static final class Serializer implements RecipeSerializer<ElectrolyzerRecipe> {
        @Override
        public ElectrolyzerRecipe fromJson(ResourceLocation id, JsonObject json) {
            FluidIngredient input = FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), id);
            var entries = GsonHelper.getAsJsonArray(json, "results");
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (entries.isEmpty() || entries.size() > OUTPUTS || ticks < 1 || energy < 0) {
                throw new JsonSyntaxException("Invalid Electrolyzer recipe " + id);
            }
            List<FluidStack> outputs = new ArrayList<>(entries.size());
            for (var entry : entries) {
                JsonObject stack = GsonHelper.convertToJsonObject(entry, "result");
                ResourceLocation fluidId = ResourceLocation.tryParse(GsonHelper.getAsString(stack, "fluid"));
                Fluid fluid = fluidId == null ? null : ForgeRegistries.FLUIDS.getValue(fluidId);
                int amount = GsonHelper.getAsInt(stack, "amount");
                if (fluid == null || fluid == Fluids.EMPTY || amount < 1) {
                    throw new JsonSyntaxException("Invalid Electrolyzer result for " + id);
                }
                outputs.add(new FluidStack(fluid, amount));
            }
            return new ElectrolyzerRecipe(id, input, outputs, ticks, energy);
        }

        @Override
        public ElectrolyzerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            FluidIngredient input = FluidIngredient.fromNetwork(buffer);
            List<FluidStack> outputs = buffer.readList(FriendlyByteBuf::readFluidStack);
            return new ElectrolyzerRecipe(id, input, outputs, buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ElectrolyzerRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeCollection(recipe.results, FriendlyByteBuf::writeFluidStack);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
        }
    }
}
