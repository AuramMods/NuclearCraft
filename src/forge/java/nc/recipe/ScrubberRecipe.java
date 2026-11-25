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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record ScrubberRecipe(ResourceLocation id, @Nullable ItemProcessorRecipe.Input itemInput,
                             @Nullable FluidInput fluidInput, ItemProcessorRecipe.Product itemProduct,
                             FluidProduct fluidProduct, @Nullable Medium medium, int customTicks, int customPower,
                             double customEfficiency) implements Recipe<Container> {
    public ItemStack itemOutput() { return itemProduct.stack(); }
    public FluidStack fluidOutput() { return fluidProduct.stack(); }

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
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override
        public ScrubberRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            var itemEntry = json.get("item_input");
            var item = itemEntry == null || MachineInputs.unusedSlot(itemEntry) ? null : ItemProcessorRecipe.Input.readSlot(itemEntry, id);
            var fluidEntry = json.get("fluid_input");
            var fluid = fluidEntry == null || MachineInputs.unusedSlot(fluidEntry) ? null : FluidInput.readSlot(fluidEntry, id);
            var product = ItemProcessorRecipe.Product.readSlot(json.get("item_output"), context);
            var result = json.has("fluid_output") ? FluidProduct.readSlot(id, json.get("fluid_output")) : FluidProduct.EMPTY;
            if ((item == null && fluid == null && product.isEmpty() && result.isEmpty())
                    || (item != null && item.ingredient().isEmpty())
                    || (fluid != null && fluid.maxAmount() > ProcessorFluidTanks.CAPACITY)
                    || result.stack().getAmount() > ProcessorFluidTanks.CAPACITY) {
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
            var item = buffer.readNullable(ItemProcessorRecipe.Input::fromNetwork);
            var fluid = buffer.readNullable(FluidInput::fromNetwork);
            return new ScrubberRecipe(id, item, fluid, ItemProcessorRecipe.Product.fromNetwork(buffer),
                    FluidProduct.fromNetwork(buffer), null,
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ScrubberRecipe recipe) {
            buffer.writeNullable(recipe.itemInput, (output, item) -> item.toNetwork(output));
            buffer.writeNullable(recipe.fluidInput, FluidInput::write);
            recipe.itemProduct.toNetwork(buffer);
            recipe.fluidProduct.toNetwork(buffer);
            buffer.writeVarInt(recipe.ticks());
            buffer.writeVarInt(recipe.power());
            buffer.writeDouble(recipe.efficiency());
        }
    }
}
