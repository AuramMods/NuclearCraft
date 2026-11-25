package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCFission;
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
import net.minecraftforge.common.crafting.CraftingHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record FissionIrradiatorRecipe(ResourceLocation id, ItemInput input, ItemStack result,
                                     long fluxRequired, long minFlux, long maxFlux, double rawHeatPerFlux,
                                     double rawEfficiency, double radiation, @Nullable Material material)
        implements Recipe<Container> {
    public enum Material {
        THORIUM(0), TBP(0), BISMUTH(0.5D);

        public final double defaultEfficiency;

        Material(double defaultEfficiency) {
            this.defaultEfficiency = defaultEfficiency;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public double heatPerFlux() {
        return rawHeatPerFlux >= 0 ? rawHeatPerFlux : NCFission.irradiatorHeat(material);
    }

    public double efficiency() {
        return rawEfficiency >= 0 ? rawEfficiency : NCFission.irradiatorEfficiency(material);
    }

    public boolean inFluxRange(long flux) {
        return flux >= minFlux && (maxFlux < 0 || flux <= maxFlux);
    }

    public boolean accepts(ItemStack stack) {
        return input.accepts(stack);
    }

    public boolean matchesInput(ItemStack stack) {
        return input.matches(stack);
    }

    @Override public boolean matches(Container container, Level level) { return matchesInput(container.getItem(0)); }
    @Override public ItemStack assemble(Container container, RegistryAccess registries) { return result(); }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return result(); }
    @Override public ItemStack result() { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCFission.IRRADIATOR_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCFission.IRRADIATION.get(); }

    public static final class Serializer implements RecipeSerializer<FissionIrradiatorRecipe> {
        @Override
        public FissionIrradiatorRecipe fromJson(ResourceLocation id, JsonObject json) {
            int count = GsonHelper.getAsInt(json, "count", 1);
            ItemInput input = ItemInput.readIngredientSlot(json.get("input"), id, count);
            ItemStack result = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
            long required = GsonHelper.getAsLong(json, "flux_required");
            long min = GsonHelper.getAsLong(json, "min_flux", 0);
            long max = GsonHelper.getAsLong(json, "max_flux", -1);
            Material material = null;
            if (json.has("material")) {
                try {
                    material = Material.valueOf(GsonHelper.getAsString(json, "material").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new JsonSyntaxException("Unknown irradiation material in " + id, exception);
                }
            }
            double heat = GsonHelper.getAsDouble(json, "heat_per_flux", material == null ? 0 : -1);
            double efficiency = GsonHelper.getAsDouble(json, "efficiency", material == null ? 0 : -1);
            double radiation = GsonHelper.getAsDouble(json, "radiation", 0);
            if (input.matches(ItemStack.EMPTY)
                    || (input instanceof ItemProcessorRecipe.Input single && single.ingredient().isEmpty()) || result.isEmpty() || result.getCount() > result.getMaxStackSize()
                    || count < 1 || count > 64 || required < 1 || min < -1 || max < -1 || max >= 0 && max < min
                    || !stat(heat, json.has("heat_per_flux")) || !stat(efficiency, json.has("efficiency"))
                    || !Double.isFinite(radiation) || radiation < 0) {
                throw new JsonSyntaxException("Invalid irradiation recipe " + id);
            }
            return new FissionIrradiatorRecipe(id, input, result, required, min, max, heat, efficiency, radiation, material);
        }

        private static boolean stat(double value, boolean explicit) {
            return Double.isFinite(value) && (value >= 0 || !explicit && value == -1);
        }

        @Override
        public FissionIrradiatorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new FissionIrradiatorRecipe(id, ItemInput.fromNetwork(buffer),
                    buffer.readItem(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), null);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FissionIrradiatorRecipe recipe) {
            ItemInput.write(buffer, recipe.input);
            buffer.writeItem(recipe.result);
            buffer.writeVarLong(recipe.fluxRequired);
            buffer.writeVarLong(recipe.minFlux);
            buffer.writeVarLong(recipe.maxFlux);
            buffer.writeDouble(recipe.heatPerFlux());
            buffer.writeDouble(recipe.efficiency());
            buffer.writeDouble(recipe.radiation);
        }
    }
}
