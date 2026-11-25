package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCHeatExchangers;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record CondenserRecipe(ResourceLocation id, FluidInput input, FluidProduct product, double cooling,
                              int inputTemperature, int outputTemperature, int preferredFlow, double flowBonus)
        implements Recipe<Container> {
    public double flowMultiplier(Vec3 flow) {
        return HeatExchangerRecipe.flowMultiplier(flow, preferredFlow, flowBonus);
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCHeatExchangers.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCHeatExchangers.RECIPES.get();
    }

    public static final class Serializer implements RecipeSerializer<CondenserRecipe> {
        @Override
        public CondenserRecipe fromJson(ResourceLocation id, JsonObject json) {
            FluidInput input = FluidInput.readRequired(json.get("input"), id);
            FluidProduct product = FluidProduct.readSlot(id, json.get("output"));
            double cooling = GsonHelper.getAsDouble(json, "cooling");
            int inputTemperature = GsonHelper.getAsInt(json, "input_temperature");
            int outputTemperature = GsonHelper.getAsInt(json, "output_temperature");
            int preferredFlow = GsonHelper.getAsInt(json, "preferred_flow", 0);
            double flowBonus = GsonHelper.getAsDouble(json, "flow_bonus", 0D);
            if (!Double.isFinite(cooling) || cooling <= 0D
                    || outputTemperature < 0 || inputTemperature < outputTemperature || preferredFlow < -1
                    || preferredFlow > 1 || !Double.isFinite(flowBonus) || flowBonus < 0D) {
                throw new JsonSyntaxException("Invalid condenser recipe " + id);
            }
            return new CondenserRecipe(id, input, product, cooling, inputTemperature,
                    outputTemperature, preferredFlow, flowBonus);
        }

        @Override
        public CondenserRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new CondenserRecipe(id, FluidInput.fromNetwork(buffer), FluidProduct.fromNetwork(buffer), buffer.readDouble(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readByte(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CondenserRecipe recipe) {
            FluidInput.write(buffer, recipe.input);
            recipe.product.toNetwork(buffer);
            buffer.writeDouble(recipe.cooling);
            buffer.writeVarInt(recipe.inputTemperature);
            buffer.writeVarInt(recipe.outputTemperature);
            buffer.writeByte(recipe.preferredFlow);
            buffer.writeDouble(recipe.flowBonus);
        }
    }
}
