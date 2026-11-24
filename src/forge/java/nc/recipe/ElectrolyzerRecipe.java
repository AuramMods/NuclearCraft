package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ElectrolyzerRecipe(ResourceLocation id, FluidIngredient input, List<FluidStack> results,
                                 int ticks, int energyPerTick, double radiation,
                                 @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    public static final int OUTPUTS = 4;

    public ElectrolyzerRecipe {
        results = results.stream().map(FluidStack::copy).toList();
    }

    @Override
    public List<FluidStack> results() {
        return results.stream().map(FluidStack::copy).toList();
    }

    @Override
    public List<FluidIngredient> inputs() {
        return List.of(input);
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
                outputs.add(FluidIngredient.readResult(GsonHelper.convertToJsonObject(entry, "result"), id));
            }
            return new ElectrolyzerRecipe(id, input, outputs, ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public ElectrolyzerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            FluidIngredient input = FluidIngredient.fromNetwork(buffer);
            List<FluidStack> outputs = buffer.readList(FriendlyByteBuf::readFluidStack);
            return new ElectrolyzerRecipe(id, input, outputs, buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), null);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ElectrolyzerRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeCollection(recipe.results, FriendlyByteBuf::writeFluidStack);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
