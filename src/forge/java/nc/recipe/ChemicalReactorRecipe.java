package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ChemicalReactorRecipe(ResourceLocation id, List<FluidIngredient> inputs, List<FluidProduct> products,
                                    int ticks, int energyPerTick, double radiation,
                                    @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    public static final int OUTPUTS = 2;

    public ChemicalReactorRecipe {
        inputs = List.copyOf(inputs);
        products = List.copyOf(products);
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
            var outputs = FluidProduct.readSlots(id, entries);
            return new ChemicalReactorRecipe(id, FluidIngredient.readPair(json, id), outputs, ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public ChemicalReactorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            List<FluidIngredient> inputs = FluidIngredient.readPair(buffer);
            var outputs = buffer.readList(FluidProduct::fromNetwork);
            return new ChemicalReactorRecipe(id, inputs, outputs, buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(),
                    ProcessorRecipeDefaults.fromNetwork(buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ChemicalReactorRecipe recipe) {
            recipe.inputs.forEach(input -> input.toNetwork(buffer));
            buffer.writeCollection(recipe.products, (packet, product) -> product.toNetwork(packet));
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
            ProcessorRecipeDefaults.toNetwork(buffer, recipe.defaults);
        }
    }
}
