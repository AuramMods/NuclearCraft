package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.HeatExchangerConfig;
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
import net.minecraftforge.fluids.FluidStack;

import java.util.Locale;

public record HeatExchangerRecipe(ResourceLocation id, FluidInput input, FluidProduct product,
                                  FluidProduct alternateProduct, Alternate alternate, double heat, HeatSource heatSource,
                                  int inputTemperature, int outputTemperature, boolean equalTemperatureHeating,
                                  int preferredFlow, double flowBonus) implements Recipe<Container> {
    public enum HeatSource {
        FIXED, GAS, NAK;

        double multiplier() {
            return switch (this) {
                case FIXED -> 1D;
                case GAS -> HeatExchangerConfig.gasHeat();
                case NAK -> HeatExchangerConfig.nakHeat();
            };
        }
    }

    public enum Alternate {
        NONE, HIGH_PRESSURE, EXHAUST;

        boolean selected() {
            return switch (this) {
                case NONE -> false;
                case HIGH_PRESSURE -> HeatExchangerConfig.alternateHighPressure();
                case EXHAUST -> HeatExchangerConfig.alternateExhaust();
            };
        }
    }

    public FluidProduct outputProduct() {
        return alternate.selected() ? alternateProduct : product;
    }

    public FluidStack output() {
        return outputProduct().stack();
    }

    public boolean heating() {
        return inputTemperature == outputTemperature ? equalTemperatureHeating : inputTemperature < outputTemperature;
    }

    public double heatRequired() {
        return heat * heatSource.multiplier();
    }

    public double flowMultiplier(Vec3 flow) {
        return flowMultiplier(flow, preferredFlow, flowBonus);
    }

    static double flowMultiplier(Vec3 flow, int direction, double bonus) {
        double projection = direction == 0 ? flow.horizontalDistance() : direction * flow.y;
        return Math.max(0D, 1D + bonus * (projection > 0.5D ? 1D : projection > -0.5D ? 0D : -1D));
    }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCHeatExchangers.HEAT_EXCHANGER_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCHeatExchangers.HEAT_EXCHANGER_RECIPES.get(); }

    public static final class Serializer implements RecipeSerializer<HeatExchangerRecipe> {
        @Override
        public HeatExchangerRecipe fromJson(ResourceLocation id, JsonObject json) {
            FluidInput input = FluidInput.readRequired(json.get("input"), id);
            FluidProduct output = FluidProduct.readSlot(id, json.get("output"));
            double heat = GsonHelper.getAsDouble(json, "heat");
            int inputTemperature = GsonHelper.getAsInt(json, "input_temperature");
            int outputTemperature = GsonHelper.getAsInt(json, "output_temperature");
            int preferredFlow = GsonHelper.getAsInt(json, "preferred_flow", 0);
            double flowBonus = GsonHelper.getAsDouble(json, "flow_bonus", 0D);
            boolean heating = GsonHelper.getAsBoolean(json, "heats_at_equal_temperature", false);
            HeatSource heatSource = choice(HeatSource.class, GsonHelper.getAsString(json, "heat_source", "fixed"), id);
            Alternate alternate = choice(Alternate.class, GsonHelper.getAsString(json, "alternate", "none"), id);
            FluidProduct alternateOutput = json.has("alternate_output")
                    ? FluidProduct.readSlot(id, json.get("alternate_output")) : FluidProduct.EMPTY;
            if (!Double.isFinite(heat) || heat <= 0D || inputTemperature < 0 || outputTemperature < 0
                    || preferredFlow < -1 || preferredFlow > 1 || !Double.isFinite(flowBonus) || flowBonus < 0D
                    || ((alternate != Alternate.NONE) != json.has("alternate_output"))) {
                throw new JsonSyntaxException("Invalid heat exchanger recipe " + id);
            }
            return new HeatExchangerRecipe(id, input, output, alternateOutput, alternate, heat, heatSource,
                    inputTemperature, outputTemperature, heating, preferredFlow, flowBonus);
        }

        private static <T extends Enum<T>> T choice(Class<T> type, String name, ResourceLocation id) {
            try {
                return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new JsonSyntaxException("Invalid heat exchanger option " + name + " for " + id, exception);
            }
        }

        @Override
        public HeatExchangerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new HeatExchangerRecipe(id, FluidInput.fromNetwork(buffer), FluidProduct.fromNetwork(buffer),
                    FluidProduct.fromNetwork(buffer), buffer.readEnum(Alternate.class), buffer.readDouble(),
                    buffer.readEnum(HeatSource.class), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readByte(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, HeatExchangerRecipe recipe) {
            FluidInput.write(buffer, recipe.input);
            recipe.product.toNetwork(buffer);
            recipe.alternateProduct.toNetwork(buffer);
            buffer.writeEnum(recipe.alternate);
            buffer.writeDouble(recipe.heat);
            buffer.writeEnum(recipe.heatSource);
            buffer.writeVarInt(recipe.inputTemperature);
            buffer.writeVarInt(recipe.outputTemperature);
            buffer.writeBoolean(recipe.equalTemperatureHeating);
            buffer.writeByte(recipe.preferredFlow);
            buffer.writeDouble(recipe.flowBonus);
        }
    }
}
