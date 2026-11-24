package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.HeatExchangerConfig;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
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
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record FissionHeatRecipe(ResourceLocation id, FluidIngredient input, FluidStack output, double baseHeat,
                                @Nullable Profile profile, boolean emergency) implements Recipe<Container> {
    public enum Profile {
        GAS, NAK, EMERGENCY
    }

    public FissionHeatRecipe {
        output = output.copy();
    }

    public double heatPerMb() {
        if (baseHeat > 0D) {
            return baseHeat;
        }
        return switch (profile) {
            case GAS -> Math.max(1, (int) (NCPebbleFission.heatPerMb() * NCFission.heatingGasMultiplier()));
            case NAK -> Math.max(1, (int) (HeatExchangerConfig.nakHeatPerMb() * NCFission.heatingNakMultiplier()));
            case EMERGENCY -> NCFission.emergencyCoolingMultiplier();
        };
    }

    @Override public FluidStack output() { return output.copy(); }

    @Override
    public boolean matches(Container container, Level level) {
        return container instanceof ProcessorInputs inputs && input.matches(inputs.fluid());
    }

    @Override public ItemStack assemble(Container container, RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }

    @Override
    public RecipeType<?> getType() {
        return (emergency ? NCFission.EMERGENCY_COOLING_TYPE : NCFission.HEATING_TYPE).get();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return (emergency ? NCFission.EMERGENCY_COOLING_SERIALIZER : NCFission.HEATING_SERIALIZER).get();
    }

    public record Serializer(boolean emergency) implements RecipeSerializer<FissionHeatRecipe> {
        @Override
        public FissionHeatRecipe fromJson(ResourceLocation id, JsonObject json) {
            var input = FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "input"), id);
            var output = FluidIngredient.readResult(GsonHelper.getAsJsonObject(json, "result"), id);
            Profile profile = null;
            if (json.has("legacy_profile")) {
                String value = GsonHelper.getAsString(json, "legacy_profile");
                try {
                    profile = Profile.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException error) {
                    throw new JsonSyntaxException("Unknown fission heat profile " + value + " for " + id, error);
                }
            }
            double heat = json.has("heat_per_mb") ? GsonHelper.getAsDouble(json, "heat_per_mb") : -1D;
            if ((json.has("heat_per_mb") && (!Double.isFinite(heat) || heat <= 0D))
                    || (!json.has("heat_per_mb") && profile == null)) {
                throw new JsonSyntaxException("Expected a positive heat_per_mb or a legacy_profile for " + id);
            }
            return new FissionHeatRecipe(id, input, output, heat, profile, emergency);
        }

        @Override
        public FissionHeatRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new FissionHeatRecipe(id, FluidIngredient.fromNetwork(buffer), buffer.readFluidStack(),
                    buffer.readDouble(), buffer.readNullable(input -> input.readEnum(Profile.class)), emergency);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FissionHeatRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeFluidStack(recipe.output);
            buffer.writeDouble(recipe.baseHeat);
            buffer.writeNullable(recipe.profile, FriendlyByteBuf::writeEnum);
        }
    }
}
