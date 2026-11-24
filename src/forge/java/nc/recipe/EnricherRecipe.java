package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public record EnricherRecipe(ResourceLocation id, ItemProcessorRecipe.Input itemInput, FluidIngredient fluidInput,
                             FluidStack product, int ticks, int energyPerTick, double radiation) implements FluidProcessorRecipe {
    public EnricherRecipe {
        product = product.copy();
    }

    @Override
    public FluidStack product() {
        return product.copy();
    }

    @Override
    public List<FluidIngredient> inputs() {
        return List.of(fluidInput);
    }

    @Override
    public List<FluidStack> results() {
        return List.of(product());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRegistries.ENRICHER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.ENRICHING.get();
    }

    public static final class Serializer implements RecipeSerializer<EnricherRecipe> {
        @Override
        public EnricherRecipe fromJson(ResourceLocation id, JsonObject json) {
            var item = GsonHelper.getAsJsonObject(json, "item_input");
            int count = GsonHelper.getAsInt(item, "count", 1);
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (count < 1 || count > 64 || ticks < 1 || energy < 0) {
                throw new JsonSyntaxException("Invalid Enricher recipe " + id);
            }
            var ingredient = Ingredient.fromJson(GsonHelper.getNonNull(item, "ingredient"));
            return new EnricherRecipe(id, new ItemProcessorRecipe.Input(ingredient, count),
                    FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), id),
                    FluidIngredient.readResult(GsonHelper.getAsJsonObject(json, "result"), id), ticks, energy, ProcessingRecipe.readRadiation(json, id));
        }

        @Override
        public EnricherRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var item = new ItemProcessorRecipe.Input(Ingredient.fromNetwork(buffer), buffer.readVarInt());
            return new EnricherRecipe(id, item, FluidIngredient.fromNetwork(buffer), buffer.readFluidStack(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, EnricherRecipe recipe) {
            recipe.itemInput.ingredient().toNetwork(buffer);
            buffer.writeVarInt(recipe.itemInput.count());
            recipe.fluidInput.toNetwork(buffer);
            buffer.writeFluidStack(recipe.product);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
