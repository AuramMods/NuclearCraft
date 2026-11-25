package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCMachines;
import nc.processor.ProcessorInputs;
import nc.recipe.ItemProcessorRecipe.Input;
import nc.recipe.ItemProcessorRecipe.Product;
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

public record DecayPoolRecipe(ResourceLocation id, boolean source, ItemInput itemInput, FluidInput fluidInput,
                              Product itemProduct, FluidProduct fluidProduct, double lifetime, double heat,
                              boolean heatPerMb) implements Recipe<Container> {
    public boolean accepts(ItemStack stack) {
        return itemInput.accepts(stack);
    }

    public boolean accepts(FluidStack stack) {
        return fluidInput.accepts(stack);
    }

    public double heatFor(int inputAmount) {
        return heatPerMb ? heat * inputAmount : heat;
    }

    @Override public boolean matches(Container container, Level level) {
        var item = container.getContainerSize() == 0 ? ItemStack.EMPTY : container.getItem(0);
        var fluid = container instanceof ProcessorInputs inputs ? inputs.fluid() : FluidStack.EMPTY;
        return (itemInput.isEmpty() ? item.isEmpty() : !item.isEmpty() && itemInput.matches(item))
                && (fluidInput.isEmpty() ? fluid.isEmpty() : !fluid.isEmpty() && fluidInput.matches(fluid));
    }

    @Override public ItemStack assemble(Container container, RegistryAccess registries) { return itemProduct.stack(); }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return itemProduct.stack(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return (source ? NCMachines.HEAT_SOURCE_SERIALIZER : NCMachines.POOL_SERIALIZER).get(); }
    @Override public RecipeType<?> getType() { return (source ? NCMachines.HEAT_SOURCES : NCMachines.POOL_RECIPES).get(); }

    public record Serializer(boolean source) implements RecipeSerializer<DecayPoolRecipe> {
        @Override
        public DecayPoolRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override
        public DecayPoolRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            ItemInput item = source && json.has("item_input") ? ItemInput.readSlot(json.get("item_input"), id) : Input.EMPTY;
            FluidInput fluid = source
                    ? (json.has("fluid_input") ? FluidInput.readSlot(json.get("fluid_input"), id) : FluidIngredient.EMPTY)
                    : FluidInput.readRequired(json.get("fluid_input"), id);
            Product itemProduct = source ? Product.readSlot(json.get("item_result"), context) : Product.EMPTY;
            FluidProduct fluidProduct = json.has("fluid_result") ? FluidProduct.readSlot(id, json.get("fluid_result")) : FluidProduct.EMPTY;
            double lifetime = source ? GsonHelper.getAsDouble(json, "lifetime") : 0D;
            boolean heatPerMb = !source && json.has("heat_per_mb");
            if (heatPerMb && json.has("heat_per_batch")) {
                throw new JsonSyntaxException("Specify heat_per_mb or heat_per_batch in " + id);
            }
            double heat = GsonHelper.getAsDouble(json, source ? "heat_per_tick" : heatPerMb ? "heat_per_mb" : "heat_per_batch");
            if ((item.isEmpty() && fluid.isEmpty()) || !Double.isFinite(lifetime) || lifetime < 0
                    || !Double.isFinite(heat) || heat < 0 || (!source && heat < 1)
                    || (heatPerMb && !Double.isFinite(heat * fluid.maxAmount()))) {
                throw new JsonSyntaxException("Invalid Decay Pool recipe " + id);
            }
            return new DecayPoolRecipe(id, source, item, fluid, itemProduct, fluidProduct, lifetime, heat, heatPerMb);
        }

        @Override
        public DecayPoolRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new DecayPoolRecipe(id, source, ItemInput.fromNetwork(buffer), FluidInput.fromNetwork(buffer),
                    Product.fromNetwork(buffer), FluidProduct.fromNetwork(buffer), buffer.readDouble(), buffer.readDouble(),
                    buffer.readBoolean());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, DecayPoolRecipe recipe) {
            ItemInput.write(buffer, recipe.itemInput());
            FluidInput.write(buffer, recipe.fluidInput());
            recipe.itemProduct().toNetwork(buffer);
            recipe.fluidProduct().toNetwork(buffer);
            buffer.writeDouble(recipe.lifetime());
            buffer.writeDouble(recipe.heat());
            buffer.writeBoolean(recipe.heatPerMb());
        }
    }
}
