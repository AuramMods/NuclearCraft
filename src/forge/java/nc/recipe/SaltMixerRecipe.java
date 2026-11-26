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

public record SaltMixerRecipe(ResourceLocation id, List<FluidInput> inputs, FluidProduct fluidProduct,
                              double duration, int energyPerTick, double radiation,
                              @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    public SaltMixerRecipe {
        inputs = List.copyOf(inputs);
    }

    public FluidStack product() {
        return fluidProduct.stack();
    }

    @Override
    public int ticks() {
        return (int) Math.ceil(duration);
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
            double ticks = GsonHelper.getAsDouble(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            var inputs = FluidInput.readPair(json, id);
            var result = FluidProduct.readSlot(id, json.get("result"));
            if (!Double.isFinite(ticks) || ticks < 1 || ticks > Integer.MAX_VALUE || energy < 0
                    || (inputs.stream().allMatch(FluidInput::isEmpty) && result.isEmpty())) {
                throw new JsonSyntaxException("Invalid Salt Mixer recipe " + id);
            }
            return new SaltMixerRecipe(id, inputs, result, ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public SaltMixerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var inputs = FluidInput.readPair(buffer);
            return new SaltMixerRecipe(id, inputs, FluidProduct.fromNetwork(buffer), buffer.readDouble(), buffer.readVarInt(), buffer.readDouble(),
                    ProcessorRecipeDefaults.fromNetwork(buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SaltMixerRecipe recipe) {
            recipe.inputs.forEach(input -> FluidInput.write(buffer, input));
            recipe.fluidProduct.toNetwork(buffer);
            buffer.writeDouble(recipe.duration);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
            ProcessorRecipeDefaults.toNetwork(buffer, recipe.defaults);
        }
    }
}
