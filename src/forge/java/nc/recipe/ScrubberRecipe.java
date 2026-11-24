package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCRadiation;
import nc.processor.ProcessorFluidTanks;
import nc.processor.ProcessorInputs;
import nc.radiation.ScrubberSettings.Medium;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record ScrubberRecipe(ResourceLocation id, @Nullable ItemProcessorRecipe.Input itemInput,
                             @Nullable FluidIngredient fluidInput, ItemStack itemOutput, FluidStack fluidOutput,
                             @Nullable Medium medium, int customTicks, int customPower,
                             double customEfficiency) implements Recipe<Container> {
    public ScrubberRecipe {
        itemOutput = itemOutput.copy();
        fluidOutput = fluidOutput.copy();
    }

    @Override public ItemStack itemOutput() { return itemOutput.copy(); }
    @Override public FluidStack fluidOutput() { return fluidOutput.copy(); }

    public int ticks() { return medium == null ? customTicks : medium.ticks(); }
    public int power() { return medium == null ? customPower : medium.power(); }
    public double efficiency() { return medium == null ? customEfficiency : medium.efficiency(); }

    public boolean acceptsItem(ItemStack items) {
        return itemInput != null && itemInput.accepts(items);
    }

    public boolean acceptsFluid(FluidStack fluid) {
        return fluidInput != null && fluidInput.accepts(fluid);
    }

    public boolean matches(ItemStack items, FluidStack fluid) {
        return (itemInput == null ? items.isEmpty() : acceptsItem(items) && items.getCount() >= itemInput.count())
                && (fluidInput == null ? fluid.isEmpty() : fluidInput.matches(fluid));
    }

    @Override
    public boolean matches(Container input, Level level) {
        return input instanceof ProcessorInputs supplies && matches(supplies.getItem(0), supplies.fluid());
    }

    @Override public ItemStack assemble(Container input, RegistryAccess access) { return itemOutput(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return itemOutput(); }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCRadiation.SCRUBBER_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCRadiation.SCRUBBER_RECIPES.get(); }

    public static final class Serializer implements RecipeSerializer<ScrubberRecipe> {
        @Override
        public ScrubberRecipe fromJson(ResourceLocation id, JsonObject json) {
            ItemProcessorRecipe.Input item = null;
            if (json.has("item_input")) {
                var entry = GsonHelper.getAsJsonObject(json, "item_input");
                int count = GsonHelper.getAsInt(entry, "count", 1);
                var ingredient = Ingredient.fromJson(GsonHelper.getNonNull(entry, "ingredient"));
                if (count < 1 || count > 64 || ingredient == Ingredient.EMPTY) {
                    throw new JsonSyntaxException("Invalid Scrubber item input " + id);
                }
                item = new ItemProcessorRecipe.Input(ingredient, count);
            }
            var fluid = json.has("fluid_input")
                    ? FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), id) : null;
            ItemStack product = json.has("item_output")
                    ? CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "item_output"), true) : ItemStack.EMPTY;
            FluidStack result = json.has("fluid_output")
                    ? FluidIngredient.readResult(GsonHelper.getAsJsonObject(json, "fluid_output"), id) : FluidStack.EMPTY;
            if ((item == null && fluid == null) || (product.isEmpty() && result.isEmpty())
                    || product.getCount() > product.getMaxStackSize()
                    || (fluid != null && fluid.amount() > ProcessorFluidTanks.CAPACITY)
                    || result.getAmount() > ProcessorFluidTanks.CAPACITY) {
                throw new JsonSyntaxException("Invalid Scrubber inputs or outputs for " + id);
            }
            if (json.has("medium")) {
                if (json.has("ticks") || json.has("energy_per_tick") || json.has("efficiency")) {
                    throw new JsonSyntaxException("Specify either medium or custom Scrubber statistics for " + id);
                }
                Medium medium;
                try {
                    medium = Medium.valueOf(GsonHelper.getAsString(json, "medium").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new JsonSyntaxException("Unknown Scrubber medium for " + id, exception);
                }
                return new ScrubberRecipe(id, item, fluid, product, result, medium, 0, 0, 0);
            }
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int power = GsonHelper.getAsInt(json, "energy_per_tick");
            double efficiency = GsonHelper.getAsDouble(json, "efficiency");
            if (ticks < 1 || power < 0 || !Double.isFinite(efficiency) || efficiency < 0 || efficiency > 255) {
                throw new JsonSyntaxException("Invalid Scrubber statistics for " + id);
            }
            return new ScrubberRecipe(id, item, fluid, product, result, null, ticks, power, efficiency);
        }

        @Override
        public ScrubberRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var item = buffer.readNullable(input -> new ItemProcessorRecipe.Input(Ingredient.fromNetwork(input), input.readVarInt()));
            var fluid = buffer.readNullable(FluidIngredient::fromNetwork);
            return new ScrubberRecipe(id, item, fluid, buffer.readItem(), buffer.readFluidStack(), null,
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ScrubberRecipe recipe) {
            buffer.writeNullable(recipe.itemInput, (output, item) -> {
                item.ingredient().toNetwork(output);
                output.writeVarInt(item.count());
            });
            buffer.writeNullable(recipe.fluidInput, (output, fluid) -> fluid.toNetwork(output));
            buffer.writeItem(recipe.itemOutput);
            buffer.writeFluidStack(recipe.fluidOutput);
            buffer.writeVarInt(recipe.ticks());
            buffer.writeVarInt(recipe.power());
            buffer.writeDouble(recipe.efficiency());
        }
    }
}
