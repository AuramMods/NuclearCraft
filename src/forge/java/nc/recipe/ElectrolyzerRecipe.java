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

public record ElectrolyzerRecipe(ResourceLocation id, FluidIngredient input, List<FluidProduct> products,
                                 int ticks, int energyPerTick, double radiation,
                                 @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    public static final int OUTPUTS = 4;

    public ElectrolyzerRecipe {
        products = List.copyOf(products);
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
            FluidIngredient input = FluidIngredient.readSlot(json.get("fluid_input"), id);
            var entries = GsonHelper.getAsJsonArray(json, "results");
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (entries.size() > OUTPUTS || ticks < 1 || energy < 0) {
                throw new JsonSyntaxException("Invalid Electrolyzer recipe " + id);
            }
            var outputs = FluidProduct.readSlots(id, entries);
            if (input.isEmpty() && outputs.stream().allMatch(FluidProduct::isEmpty)) {
                throw new JsonSyntaxException("Electrolyzer recipe needs an input or output for " + id);
            }
            return new ElectrolyzerRecipe(id, input, outputs, ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public ElectrolyzerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            FluidIngredient input = FluidIngredient.fromNetwork(buffer);
            var outputs = buffer.readList(FluidProduct::fromNetwork);
            return new ElectrolyzerRecipe(id, input, outputs, buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(),
                    ProcessorRecipeDefaults.fromNetwork(buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ElectrolyzerRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeCollection(recipe.products, (packet, product) -> product.toNetwork(packet));
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
            ProcessorRecipeDefaults.toNetwork(buffer, recipe.defaults);
        }
    }
}
