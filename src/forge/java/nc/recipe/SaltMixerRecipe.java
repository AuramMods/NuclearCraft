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

public record SaltMixerRecipe(ResourceLocation id, List<FluidIngredient> inputs, FluidStack product,
                              int ticks, int energyPerTick) implements FluidProcessorRecipe {
    public SaltMixerRecipe {
        inputs = List.copyOf(inputs);
        product = product.copy();
    }

    @Override
    public FluidStack product() {
        return product.copy();
    }

    @Override
    public List<FluidStack> results() {
        return List.of(product());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRegistries.SALT_MIXER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.MIXING.get();
    }

    public static final class Serializer implements RecipeSerializer<SaltMixerRecipe> {
        @Override
        public SaltMixerRecipe fromJson(ResourceLocation id, JsonObject json) {
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (ticks < 1 || energy < 0) {
                throw new JsonSyntaxException("Invalid Salt Mixer recipe " + id);
            }
            return new SaltMixerRecipe(id, FluidIngredient.readPair(json, id),
                    FluidIngredient.readResult(GsonHelper.getAsJsonObject(json, "result"), id), ticks, energy);
        }

        @Override
        public SaltMixerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var inputs = FluidIngredient.readPair(buffer);
            return new SaltMixerRecipe(id, inputs, buffer.readFluidStack(), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SaltMixerRecipe recipe) {
            recipe.inputs.forEach(input -> input.toNetwork(buffer));
            buffer.writeFluidStack(recipe.product);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
        }
    }
}
