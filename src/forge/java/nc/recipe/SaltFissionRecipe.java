package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.LegacyFissionFuelConfig.Setting;
import nc.init.NCFission;
import nc.init.NCSaltFission;
import nc.processor.ProcessorInputs;
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
import net.minecraftforge.fluids.FluidStack;

public record SaltFissionRecipe(ResourceLocation id, FluidInput fuel, FluidProduct product, double ticks,
                                Parameters parameters) implements Recipe<Container>, FissionFuelStats {
    private static final int INGOT_VOLUME = 144;

    public double ticks() {
        double legacyTicks = parameters.number(Setting.FUEL_TIME, 0D);
        return legacyTicks > 0D ? legacyTicks / INGOT_VOLUME : ticks;
    }

    @Override
    public double runtimeTicks() {
        double scaled = ticks() * (NCFission.fuelTimeMultiplier() * NCSaltFission.timeMultiplier());
        return Math.max(Double.MIN_VALUE, Math.min(Double.MAX_VALUE, scaled));
    }

    @Override
    public int runtimeHeat() {
        return (int) (heat() * (NCFission.fuelHeatMultiplier() * NCSaltFission.heatMultiplier()));
    }

    @Override
    public double runtimeEfficiency() {
        return Math.min(Double.MAX_VALUE, efficiency() * (NCFission.fuelEfficiencyMultiplier() * NCSaltFission.efficiencyMultiplier()));
    }

    public FluidStack depleted() {
        return product.stack();
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container instanceof ProcessorInputs inputs && fuel.matches(inputs.fluid());
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
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
        return NCSaltFission.FUEL_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCSaltFission.FUEL_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<SaltFissionRecipe> {
        @Override
        public SaltFissionRecipe fromJson(ResourceLocation id, JsonObject json) {
            var fuel = FluidInput.readRequired(json.get("fuel"), id);
            var product = FluidProduct.readSlot(id, json.get("result"));
            double ticks = GsonHelper.getAsDouble(json, "ticks");
            if (!Double.isFinite(ticks) || ticks <= 0D) {
                throw new JsonSyntaxException("Invalid molten-salt fission fuel " + id);
            }
            return new SaltFissionRecipe(id, fuel, product, ticks, Parameters.fromJson(id, json));
        }

        @Override
        public SaltFissionRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new SaltFissionRecipe(id, FluidInput.fromNetwork(buffer), FluidProduct.fromNetwork(buffer),
                    buffer.readDouble(), Parameters.fromNetwork(buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SaltFissionRecipe recipe) {
            FluidInput.write(buffer, recipe.fuel);
            recipe.product.toNetwork(buffer);
            buffer.writeDouble(recipe.ticks);
            recipe.parameters.toNetwork(buffer);
        }
    }
}
