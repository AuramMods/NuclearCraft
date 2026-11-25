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

public record SaltMixerRecipe(ResourceLocation id, List<FluidIngredient> inputs, FluidProduct fluidProduct,
                              int ticks, int energyPerTick, double radiation,
                              @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    public SaltMixerRecipe {
        inputs = List.copyOf(inputs);
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
                    FluidProduct.fromJson(id, GsonHelper.getAsJsonObject(json, "result")), ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public SaltMixerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var inputs = FluidIngredient.readPair(buffer);
            return new SaltMixerRecipe(id, inputs, FluidProduct.fromNetwork(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(),
                    ProcessorRecipeDefaults.fromNetwork(buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SaltMixerRecipe recipe) {
            recipe.inputs.forEach(input -> input.toNetwork(buffer));
            recipe.fluidProduct.toNetwork(buffer);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
            ProcessorRecipeDefaults.toNetwork(buffer, recipe.defaults);
        }
    }
}
