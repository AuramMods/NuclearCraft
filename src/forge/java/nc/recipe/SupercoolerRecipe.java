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

import java.util.List;

public record SupercoolerRecipe(ResourceLocation id, FluidIngredient input, FluidProduct fluidProduct,
                               int ticks, int energyPerTick, double radiation,
                               @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    @Override
    public List<FluidIngredient> inputs() {
        return List.of(input);
    }

    public FluidStack product() {
        return fluidProduct.stack();
    }

    @Override
    public List<FluidProduct> products() {
        return List.of(fluidProduct);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRegistries.SUPERCOOLER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.SUPERCOOLING.get();
    }

    public static final class Serializer implements RecipeSerializer<SupercoolerRecipe> {
        @Override
        public SupercoolerRecipe fromJson(ResourceLocation id, JsonObject json) {
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            var input = FluidIngredient.readSlot(json.get("fluid_input"), id);
            var result = FluidProduct.readSlot(id, json.get("result"));
            if (ticks < 1 || energy < 0 || (input.isEmpty() && result.isEmpty())) {
                throw new JsonSyntaxException("Invalid Supercooler recipe " + id);
            }
            return new SupercoolerRecipe(id, input, result, ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public SupercoolerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new SupercoolerRecipe(id, FluidIngredient.fromNetwork(buffer), FluidProduct.fromNetwork(buffer),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(),
                    ProcessorRecipeDefaults.fromNetwork(buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SupercoolerRecipe recipe) {
            recipe.input.toNetwork(buffer);
            recipe.fluidProduct.toNetwork(buffer);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
            ProcessorRecipeDefaults.toNetwork(buffer, recipe.defaults);
        }
    }
}
