package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCTurbine;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public record TurbineRecipe(ResourceLocation id, ResourceLocation input, boolean inputTag, int inputAmount,
                            FluidStack output, double powerPerMb, double expansion, double spinUp) implements Recipe<Container> {
    public boolean accepts(FluidStack stack) {
        return !stack.isEmpty() && (inputTag ? stack.getFluid().is(TagKey.create(Registries.FLUID, input))
                : stack.getFluid() == ForgeRegistries.FLUIDS.getValue(input));
    }

    @Override
    public FluidStack output() {
        return output.copy();
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
        return NCTurbine.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCTurbine.RECIPES.get();
    }

    public static final class Serializer implements RecipeSerializer<TurbineRecipe> {
        @Override
        public TurbineRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonObject input = GsonHelper.getAsJsonObject(json, "input");
            JsonObject output = GsonHelper.getAsJsonObject(json, "output");
            boolean tag = input.has("tag");
            ResourceLocation source = location(input, tag ? "tag" : "fluid");
            ResourceLocation destination = location(output, "fluid");
            Fluid product = ForgeRegistries.FLUIDS.getValue(destination);
            int inputAmount = GsonHelper.getAsInt(input, "amount");
            int outputAmount = GsonHelper.getAsInt(output, "amount");
            double power = GsonHelper.getAsDouble(json, "power_per_mb");
            double expansion = GsonHelper.getAsDouble(json, "expansion");
            double spinUp = GsonHelper.getAsDouble(json, "spin_up", 1D);
            if (input.has("fluid") == tag || (!tag && !ForgeRegistries.FLUIDS.containsKey(source))
                    || product == null || product == Fluids.EMPTY || inputAmount <= 0 || outputAmount <= 0
                    || !Double.isFinite(power) || power < 0 || !Double.isFinite(expansion) || expansion < 1
                    || !Double.isFinite(spinUp) || spinUp < 0) {
                throw new JsonSyntaxException("Invalid turbine recipe " + id);
            }
            return new TurbineRecipe(id, source, tag, inputAmount, new FluidStack(product, outputAmount), power, expansion, spinUp);
        }

        private static ResourceLocation location(JsonObject json, String key) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, key));
            if (id == null) {
                throw new JsonSyntaxException("Invalid turbine fluid key " + json.get(key));
            }
            return id;
        }

        @Override
        public TurbineRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new TurbineRecipe(id, buffer.readResourceLocation(), buffer.readBoolean(), buffer.readVarInt(),
                    buffer.readFluidStack(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, TurbineRecipe recipe) {
            buffer.writeResourceLocation(recipe.input);
            buffer.writeBoolean(recipe.inputTag);
            buffer.writeVarInt(recipe.inputAmount);
            buffer.writeFluidStack(recipe.output);
            buffer.writeDouble(recipe.powerPerMb);
            buffer.writeDouble(recipe.expansion);
            buffer.writeDouble(recipe.spinUp);
        }
    }
}
