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

public record EnricherRecipe(ResourceLocation id, ItemInput itemInput, FluidInput fluidInput,
                             FluidProduct fluidProduct, int ticks, int energyPerTick, double radiation,
                             @Nullable ProcessorRecipeDefaults defaults) implements FluidProcessorRecipe {
    @Override
    public List<FluidInput> inputs() {
        return List.of(fluidInput);
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
        return NCRegistries.ENRICHER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.ENRICHING.get();
    }

    public static final class Serializer implements RecipeSerializer<EnricherRecipe> {
        @Override
        public EnricherRecipe fromJson(ResourceLocation id, JsonObject json) {
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            var item = ItemInput.readSlot(json.get("item_input"), id);
            var fluid = FluidInput.readSlot(json.get("fluid_input"), id);
            var result = FluidProduct.readSlot(id, json.get("result"));
            if (ticks < 1 || energy < 0 || (item.isEmpty() && fluid.isEmpty() && result.isEmpty())) {
                throw new JsonSyntaxException("Invalid Enricher recipe " + id);
            }
            return new EnricherRecipe(id, item, fluid, result, ticks, energy, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
        }

        @Override
        public EnricherRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var item = ItemInput.fromNetwork(buffer);
            return new EnricherRecipe(id, item, FluidInput.fromNetwork(buffer), FluidProduct.fromNetwork(buffer),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), null);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, EnricherRecipe recipe) {
            ItemInput.write(buffer, recipe.itemInput);
            FluidInput.write(buffer, recipe.fluidInput);
            recipe.fluidProduct.toNetwork(buffer);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
