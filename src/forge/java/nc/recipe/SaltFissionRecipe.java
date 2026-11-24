package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
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

public record SaltFissionRecipe(ResourceLocation id, FluidIngredient fuel, FluidStack depleted, double ticks,
                                int heat, double efficiency, int criticality, int intrinsicFlux, boolean selfPriming,
                                double decayFactor, double radiation) implements Recipe<Container>, FissionFuelStats {
    public SaltFissionRecipe {
        depleted = depleted.copy();
    }

    @Override
    public double runtimeTicks() {
        double scaled = ticks * (NCFission.fuelTimeMultiplier() * NCSaltFission.timeMultiplier());
        return Math.max(Double.MIN_VALUE, Math.min(Double.MAX_VALUE, scaled));
    }

    @Override
    public int runtimeHeat() {
        return (int) (heat * (NCFission.fuelHeatMultiplier() * NCSaltFission.heatMultiplier()));
    }

    @Override
    public double runtimeEfficiency() {
        return Math.min(Double.MAX_VALUE, efficiency * (NCFission.fuelEfficiencyMultiplier() * NCSaltFission.efficiencyMultiplier()));
    }

    @Override
    public FluidStack depleted() {
        return depleted.copy();
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
            var fuel = FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fuel"), id);
            var depleted = FluidIngredient.readResult(GsonHelper.getAsJsonObject(json, "result"), id);
            double ticks = GsonHelper.getAsDouble(json, "ticks");
            int heat = GsonHelper.getAsInt(json, "heat");
            double efficiency = GsonHelper.getAsDouble(json, "efficiency");
            int criticality = GsonHelper.getAsInt(json, "criticality");
            int intrinsicFlux = GsonHelper.getAsInt(json, "intrinsic_flux", 0);
            double decay = GsonHelper.getAsDouble(json, "decay_factor", 0);
            double radiation = GsonHelper.getAsDouble(json, "radiation", 0);
            if (!finiteNonnegative(ticks) || ticks == 0 || heat < 0 || !finiteNonnegative(efficiency)
                    || criticality < 0 || intrinsicFlux < 0 || !finiteNonnegative(decay) || !finiteNonnegative(radiation)) {
                throw new JsonSyntaxException("Invalid molten-salt fission fuel " + id);
            }
            return new SaltFissionRecipe(id, fuel, depleted, ticks, heat, efficiency, criticality, intrinsicFlux,
                    GsonHelper.getAsBoolean(json, "self_priming", false), decay, radiation);
        }

        private static boolean finiteNonnegative(double value) {
            return Double.isFinite(value) && value >= 0;
        }

        @Override
        public SaltFissionRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new SaltFissionRecipe(id, FluidIngredient.fromNetwork(buffer), buffer.readFluidStack(),
                    buffer.readDouble(), buffer.readVarInt(), buffer.readDouble(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SaltFissionRecipe recipe) {
            recipe.fuel.toNetwork(buffer);
            buffer.writeFluidStack(recipe.depleted);
            buffer.writeDouble(recipe.ticks);
            buffer.writeVarInt(recipe.heat);
            buffer.writeDouble(recipe.efficiency);
            buffer.writeVarInt(recipe.criticality);
            buffer.writeVarInt(recipe.intrinsicFlux);
            buffer.writeBoolean(recipe.selfPriming);
            buffer.writeDouble(recipe.decayFactor);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
