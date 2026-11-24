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

import java.util.List;

public record SupercoolerRecipe(ResourceLocation id, FluidIngredient input, FluidStack product,
                               int ticks, int energyPerTick, double radiation) implements FluidProcessorRecipe {
    public SupercoolerRecipe {
        product = product.copy();
    }

    @Override
    public FluidStack product() {
        return product.copy();
    }

    @Override
    public List<FluidIngredient> inputs() {
        return List.of(input);
    }

    @Override
    public List<FluidStack> results() {
        return List.of(product());
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
            if (ticks < 1 || energy < 0) {
                throw new JsonSyntaxException("Invalid Supercooler recipe " + id);
            }
            return new SupercoolerRecipe(id, FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), id),
                    FluidIngredient.readResult(GsonHelper.getAsJsonObject(json, "result"), id), ticks, energy, ProcessingRecipe.readRadiation(json, id));
        }

        @Override
        public SupercoolerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new SupercoolerRecipe(id, FluidIngredient.fromNetwork(buffer), buffer.readFluidStack(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SupercoolerRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeFluidStack(recipe.product);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
