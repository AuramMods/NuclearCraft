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

public record ChemicalReactorRecipe(ResourceLocation id, List<FluidIngredient> inputs, List<FluidStack> results,
                                    int ticks, int energyPerTick, double radiation,
                                    @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    public static final int OUTPUTS = 2;

    public ChemicalReactorRecipe {
        inputs = List.copyOf(inputs);
        results = results.stream().map(FluidStack::copy).toList();
    }

    @Override
    public List<FluidStack> results() {
        return results.stream().map(FluidStack::copy).toList();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRegistries.CHEMICAL_REACTOR_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.CHEMICAL_REACTIONS.get();
    }

    public static final class Serializer implements RecipeSerializer<ChemicalReactorRecipe> {
        @Override
        public ChemicalReactorRecipe fromJson(ResourceLocation id, JsonObject json) {
            var entries = GsonHelper.getAsJsonArray(json, "results");
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (entries.isEmpty() || entries.size() > OUTPUTS || ticks < 1 || energy < 0) {
                throw new JsonSyntaxException("Invalid Chemical Reactor recipe " + id);
            }
            List<FluidStack> outputs = new ArrayList<>(entries.size());
            for (var entry : entries) {
                outputs.add(FluidIngredient.readResult(GsonHelper.convertToJsonObject(entry, "result"), id));
            }
            return new ChemicalReactorRecipe(id, FluidIngredient.readPair(json, id), outputs, ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public ChemicalReactorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            List<FluidIngredient> inputs = FluidIngredient.readPair(buffer);
            List<FluidStack> outputs = buffer.readList(FriendlyByteBuf::readFluidStack);
            return new ChemicalReactorRecipe(id, inputs, outputs, buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), null);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ChemicalReactorRecipe recipe) {
            recipe.inputs.forEach(input -> input.toNetwork(buffer));
            buffer.writeCollection(recipe.results, FriendlyByteBuf::writeFluidStack);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
