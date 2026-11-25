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

public record CentrifugeRecipe(ResourceLocation id, FluidInput input, List<FluidProduct> products,
                               double duration, int energyPerTick, double radiation,
                               @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    public static final int OUTPUTS = 6;

    public CentrifugeRecipe {
        products = List.copyOf(products);
    }

    @Override
    public int ticks() {
        return (int) Math.ceil(duration);
    }

    @Override
    public List<FluidInput> inputs() {
        return List.of(input);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRegistries.CENTRIFUGE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.CENTRIFUGING.get();
    }

    public static final class Serializer implements RecipeSerializer<CentrifugeRecipe> {
        @Override
        public CentrifugeRecipe fromJson(ResourceLocation id, JsonObject json) {
            double ticks = GsonHelper.getAsDouble(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (!Double.isFinite(ticks) || ticks < 1 || ticks > Integer.MAX_VALUE || energy < 0) {
                throw new JsonSyntaxException("Invalid Centrifuge recipe " + id);
            }

            var results = GsonHelper.getAsJsonArray(json, "results");
            if (results.size() > OUTPUTS) {
                throw new JsonSyntaxException("Expected up to six fluid outputs for " + id);
            }
            var input = FluidInput.readSlot(json.get("fluid_input"), id);
            var products = FluidProduct.readSlots(id, results);
            if (input.isEmpty() && products.stream().allMatch(FluidProduct::isEmpty)) {
                throw new JsonSyntaxException("Centrifuge recipe needs an input or output for " + id);
            }
            return new CentrifugeRecipe(id, input, products, ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public CentrifugeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            FluidInput input = FluidInput.fromNetwork(buffer);
            var products = buffer.readList(FluidProduct::fromNetwork);
            return new CentrifugeRecipe(id, input, products, buffer.readDouble(), buffer.readVarInt(), buffer.readDouble(),
                    ProcessorRecipeDefaults.fromNetwork(buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CentrifugeRecipe recipe) {
            FluidInput.write(buffer, recipe.input);
            buffer.writeCollection(recipe.products, (packet, product) -> product.toNetwork(packet));
            buffer.writeDouble(recipe.duration);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
            ProcessorRecipeDefaults.toNetwork(buffer, recipe.defaults);
        }
    }
}
